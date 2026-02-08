import Foundation
import Network
import SwiftUI
import CryptoKit



private let alpnName = "mobiletiltmouseproto"
private let connectionTimeout = 30  // seconds


/// Manages QUIC network connections with the remote server that controls mouse pointer.
///
/// It handles establishing, maintaining and closing secure QUIC connections.
/// It provides:
/// - Secure connection setup with certificate verification 
/// - Client authentication using a PKCS#12 client certificate
/// - Connection state monitoring and UI updates
/// - Data transmission and reception
/// - Starts pairing process
///
/// The class uses the Network framework for QUIC protocol support and CryptoKit
/// for cryptographic operations. It updates the global `networkStatus` for UI state changes.
///
/// Only one active connection is allowed at a time. Connection attempts while a connection exists will be ignored.
///
///  - Parameters:
///     - errAlert: For showing error alert
///     - networkStatus: Status of network connectivity
///     - pairing: Optional ``Pairing`` object
///
class Connection {
    var nwConnection: NWConnection?
    var endpoint: NWEndpoint?
    var errAlert: ErrorAlert
    var networkStatus: NWStatus
    weak var pairing: Pairing?
    
    init(errAlert: ErrorAlert, networkStatus: NWStatus, pairing: Pairing?) {
        self.errAlert = errAlert
        self.networkStatus = networkStatus
        self.pairing = pairing
    }
    
    /// Shows an alert to the user when server certificate verification fails.
    private func serverCertificateAlert() {
        errAlert.error = true
        errAlert.message = "Check of server certificate failed."
    }
    
    /// Shows an alert to the user when client certificate loading fails.
    private func clientCertificateAlert() {
        errAlert.error = true
        errAlert.message = "Loading of client certificate failed."
    }
    
    /// Loads the hash of the server certificate.
    ///
    /// The file with the hash string is loaded from the app's asset catalog.
    /// The hex string of the file is converted to UInt8 values.
    ///
    /// - Returns: `UInt8` array of the loaded hash, `nil` on error
    func loadServerCertHash() -> [UInt8]? {
        // load server certificate hash from file
        guard let serverCertHashData = NSDataAsset(name: "ServerCertHash")?.data else {
            lgg.error("Error: Could not load server certificate hash")
            return nil
        }
        
        // convert hash data to string
        guard let serverCertHashString = String(data: serverCertHashData, encoding: .utf8) else {
            lgg.error("Error: Could not convert server certificate hash data to string")
            return nil
        }
        
        // check length
        guard serverCertHashString.count == 64 else {
            lgg.error("Error: Wrong length of server certificate hash")
            return nil
        }
        
        // convert hash hex-string to UInt8-array
        let serverCertHashChars = Array(serverCertHashString)
        let serverCertHash = stride(from: 0, to: serverCertHashChars.count, by: 2)
            .compactMap { UInt8("\(serverCertHashChars[$0])\(serverCertHashChars[$0 + 1])", radix: 16) }
        
        return serverCertHash
    }
     
    
    /// Loads the self-signed client certificate.
    ///
    /// This method loads a PKCS#12 (.p12) client certificate from the app's asset catalog.
    /// It imports the certificate and extract the identity required for client authentication in QUIC connections.
    ///
    /// Steps performed:
    /// 1. Loads the PKCS#12 data from the asset catalog.
    /// 2. Imports and decrypts the certificate.
    /// 3. Extracts the first identity from the imported items.
    /// 4. Returns the SecIdentity for use in network security configuration.
    ///
    /// - Returns: The extracted `SecIdentity` if successful, or `nil` if loading/importing fails.
    func loadClientCertificate() -> SecIdentity? {
        // load client certificate from file
        guard let p12Data = NSDataAsset(name: "ClientCertificate")?.data else {
            lgg.error("Error: Could not load client certificate")
            return nil
        }
        
        guard let serverCertHash = loadServerCertHash() else {
            lgg.error("Error loading client certificate: Could not get server certificate hash")
            return nil
        }
        
        let randomData = (0..<32).map { i in
            var hasher = SHA256()
            hasher.update(data: serverCertHash)
            hasher.update(data: [UInt8(i)])
            let dat = hasher.finalize()
            return Data(dat)[0]
        }

        var hasher = SHA512()
        hasher.update(data: serverCertHash)
        hasher.update(data: randomData)
        let dat = hasher.finalize()
        let datString = dat.map { String(format: "%02hhx", $0) }.joined()

        // import p12 data
        let p12Options = [kSecImportExportPassphrase as String: datString]
        var rawItems: CFArray?
        let status = SecPKCS12Import(p12Data as CFData, p12Options as CFDictionary, &rawItems)
        guard status == errSecSuccess else {
            lgg.error("Error: Could not import client certificate")
            return nil
        }
        let items = rawItems! as! Array<Dictionary<String, Any>>
        let firstItem = items[0]
        guard let identity = firstItem[kSecImportItemIdentity as String] as! SecIdentity? else {
            lgg.error("Error: Could not get identity from client certificate")
            return nil
        }
        return identity
    }
    
    
    /// Returns a closure verifying self-signed server certificate
    ///
    /// 1. Extracts the received certificate in DER format
    /// 2. Get reference hash
    /// 3. Computes SHA-256 hash of the DER data
    /// 4. Compares against reference hash
    ///
    /// - Returns: closure with check
    func verifyServerCertificate() -> sec_protocol_verify_t {
        return { [weak self] (sec_protocol_metadata, sec_trust, completionHandler) in
            
            // get received certificate for comparison with reference
            let secTrustRef = sec_trust_copy_ref(sec_trust).takeRetainedValue() as SecTrust
            
            guard let certArray = SecTrustCopyCertificateChain(secTrustRef) as? [SecCertificate] else {
                lgg.error("Error: No certificate received")
                self?.serverCertificateAlert()
                completionHandler(false)
                return
            }
            
            // there should be only one certificate received
            let certCount = SecTrustGetCertificateCount(secTrustRef)
            guard ((certCount == 1) && (certArray.count == 1)) else {
                lgg.error("Error: Invalid number of certificates: \(certCount) \(certArray.count)")
                self?.serverCertificateAlert()
                completionHandler(false)
                return
            }

            // get reference hash
            guard let referenceCertHash = self?.loadServerCertHash() else {
                lgg.error("Error verifying server certificate: Could not get server certificate hash")
                self?.serverCertificateAlert()
                completionHandler(false)
                return
            }
            
            // get DER format of received certificate
            // get its hash value and
            // compare hash against reference hash
            let dataDerCF = SecCertificateCopyData(certArray[0])
            
            let digest = SHA256.hash(data: dataDerCF as Data)
            
            if !digest.elementsEqual(referenceCertHash) {
                lgg.error("Error: Server certificate hash is wrong")
                self?.serverCertificateAlert()
                completionHandler(false)
                return
            }
            
            lgg.info("Certificate ok")
            completionHandler(true)
        }
    }
    

    /// Configures QUIC protocol parameters for the network connection.
    ///
    /// Configures a QUIC connection with:
    /// - Sets application protocol name for ALPN negotiation
    /// - Sets up unidirectional communication
    /// - Configures connection timeout
    /// - Loading and attaching a self-signed client certificate for mutual authentication
    /// - Verification of self-signed server certificate
    ///
    /// - Returns: Configured NWParameters for QUIC connection
    /// - Important: The connection will fail if certificate checks do not pass.
    func quicNWParameters() -> NWParameters {
        let options = NWProtocolQUIC.Options(alpn: [alpnName])
        options.direction = .bidirectional
        options.idleTimeout = connectionTimeout * 1000
        
        let securityProtocolOptions: sec_protocol_options_t = options.securityProtocolOptions
        
        // set client certificate
        if let identity = loadClientCertificate() {
            sec_protocol_options_set_local_identity(securityProtocolOptions, sec_identity_create(identity)!)
        } else {
            clientCertificateAlert()
        }
        
        // verify server certificate
        sec_protocol_options_set_verify_block(
            securityProtocolOptions,
            verifyServerCertificate(),
            .global()
        )
        
        return NWParameters(quic: options)
    }
    
    
    /// Establishes a QUIC network connection to the server endpoint.
    ///
    /// Creates and starts a new connection if none exists. The connection setup includes:
    /// - Configuration of QUIC protocol parameters
    /// - Setup of state monitoring handlers
    /// - Connection viability monitoring
    /// - Path quality monitoring
    /// Only one connection can be active at a time. Subsequent calls while a connection
    /// exists will be ignored.
    ///
    /// When connection is viable, pairing process is started.
    ///
    /// State changes are monitored and trigger UI updates:
    /// - `.ready` → Sets connected status to true
    /// - `.cancelled` → Sets connected status to false
    /// - `.failed` → Connection stopped, sets connected status to false
    ///
    /// - Parameter endpoint: Optional NWEndpoint to connect to. If nil, uses previously stored endpoint.
    func startConnection(_ endpoint: NWEndpoint? = nil) {
        guard nwConnection == nil else {
            lgg.info("Connection already started")
            return
        }

        lgg.info("Starting connection with endpoint: \(endpoint.debugDescription, privacy: .public)")
        
        // store passed endpoint or (if nil) use previously stored one
        if let endpoint {
            self.endpoint = endpoint
        }
        
        if let nwEndpoint = self.endpoint {
            let params = quicNWParameters()
            nwConnection = NWConnection(to: nwEndpoint, using: params)
            lgg.info("Connecting to endpoint: \(nwEndpoint.debugDescription, privacy: .public)");
        }
        
        nwConnection?.stateUpdateHandler = { [weak self] newState in
            switch (newState) {
            case .setup:
                lgg.info("Connection state: setup")
            case .preparing:
                lgg.info("Connection state: preparing")
            case .ready:
                lgg.info("Connection state: ready")
                if let remoteEndpoint = self?.nwConnection?.currentPath?.remoteEndpoint {
                    lgg.info("Connected to \(remoteEndpoint.debugDescription, privacy: .public)")
                    if let interface = remoteEndpoint.interface {
                        lgg.info("Connected over interface type \(String(describing: interface.type), privacy: .public) with name \(interface.name, privacy: .public)")
                    }
                }
                self?.pairing?.startPairing(connection: self)
            case .cancelled:
                self?.networkStatus.connected = false // inform UI about connection status
                lgg.info("Connection state: cancelled")
            case .waiting(let err):
                lgg.info("Connection state: waiting, error: \(err, privacy: .public)")
            case .failed(let err):
                lgg.info("Connection state: failed, error: \(err, privacy: .public)")
                self?.networkStatus.connected = false // inform UI about connection status
                self?.stopConnection()
            default:
                lgg.info("Connection state: unknown: \(String(describing: newState), privacy: .public)")
            }
        }
        
        nwConnection?.viabilityUpdateHandler = { isViable in
            if (isViable) {
                lgg.info("Connection is viable")
            } else {
                lgg.info("Connection is not viable")
            }
        }
        
        nwConnection?.betterPathUpdateHandler = { betterPathAvailable in
            if (betterPathAvailable) {
                lgg.info("Connection: A better path is availble")
            } else {
                lgg.info("Connection: No better path is available")
            }
        }
        
        nwConnection?.start(queue: .global())
    }
    
    /// Sends message data over the QUIC connection.
    ///
    /// The method silently returns if no connection exists.
    ///
    /// - Parameter message: The data to send over the connection
    func send(_ message: Data) {
        nwConnection?.send(content: message, completion: .contentProcessed({ sendError in
            if let error = sendError {
                lgg.error("Error sending data: \(error, privacy: .public)")
            }
        })
        )
    }
    
    /// Send message data and receives response
    ///
    /// The method silently returns if no connection exists.
    ///
    /// - Parameters:
    ///     - sendMessage: The data to send over the connection
    ///     - receive: closure with parameter of type Data, which contains the received bytes
    func sendReceive(sendMessage: Data, receive: @escaping (Data?) -> Void) {
        nwConnection?.send(content: sendMessage, completion: .contentProcessed({ sendError in
            if let error = sendError {
                lgg.error("Error sending data in sendReceive: \(error, privacy: .public)")
            } else {
                self.nwConnection?.receive(minimumIncompleteLength: 1, maximumLength: 33) {
                    (content: Data?, contentContext: NWConnection.ContentContext?, isComplete: Bool, receiveError: NWError?) in
                    if let error = receiveError {
                        lgg.error("Error receiving data in sendReceive: \(error, privacy: .public)")
                    } else if let data = content {
                        //lgg.info("Received data: \(data, privacy: .public)")
                        receive(data)
                    } else {
                        lgg.info("Received content is nil")
                    }
                }
            }
        })
        )
    }

    
    /// Stops and cleans up the QUIC network connection.
    ///
    /// Performs an orderly shutdown of the connection:
    /// 1. Cancels the active connection
    /// 2. Waits up to 1 second for connection state to become `.cancelled`
    /// 3. Removes the connection reference
    func stopConnection() {
        lgg.info("Stopping connection")
        nwConnection?.cancel()
        // wait maximum 1 second for connection to become cancelled
        for _ in 1...100 {
            Thread.sleep(forTimeInterval: 0.01)
            if nwConnection?.state == .cancelled {
                break
            }
        }
        nwConnection = nil
    }
}
