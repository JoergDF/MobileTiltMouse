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
        let crypt = Crypto(keyTag: "serverID.remoteKey")

        if let encryptedRemoteKey = UserDefaults.standard.data(forKey: userDefaultsKey) {
            if let remoteKey = crypt?.decrypt(encryptedRemoteKey) {
                return remoteKey
            } else {
                lgg.error("Failed to decrypt remote key, a new one will be created.")
            }
        }
        
        let newRemoteKey = SymmetricKey(size: .bits256).withUnsafeBytes { Data($0) }
        if let encryptedNewRemoteKey = crypt?.encrypt(newRemoteKey) {
            UserDefaults.standard.set(encryptedNewRemoteKey, forKey: userDefaultsKey)
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
        let crypt = Crypto(keyTag: "deviceId.key")
        if let encryptedDeviceId = UserDefaults.standard.data(forKey: userDefaultsKey) {
            if let deviceId = crypt?.decrypt(encryptedDeviceId) {
                return deviceId
            } else {
                lgg.error("Failed to decrypt device ID, a new one will be created.")
            }
        }
        
        // Device ID could not be retrieved, create new one
        let newDeviceId = createDeviceId()
        if let encryptedNewDeviceId = crypt?.encrypt(newDeviceId) {
            UserDefaults.standard.set(encryptedNewDeviceId, forKey: userDefaultsKey)
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
    let crypt = Crypto(keyTag: "serverId.key")
    
    /// Initializes the ServerIDs manager.
    ///
    /// On the very first run, this method creates a list of dummy server IDs, encrypts them, and saves them in `UserDefaults`.
    /// This ensures the app starts with a secure storage environment for server IDs.
    init() {
        // fill UserDefaults with dummy server IDs on very first run of the app
        if UserDefaults.standard.array(forKey: userDefaultsKey) == nil {
            lgg.info("Initializing UserDefaults with dummy server IDs.")
            assert(crypt != nil)
            let dummyIDs = (0..<maxIDs).map { _ in
                let dummyId = (0..<32).map { _ in UInt8.random(in: 0..<255) }
                return crypt?.encrypt(hash(Data(dummyId)))
            }
            UserDefaults.standard.set(dummyIDs, forKey: userDefaultsKey)
        }
        // log all server ids
        //getIDs().forEach { lgg.info("\($0.map { String(format: "%02hhx", $0) }.joined(separator: ":"))") }
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
            if hash(serverID) == crypt?.decrypt(id) {
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
        if let encryptedID = crypt?.encrypt(hash(serverID)) {
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


/// Cryptographic helper that performs asymmetric encryption/decryption using a Secure Enclave-backed key.
///
/// This class manages a persistent EC key pair stored in the Secure Enclave and uses the public key
/// to encrypt small secrets and the enclave-backed private key to decrypt them. The private key
/// is non-exportable and remains protected by the Secure Enclave; callers receive only encrypted
/// blobs suitable for storage. On unsuccessful crypto operations `nil` is returned and failures logged.
///
/// - Parameters: keyTag: A short identifier that is combined with the app bundle identifier to form the
///   application tag used to locate or create the Secure Enclave key pair in the Keychain.
class Crypto {
    let keyAlgorithm = SecKeyAlgorithm.eciesEncryptionCofactorVariableIVX963SHA256AESGCM
    var keyTag: Data
    
    init?(keyTag: String) {
        guard !keyTag.isEmpty else {
            lgg.error("Error: empty key tag in pairing/crypto")
            return nil
        }
        guard let bundleId = Bundle.main.bundleIdentifier else {
            lgg.error("Error getting bundle identifier in pairing/crypto")
            return nil
        }
        guard let tag = (bundleId + "." + keyTag).data(using: .utf8) else {
            lgg.error("Error creating key tag in pairing/crypto")
            return nil
        }
        self.keyTag = tag
    }
    
    /// Creates a new persistent private key inside the Secure Enclave.
    ///
    /// The created key is stored permanently in the device keychain and tagged using `keyTag`.
    ///
    /// - Returns: The newly created `SecKey` private key reference on success, or `nil` on failure
    ///   (an error is logged).
    func createPrivateKey() -> SecKey? {
        let accessControl = SecAccessControlCreateWithFlags(
            nil,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            [],
            nil)
        let attributes: NSDictionary = [
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeySizeInBits as String: 256,
            kSecAttrTokenID as String: kSecAttrTokenIDSecureEnclave,
            kSecPrivateKeyAttrs as String: [
                kSecAttrIsPermanent as String: true,
                kSecAttrApplicationTag as String: keyTag,
                kSecAttrAccessControl as String: (accessControl as Any)
            ]
        ]

        var error: Unmanaged<CFError>?
        if let privateKey = SecKeyCreateRandomKey(attributes, &error) {
            return privateKey
        } else {
            lgg.error("Error creating key: \(error!.takeRetainedValue())")
            return nil
        }
    }
    
    /// Loads an existing private key from the Keychain using the configured application tag.
    ///
    /// - Returns: A `SecKey` reference to the existing private key if found, otherwise `nil`.
    func loadPrivateKey() -> SecKey? {
        let query: NSDictionary = [
            kSecClass as String: kSecClassKey,
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrApplicationTag as String: keyTag,
            kSecReturnRef as String: true
        ]
        
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecSuccess {
            return (item as! SecKey)
        }
        return nil
    }

    /// Obtains a usable private key, preferring an existing key and creating one if necessary.
    ///
    /// This helper first attempts to load the key via `loadPrivateKey()`. If no key is present,
    /// it will call `createPrivateKey()` to generate and persist a new enclave-backed key pair.
    ///
    /// - Returns: A `SecKey` reference to the private key or `nil` if none could be obtained.
    func getPrivateKey() -> SecKey? {
        if let key = loadPrivateKey() {
            return key
        } else if let newKey = createPrivateKey() {
            return newKey
        }
        return nil
    }

    /// Encrypts the provided `Data` using the Secure Enclave public key.
    ///
    /// The method obtains the public key corresponding to the enclave private key and uses the
    /// configured `keyAlgorithm` to encrypt the input bytes. The encrypted result is returned
    /// as a `Data` blob suitable for storage.
    ///
    /// - Parameter data: Plaintext `Data` to encrypt.
    /// - Returns: Encrypted `Data` on success, or `nil` on failure (an error is logged).
    func encrypt(_ data: Data) -> Data? {
        if let privateKey = getPrivateKey() {
            if let publicKey = SecKeyCopyPublicKey(privateKey) {
                if SecKeyIsAlgorithmSupported(publicKey, .encrypt, keyAlgorithm) {
                    var error: Unmanaged<CFError>?
                    if let encryptedData = SecKeyCreateEncryptedData(
                        publicKey,
                        keyAlgorithm,
                        data as CFData,
                        &error
                    ) {
                        return (encryptedData as Data)
                    } else {
                        lgg.error("Encryption in pairing failed: \(error!.takeRetainedValue())")
                    }
                }
            }
        }
        return nil
    }
    
    /// Decrypts the provided data using the Secure Enclave private key.
    ///
    /// The method uses the enclave-backed private key and the configured `keyAlgorithm` to decrypt
    /// a previously produced encrypted blob. Because the private key is protected by the
    /// Secure Enclave, decryption succeeds only if the key exists and the algorithm is supported.
    ///
    /// - Parameter encryptedData: The encrypted `Data` blob to decrypt.
    /// - Returns: The decrypted plaintext `Data` on success, or `nil` on failure (an error is logged).
    func decrypt(_ encryptedData: Data) -> Data? {
        if let privateKey = getPrivateKey() {
            if SecKeyIsAlgorithmSupported(privateKey, .decrypt, keyAlgorithm) {
                var error: Unmanaged<CFError>?
                if let decrpytedData = SecKeyCreateDecryptedData(
                    privateKey,
                    keyAlgorithm,
                    encryptedData as CFData,
                    &error
                ) {
                    return (decrpytedData as Data)
                } else {
                    lgg.error("Decryption in pairing failed: \(error!.takeRetainedValue())")
                }
            }
        }
        return nil
    }
}
