import Foundation
import CryptoKit
import Security


/// Manages the pairing process through a mutual exchange of identifiers.
///
/// This class handles the exchange of unique IDs between the client and server to recognize
/// previously established connections. If either the client or the server is unknown to the other,
/// it initiates a pairing-code-entry process to create a new trusted pairing.
///
/// ## Key Responsibilities
/// - **ID Exchange and Recognition:** Manages a two-way handshake. It checks the server's ID against a local list of known
///   servers (via ``ServerIDs``) and sends its own device ID for the server to perform a similar check.
/// - **Device ID Management:** Creates and persists a unique, encrypted device ID. The encryption key is stored in the Keychain,
///   and the ID is stored in `UserDefaults`.
/// - **Pairing Code Handling:** Triggers the UI for pairing code entry if a new device/server combination is detected. It sends
///   the entered code for validation to complete the pairing.
/// - **State Management:** Updates ``PairingStatus`` to control the pairing UI and ``NWStatus`` to reflect the final
///   connection status.
/// - **Process Flow:**
///   1. ``startPairing(connection:)`` is called with an active connection.
///   2. ``requestServerId()`` is called to get the server's ID. The client checks if this ID is locally known.
///   3. ``sendDeviceId()`` sends the client's unique ID to the server.
///   4. The server responds based on whether it recognizes the client's ID:
///      - If both client and server IDs are mutually recognized, mouse actions are started and pairing completes immediately.
///      - If either ID is unknown, the server requests a pairing code to proceed and the UI for code entry is shown.
///   5. The user enters the pairing code and ``sendPairingCode(_:)`` sends it to the server.
///   6. Upon successful validation:
///      - The new server's ID is stored locally.
///      - Code entry UI is closed, mouse actions are started and the process completes.
///     If validation fails, the UI is updated to allow for another attempt.
///
/// - Parameters:
///    - connection: An optional ``Connection`` object for network communication.
///    - mouseAction: An optional ``MouseActions`` object to be activated after successful pairing.
///    - pairingStatus: A ``PairingStatus`` object to manage the UI state of the pairing process.
///    - networkStatus: Status of network connectivity to be changed after successful pairing.
///
class Pairing {
    var connection: Connection?
    var mouseAction: MouseActions?
    var pairingStatus: PairingStatus
    var networkStatus: NWStatus
    let serverIDs = ServerIDs()
    var newServerId = Data()
    
    init(mouseAction: MouseActions?, pairingStatus: PairingStatus, networkStatus: NWStatus) {
        self.mouseAction = mouseAction
        self.pairingStatus = pairingStatus
        self.networkStatus = networkStatus
    }
    
    /// Retrieves or generates a symmetric encryption key used for server ID related operations.
    ///
    /// This function first attempts to load a previously stored `remoteKey` from `UserDefaults`.
    /// If a key is found, it is decrypted and returned. If decryption fails or no key is found,
    /// a new 256-bit symmetric key is generated, encrypted, and securely stored in `UserDefaults`
    /// for future use. This ensures that a consistent key is used for encrypting and decrypting
    /// server-related identifiers.
    ///
    /// - Returns: A `Data` representation of the symmetric encryption key.
    func getRemoteKey() -> Data {
        let userDefaultsKey = "remoteKey"
        let crypt = Crypto(keyChainService: "serverID", keyChainAccount: "serverID.remoteKey.key")
        if let sealedRemoteKey = UserDefaults.standard.data(forKey: userDefaultsKey) {
            if let remoteKey = crypt.decrypt(sealedRemoteKey) {
                return remoteKey
            } else {
                lgg.error("Failed to decrypt remote key, a new one will be created.")
            }
        }
        
        let newRemoteKey = SymmetricKey(size: .bits256).withUnsafeBytes { Data($0) }
        if let sealedNewRemoteKey = crypt.encrypt(newRemoteKey) {
            UserDefaults.standard.set(sealedNewRemoteKey, forKey: userDefaultsKey)
        } else {
            lgg.error("Encryption and storage of remote key failed.")
        }
        return newRemoteKey
    }
    
    /// Requests the server's unique identifier and checks if it is already known.
    ///
    /// Sends a key to the server to receive its ID. With the key the server decrypts its stored ID.
    /// If the server ID is recognized, it sends an acknowledgment and proceeds. If the server ID is new, 
    /// it stores it for later pairing. Then, it continues by sending the device ID to the server.
    func requestServerId() {
        let header = Data([0x40])
        let message = header + getRemoteKey()
        connection?.sendReceive(sendMessage: Data(message), receive: { rxdata in
            if let rxdata {
                // check header
                if rxdata[0] == 0x41 {
                    // get server ID
                    let servID = rxdata[1...]
                    if self.serverIDs.isKnownID(servID) {
                        self.connection?.send(Data([0x42]))
                        lgg.info("Server Id known.");
                    } else {
                        self.newServerId = servID
                        self.connection?.send(Data([0x43]))
                        lgg.info("Server Id is new.");
                    }
                    self.sendDeviceId()
                } else {
                    lgg.error("Error receiving server Id, invalid header: \(rxdata[0])")
                }
            } else {
               lgg.error("Error receiving server Id: nil")
            }
        })
    }

    /// Creates device Id from sha256-hashing of UUID and a random array
    /// - Returns: Device Id hash as `Data`
    func createDeviceId() -> Data {
        let ud = UUID().uuid
        let randomArray = (0...31).map {_ in UInt8.random(in: 0...255)}
        let uuidHash = SHA256.hash(data: [ud.0, ud.1, ud.2, ud.3, ud.4, ud.5, ud.6, ud.7, ud.8,
                                          ud.9, ud.10, ud.11, ud.12, ud.13, ud.14, ud.15] + randomArray)
        return Data(uuidHash)
    }
      
    /// Retrieves the device ID from `UserDefaults`and decrypts it.
    /// If device ID does not exist or decryption fails, a new one is generated, encrypted and stored in `UserDefaults`.
    /// Ensures the same device ID is returned for future calls.
    /// - Returns: Device ID hash as `Data`
    func getDeviceId() -> Data {
        let userDefaultsKey = "deviceId"
        let crypt = Crypto(keyChainService: "deviceId", keyChainAccount: "deviceId.key")
        if let sealedDeviceId = UserDefaults.standard.data(forKey: userDefaultsKey) {
            if let deviceId = crypt.decrypt(sealedDeviceId) {
                return deviceId
            } else {
                lgg.error("Failed to decrypt device ID, a new one will be created.")
            }
        }
        
        // Device ID could not be retrieved, create new one
        let newDeviceId = createDeviceId()
        if let sealedNewDeviceId = crypt.encrypt(newDeviceId) {
            UserDefaults.standard.set(sealedNewDeviceId, forKey: userDefaultsKey)
        } else {
            lgg.error("Encryption and storage of device ID failed.")
        }
        return newDeviceId
    }
            
    /// Sends the device ID to the server.
    ///
    /// First it sends the device ID, then it reads the server's response to determine, if the device is already paired with the server:
    /// If true, it exits the pairing process.
    /// If false, it sets `PairingStatus.showCodeEntry = true` to prompt for a pairing code in the UI.
    /// When the user has finished entering the pairing code, UI will call ``sendPairingCode(_:)``.
    func sendDeviceId() {
        let header = Data([0x50])
        let message = header + getDeviceId() // length:: 33 bytes
        lgg.info("Sending client ID")

        connection?.sendReceive(sendMessage: message, receive: { rxdata in
            if let rxdata {
                switch rxdata[0] {
                    case 0x51:
                    // Device ID is known by server
                    lgg.info("Server response: Server and client ID are known.")
                    self.exitPairing()
                    
                    case 0x52:
                    // New Device Id for server, pairing code required, show pairing code entry view
                    lgg.info("Server response: Unknown server ID or client ID.")
                    self.pairingStatus.showCodeEntry = true
                                      
                    default:
                    lgg.error("Unknown client ID response: \(rxdata[0], privacy: .public)")
                }
            } else {
                lgg.error("Error receiving client ID response: nil.")
            }
        })
    }
    
    /// Sends the user-entered pairing code to the server.
    ///
    /// It then reads the server's response to determine if the code was accepted or rejected.
    /// If accepted, it exits pairing.
    /// If rejected, it updates `PairingStatus` to indicate rejection in the code entry UI.
    /// - Parameter code: Array of strings, where each string is a digit of the pairing code in range 0..9
    func sendPairingCode(_ code: [String]) {
        assert(code.count == 5, "Error: number of code digits cannot be sent")

        // convert strings of 2 characters with leading 0 to integers
        let codi = code.map { $0.last?.wholeNumberValue ?? 0xF }
        lgg.info( "Sending pairing code: \(codi, privacy: .public)")
        
        let header = 0x60
        let message = Data([UInt8(header | (codi[4] & 0x0F)),
                            UInt8((codi[3] << 4) | codi[2]),
                            UInt8((codi[1] << 4) | codi[0])])
        connection?.sendReceive(sendMessage: message, receive: { rxdata in
            if let rxdata {
                switch rxdata[0] {
                    case 0x61:
                    // Pairing code was correct, save new server Id close code entry sheet
                    lgg.info("Pairing code accepted.")
                    self.serverIDs.storeID(self.newServerId)
                    self.pairingStatus.codeRejected = false
                    self.exitPairing()
                    
                    case 0x62:
                    // Pairing code was wrong
                    lgg.info("Pairing code rejected.")
                    self.pairingStatus.codeRejected = true
                    self.pairingStatus.showCodeEntry = true
                    
                    default:
                    lgg.error( "Unexpected pairing code response: \(rxdata, privacy: .public)")
                }
            } else {
                lgg.error("Error receiving pairing code response: nil.")
            }
        })
    }
    
    /// Starts the pairing process with the given connection.
    /// - Parameter connection: ``Connection`` instance which is used for communication with the server
    func startPairing(connection: Connection?) {
        self.connection = connection
        lgg.info("Starting pairing...")
        requestServerId()
    }
    
    /// Exits the pairing process.
    ///
    /// It starts transmission of mouse actions, sets the network status to connected and closes the code entry UI.
    func exitPairing() {
        self.mouseAction?.startMotionUpdate(connection: self.connection)
        self.networkStatus.connected = true  // inform UI about connection status
        self.connection = nil
        self.pairingStatus.showCodeEntry = false
        lgg.info("Exited pairing")
    }
    
    /// Resets all stored pairing information by removing relevant data from `UserDefaults`.
    ///
    /// This includes the device ID, known server IDs, and the remote key used for encryption.
    /// After calling this method, the application will behave as if no prior pairing has occurred,
    /// requiring a new pairing process to be initiated.
    func resetPairing() {
        UserDefaults.standard.removeObject(forKey: "deviceId")
        UserDefaults.standard.removeObject(forKey: "serverIDs")
        UserDefaults.standard.removeObject(forKey: "remoteKey")
        lgg.info("Reseted pairing")
    }
    
}


/// Manages a list of known server IDs for pairing.
///
/// This class handles secure storage, encryption, and retrieval of server IDs.
/// Server IDs are hashed and encrypted. The encrypted IDs are persisted in `UserDefaults`.
/// On first run, it initializes the storage with dummy IDs and a new encryption key.
///
class ServerIDs {
    let maxIDs = 5
    let userDefaultsKey = "serverIDs"
    let crypt = Crypto(keyChainService: "serverId", keyChainAccount: "serverId.key")
    
    /// Initializes the ServerIDs manager.
    ///
    /// On the very first run, this method creates a list of dummy server IDs, encrypts them, and saves them in `UserDefaults`.
    /// This ensures the app starts with a secure storage environment for server IDs.
    init() {
        // fill UserDefaults with dummy server IDs on very first run of the app
        if UserDefaults.standard.array(forKey: userDefaultsKey) == nil {
            lgg.info("Initializing UserDefaults with dummy server IDs.")
            let dummyIDs = (0..<maxIDs).map { _ in
                let dummyId = (0..<32).map { _ in UInt8.random(in: 0..<255) }
                return crypt.encrypt(hash(Data(dummyId)))
            }
            UserDefaults.standard.set(dummyIDs, forKey: userDefaultsKey)
        }
        // log all server id hashes
        //getIDHashes().forEach { lgg.info("\($0.map { String(format: "%02hhx", $0) }.joined(separator: ":"))") }
    }
    
    /// Retrieves the list of encrypted server IDs from `UserDefaults`.
    ///
    /// - Returns: An array of `Data` objects, each representing an encrypted server ID.
    ///            Returns an empty array if no IDs are stored.
    func getIDs() -> [Data] {
        return UserDefaults.standard.array(forKey: userDefaultsKey) as? [Data] ?? []
    }
    
    /// Checks if the given server ID is already known.
    ///
    /// Compares the SHA256 hash of the provided server ID with the decrypted hashes of stored IDs.
    /// - Parameter serverID: The server ID to check.
    /// - Returns: `true` if the server ID is recognized, `false` otherwise.
    func isKnownID(_ serverID: Data) -> Bool {
        for id in getIDs() {
            if hash(serverID) == crypt.decrypt(id) {
                return true
            }
        }
        return false
    }
    
    /// Stores a new server ID securely.
    ///
    /// Hashes and encrypts the server ID, then adds it to the list in `UserDefaults`.
    /// If the maximum number of IDs is reached, the oldest is removed.
    /// - Parameter serverID: The server ID to store.
    func storeID(_ serverID: Data) {
        guard !serverID.isEmpty else { return }
        
        var ids = getIDs()
        while ids.count >= maxIDs {
            ids.removeLast()
        }
        if let encryptedID = crypt.encrypt(hash(serverID)) {
            ids.insert(encryptedID, at: 0)
            UserDefaults.standard.set(ids, forKey: userDefaultsKey)
        } else {
            lgg.error("Could not encrypt and store server ID.")
        }
    }
    
    /// Hashes the given ID using SHA256.
    ///
    /// - Parameter id: The ID to hash.
    /// - Returns: The SHA256 hash as `Data`.
    func hash(_ id: Data) -> Data {
        let digest = SHA256.hash(data: id)
        return Data(digest)
    }
}


/// A utility class for handling cryptographic operations, including secure storage
/// of symmetric keys in the Keychain and encryption/decryption of data.
///
/// This class abstracts the complexities of `Keychain` services and `ChaChaPoly` encryption,
/// providing a simplified interface for managing sensitive data. It ensures that encryption keys
/// are securely stored and retrieved, and that data can be encrypted and decrypted reliably.
///
/// - Parameters:
///   - keyChainService: A string identifier for the Keychain service.
///   - keyChainAccount: A string identifier for the Keychain account.
class Crypto {
    var keyChainService: String
    var keyChainAccount: String
    
    init(keyChainService: String, keyChainAccount: String) {
        self.keyChainService = keyChainService
        self.keyChainAccount = keyChainAccount
    }
    
    /// Saves data securely to the Keychain.
    ///
    /// If an item with the same service and account exists, it updates the data.
    /// Otherwise, it adds a new item to the Keychain.
    /// - Parameters:
    ///   - data: The data to store.
    ///   - service: The Keychain service identifier.
    ///   - account: The Keychain account identifier.
    func saveToKeychain(data: Data, service: String, account: String) {
        var query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service,
                                    kSecAttrAccount as String: account]
        let attributes: [String: Any] = [kSecValueData as String: data]
        
        // There might be the same item from a previous installation of the app
        // that must be updated otherwise the item can be added.
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            query[kSecValueData as String] = data
            let addStatus = SecItemAdd(query as CFDictionary, nil)
            if addStatus != errSecSuccess {
                lgg.error("Failed to save data to keychain: \(status)")
            }
        } else if status != errSecSuccess {
            lgg.error("Failed to update data of keychain: \(status)")
        }
    }
    
    /// Loads data securely from the Keychain.
    ///
    /// Searches for an item matching the given service and account identifiers.
    /// Returns the stored data if found, otherwise returns nil.
    /// - Parameters:
    ///   - service: The Keychain service identifier.
    ///   - account: The Keychain account identifier.
    /// - Returns: The data from the Keychain, or nil if not found.
    func loadFromKeychain(service: String, account: String) -> Data? {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service,
                                    kSecAttrAccount as String: account,
                                    kSecMatchLimit as String: kSecMatchLimitOne,
                                    kSecReturnData as String: true]
        
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecSuccess {
            return item as? Data
        }
        return nil
    }
    
    /// Retrieves a symmetric encryption key from the Keychain, or generates and stores a new one if it doesn't exist.
    ///
    /// This method ensures that a persistent symmetric key is available for cryptographic operations.
    /// If a key associated with the `keyChainService` and `keyChainAccount` is found in the Keychain,
    /// it is returned. Otherwise, a new 256-bit `SymmetricKey` is generated, securely stored in the Keychain,
    /// and then returned.
    ///
    /// - Returns: A `SymmetricKey` instance for encryption and decryption.
    func getKey() -> SymmetricKey {
        if let keyData = loadFromKeychain(service: keyChainService, account: keyChainAccount) {
            return SymmetricKey(data: keyData)
        } else {
            let key = SymmetricKey(size: .bits256)
            let keyData = key.withUnsafeBytes { Data($0) }
            saveToKeychain(data: keyData, service: keyChainService, account: keyChainAccount)
            return key
        }
    }
    
    /// Encrypts the given data using `ChaChaPoly` symmetric encryption.
    ///
    /// This method uses the symmetric key retrieved by ``getKey()`` to encrypt the provided `Data`.
    /// The encryption process includes generating a nonce and authentication tag, which are combined
    /// with the ciphertext into a single `Data` object.
    ///
    /// - Parameter data: The plaintext `Data` to be encrypted.
    /// - Returns: An optional `Data` object containing the combined sealed box (ciphertext, nonce, and authentication tag),
    ///            or `nil` if the encryption fails.
    func encrypt(_ data: Data) -> Data? {
        do {
            let sealedData = try ChaChaPoly.seal(data, using: getKey())
            return sealedData.combined
        } catch {
            lgg.error("Encryption of data failed: \(error.localizedDescription)")
            return nil
        }
    }
    
    /// Decrypts the given sealed data using `ChaChaPoly` symmetric decryption.
    ///
    /// This method uses the symmetric key retrieved by ``getKey()`` to decrypt a `ChaChaPoly.SealedBox`.
    /// The input `sealedData` is expected to be a combined representation of ciphertext, nonce, and authentication tag
    /// as produced by the ``encrypt(_:)`` method.
    ///
    /// - Parameter sealedData: The `Data` object containing the combined sealed box to be decrypted.
    /// - Returns: An optional `Data` object containing the decrypted plaintext, or `nil` if the decryption fails
    ///            (e.g., due to corrupted data or an incorrect key).
    func decrypt(_ sealedData: Data) -> Data? {
        do {
            let sealedBox = try ChaChaPoly.SealedBox(combined: sealedData)
            let data = try ChaChaPoly.open(sealedBox, using: getKey())
            return data
        } catch {
            lgg.error("Decryption of data failed: \(error.localizedDescription)")
            return nil
        }
    }
}

