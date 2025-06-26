import Foundation
import Network
import SwiftUI
import CryptoKit



let alpnName = "mobiletiltmouseproto"
let connectionTimeout = 30  // seconds

/// A class that manages QUIC network connections with the remote server that controls mouse pointer.
///
/// The Connection class handles establishing, maintaining and closing secure QUIC connections. 
/// It provides:
/// - Secure connection setup with certificate verification 
/// - Client authentication using HMAC
/// - Connection state monitoring
/// - Data transmission
///
/// Example usage:
/// ```swift
/// // Create connection instance
/// let connection = Connection(remoteAccess)
///
/// // Start connection to endpoint
/// connection.startConnection(endpoint)
///
/// // Send data
/// connection.send(payload)
///
/// // Stop connection
/// connection.stopConnection()
/// ```
///
/// The class uses the Network framework for QUIC protocol support and CryptoKit
/// for cryptographic operations. It integrates with [`RemoteAccess`](RemoteAccess.swift)
/// for network management and updates the global [`networkStatus`](MobileMouseApp.swift)
/// for UI state changes.
///
/// Only one active connection is allowed at a time. Connection attempts while a connection exists will be ignored.
/// 
class Connection {
    var connection: NWConnection?
    var endpoint: NWEndpoint?
    weak var remoteAccess: RemoteAccess?
    
    init(_ remoteAccess: RemoteAccess?) {
        self.remoteAccess = remoteAccess
    }
    
    /// Shows an alert to the user when certificate verification fails.
    ///
    /// Sets the global error alert state to display a message indicating that the security
    /// certificate check failed during connection establishment.
    ///
    /// The alert uses the global `errAlert` observable object to trigger the alert display
    /// in the UI layer.
    private func certificateAlert() {
        errAlert.error = true
        errAlert.message = "Check of security certificate of connection failed."
    }
        
    /// Authenticates the client to the server using a shared key and HMAC verification.
    /// 
    /// The authentication runs over an already encrypted QUIC connection and follows these steps:
    /// 1. Uses a predefined 32-byte shared key
    /// 2. Generates a SHA-512 hash of the key
    /// 3. Creates a random 32-byte message
    /// 4. Computes HMAC-SHA256 of the message using the key hash
    /// 5. Sends both message and its HMAC to server for verification
    /// 
    /// The protocol sends two types of packets:
    /// - Type 0xA: Contains the random message
    /// - Type 0xB: Contains the HMAC
    /// 
    /// Authentication packet format:
    /// ```
    /// +--------------+-------------+----------------+
    /// | 4-bit header | 4-bit zeros | 2 bytes data   |
    /// +--------------+-------------+----------------+
    /// ```
    func authenticateClientToServer() {
        func sendAuthData(header: Int, data: [UInt8]) {
            assert(data.count % 2 == 0, "Send authentication: Data size must be even")
            
            let headDataByte = UInt8(header << 4)
            
            for i in stride(from: 0, to: data.count, by: 2) {
                send(Data([headDataByte, data[i], data[i+1]]))
            }
        }
        
        let key: [UInt8] = [
            0x24, 0xe3, 0x82, 0x41, 0x55, 0xc6, 0x1d, 0xdc,
            0x12, 0xe1, 0x8a, 0xc2, 0x02, 0x9f, 0x66, 0x5f,
            0x24, 0x19, 0xe8, 0x9d, 0x5e, 0x17, 0x6d, 0x55,
            0x07, 0x74, 0x37, 0x0d, 0x0e, 0x5f, 0xb6, 0x58
        ]

        let keyHash = SHA512.hash(data: key)
        let message = (0..<32).map{_ in UInt8.random(in: 0x00...0xFF)}
        let hmac = HMAC<SHA256>.authenticationCode(for: message, using: SymmetricKey(data: keyHash))

        sendAuthData(header: 0xA, data: message)
        sendAuthData(header: 0xB, data: Array<UInt8>(hmac))
    }
    

    /// Configures QUIC protocol parameters for the network connection.
    ///
    /// Configures a QUIC connection with:
    /// - Sets application protocol name for ALPN negotiation
    /// - Sets up unidirectional communication
    /// - Configures connection timeout
    /// - Verification of self-signed server certificate
    ///
    /// The certificate verification:
    /// 1. Requires exactly one certificate (no CA chain)
    /// 2. Extracts the certificate in DER format
    /// 3. Computes SHA-256 hash of the DER data
    /// 4. Compares against hardcoded reference hash
    ///
    /// To generate reference hash for a new certificate:
    /// ```bash
    /// # Convert certificate from PEM to DER format
    /// openssl x509 -in cert.pem -outform DER -out cert.der
    /// # Calculate SHA-256 hash
    /// shasum -a 256 cert.der
    /// ```
    ///
    /// - Returns: Configured NWParameters for QUIC connection
    /// - Important: The connection will fail if the server's certificate hash doesn't match 
    ///   the expected value.
    func quicNWParameters() -> NWParameters {
        let options = NWProtocolQUIC.Options(alpn: [alpnName])
        options.direction = .unidirectional
        options.idleTimeout = connectionTimeout * 1000
        
        let securityProtocolOptions: sec_protocol_options_t = options.securityProtocolOptions
        
        sec_protocol_options_set_verify_block(
            securityProtocolOptions,
            { (sec_protocol_metadata,
               sec_trust,
               completionHandler
            ) in
                
                // get received certificate for comparison with reference
                
                let secTrustRef = sec_trust_copy_ref(sec_trust).takeRetainedValue() as SecTrust
                
                guard let certArray = SecTrustCopyCertificateChain(secTrustRef) as? [SecCertificate] else {
                    lgg.error("Error: No certificate received")
                    self.certificateAlert()
                    completionHandler(false)
                    return
                }
                
                // there should be only one certificate received
                let certCount = SecTrustGetCertificateCount(secTrustRef)
                guard ((certCount == 1) && (certArray.count == 1)) else {
                    lgg.error("Error: Invalid number of certificates: \(certCount) \(certArray.count)")
                    self.certificateAlert()
                    completionHandler(false)
                    return
                }
                
                // get DER format of received certificate
                // get its hash value and
                // compare hash against reference hash
                let dataDerCF = SecCertificateCopyData(certArray[0])
                
                let digest = SHA256.hash(data: dataDerCF as Data)
                
                // reference hash of server certificate (DER format, in file cert.der)
                // is output of command line tool: shasum -a 256 cert.der
                let referenceCertHash: [UInt8] = [
                    0x4f, 0x4b, 0xf5, 0xb8, 0x6e, 0xa4, 0x3d, 0xbc,
                    0xf9, 0xae, 0x83, 0xd2, 0xcb, 0x6c, 0xfc, 0x0e,
                    0xd8, 0xc9, 0xda, 0x9e, 0xf0, 0x27, 0xb8, 0x02,
                    0x6e, 0xe1, 0x84, 0x81, 0x84, 0x52, 0xf2, 0x14
                    ]
                if !digest.elementsEqual(referenceCertHash) {
                    lgg.error("Error: Certificate hash is wrong")
                    self.certificateAlert()
                    completionHandler(false)
                    return
                }
                                
                lgg.info("Certificate ok")
                completionHandler(true)

            },
            .global())
        return NWParameters(quic: options)
    }
    
    
    /// Establishes a QUIC network connection to the server endpoint.
    ///
    /// Creates and starts a new connection if none exists. The connection setup includes:
    /// - Configuration of QUIC protocol parameters
    /// - Setup of state monitoring handlers
    /// - Connection viability monitoring
    /// - Path quality monitoring
    /// - Automatic client authentication when connection becomes viable
    /// Only one connection can be active at a time. Subsequent calls while a connection
    /// exists will be ignored.
    ///
    /// State changes are monitored and trigger UI updates:
    /// - `.ready` → Sets connected status to true
    /// - `.cancelled` → Sets connected status to false
    /// - `.failed` → Triggers network restart
    ///
    /// Example usage:
    /// ```swift
    /// // Connect to specific endpoint
    /// connection.startConnection(endpoint)
    /// 
    /// // Reconnect using previously stored endpoint
    /// connection.startConnection()
    /// ```
    ///
    /// - Parameter endpoint: Optional NWEndpoint to connect to. If nil, uses previously stored endpoint.
    func startConnection(_ endpoint: NWEndpoint? = nil) {
        guard connection == nil else {
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
            connection = NWConnection(to: nwEndpoint, using: params)
            lgg.info("Connecting to endpoint: \(nwEndpoint.debugDescription, privacy: .public)");
        }
        
        connection?.stateUpdateHandler = { [weak self] newState in
            switch (newState) {
            case .setup:
                lgg.info("Connection state: setup")
            case .preparing:
                lgg.info("Connection state: preparing")
            case .ready:
                networkStatus.connected = true // inform UI about connection status
                lgg.info("Connection state: ready")
            case .cancelled:
                networkStatus.connected = false // inform UI about connection status
                lgg.info("Connection state: cancelled")
            case .waiting(let err):
                lgg.info("Connection state: waiting, error: \(err, privacy: .public)")
            case .failed(let err):
                lgg.info("Connection state: failed, error: \(err, privacy: .public)")
                networkStatus.connected = false // inform UI about connection status
                self?.remoteAccess?.restartNetwork()
            default:
                lgg.info("Connection state: unknown: \(String(describing: newState), privacy: .public)")
            }
        }
        
        connection?.viabilityUpdateHandler = { [weak self] isViable in
            if (isViable) {
                lgg.info("Connection is viable")
                if let remoteEndpoint = self?.connection?.currentPath?.remoteEndpoint {
                    lgg.info("Connected to \(remoteEndpoint.debugDescription, privacy: .public)")
                    if let interface = remoteEndpoint.interface {
                        lgg.info("Connected over interface type \(String(describing: interface.type), privacy: .public) with name \(interface.name, privacy: .public)")
                    }
                }
                
                // authenticate this client to its server
                self?.authenticateClientToServer()
            } else {
                lgg.info("Connection is not viable")
            }
        }
        
        connection?.betterPathUpdateHandler = { betterPathAvailable in
            if (betterPathAvailable) {
                lgg.info("Connection: A better path is availble")
            } else {
                lgg.info("Connection: No better path is available")
            }
        }
        
        connection?.start(queue: .global())
    }
    
    /// Sends payload data over the QUIC connection.
    ///
    /// Sends the provided data using the established QUIC connection. If a send error occurs,
    /// it triggers a network restart. The method silently returns if no connection exists.
    ///
    /// - Parameter payload: The data to send over the connection
    func send(_ payload: Data) {
        connection?.send(content: payload, completion: .contentProcessed({ sendError in
            if let error = sendError {
                lgg.error("Error sending data: \(error, privacy: .public)")
                self.remoteAccess?.restartNetwork()
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
        connection?.cancel()
        // wait maximum 1 second for connection to become cancelled
        for _ in 1...100 {
            Thread.sleep(forTimeInterval: 0.01)
            if connection?.state == .cancelled {
                break
            }
        }
        connection = nil
    }
}
