import Foundation
import Network
import SwiftUI
import CryptoKit



let alpnName = "mobiletiltmouseproto"
let connectionTimeout = 30  // seconds

/// Manages QUIC network connections with the remote server that controls mouse pointer.
///
/// The Connection class handles establishing, maintaining and closing secure QUIC connections. 
/// It provides:
/// - Secure connection setup with certificate verification 
/// - Client authentication using a PKCS#12 client certificate
/// - Connection state monitoring and UI updates
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
    
    /// Shows an alert to the user when server certificate verification fails.
    ///
    /// The alert uses the global `errAlert` observable object to trigger the alert display
    /// in the UI layer.
    private func serverCertificateAlert() {
        errAlert.error = true
        errAlert.message = "Check of server certificate failed."
    }
    
    /// Shows an alert to the user when client certificate loading fails.
    ///
    /// The alert uses the global `errAlert` observable object to trigger the alert display
    /// in the UI layer.
    private func clientCertificateAlert() {
        errAlert.error = true
        errAlert.message = "Loading of client certificate failed."
    }
    
     
    /// Loads the self-signed client certificate.
    ///
    /// This method attempts to load a PKCS#12 (.p12) client certificate named "ClientCertificate"
    /// from the app's asset catalog. It imports the certificate and extract the identity required 
    /// for client authentication in QUIC connections.
    ///
    /// Steps performed:
    /// 1. Loads the PKCS#12 data from the asset catalog.
    /// 2. Imports the certificate using the provided password.
    /// 3. Extracts the first identity from the imported items.
    /// 4. Returns the SecIdentity for use in network security configuration.
    ///
    /// If loading or importing fails, logs an error and returns nil.
    ///
    /// - Returns: The extracted `SecIdentity` if successful, or `nil` if loading/importing fails.
    func loadClientCertificate() -> SecIdentity? {
        guard let p12Data = NSDataAsset(name: "ClientCertificate")?.data else {
            lgg.error("Error: Could not load client certificate")
            return nil
        }
        let p12Options = [kSecImportExportPassphrase as String: "mtm"]
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
    /// 1. Requires exactly one certificate (no CA chain)
    /// 2. Extracts the certificate in DER format
    /// 3. Computes SHA-256 hash of the DER data
    /// 4. Compares against hardcoded reference hash
    ///
    /// To generate the reference hash for a new server certificate:
    /// ```bash
    /// # Convert certificate from PEM to DER format
    /// openssl x509 -in cert.pem -outform DER -out cert.der
    /// # Calculate SHA-256 hash
    /// shasum -a 256 cert.der
    /// ```
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
            
            // get DER format of received certificate
            // get its hash value and
            // compare hash against reference hash
            let dataDerCF = SecCertificateCopyData(certArray[0])
            
            let digest = SHA256.hash(data: dataDerCF as Data)
            
            // reference hash of server certificate (in DER format)
            // is output of command line tool: shasum -a 256 cert.der
            let referenceCertHash: [UInt8] = [
                0x4f, 0x4b, 0xf5, 0xb8, 0x6e, 0xa4, 0x3d, 0xbc,
                0xf9, 0xae, 0x83, 0xd2, 0xcb, 0x6c, 0xfc, 0x0e,
                0xd8, 0xc9, 0xda, 0x9e, 0xf0, 0x27, 0xb8, 0x02,
                0x6e, 0xe1, 0x84, 0x81, 0x84, 0x52, 0xf2, 0x14
            ]
            if !digest.elementsEqual(referenceCertHash) {
                lgg.error("Error: Certificate hash is wrong")
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
    /// - Loading and attaching a self-signed client certificate for mutual TLS authentication
    /// - Verification of self-signed server certificate
    ///
    /// - Returns: Configured NWParameters for QUIC connection
    /// - Important: The connection will fail if certificate checks do not pass.
    func quicNWParameters() -> NWParameters {
        let options = NWProtocolQUIC.Options(alpn: [alpnName])
        options.direction = .unidirectional
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
    /// State changes are monitored and trigger UI updates:
    /// - `.ready` → Sets connected status to true
    /// - `.cancelled` → Sets connected status to false
    /// - `.failed` → Connectopn stopped
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
                self?.stopConnection()
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
