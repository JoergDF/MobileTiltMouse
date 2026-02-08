import Testing
import Network
import Foundation
@testable import MobileTiltMouse


@Suite struct ConnectionTests {
    
    class MockSendOfConnection: Connection {
        var sendData: [Data] = []
        override func send(_ payload: Data) {
            sendData.append(payload)
        }
    }
    
    
    @Test
    func initialisation() {
        let errAlert = ErrorAlert()
        let networkStatus = NWStatus()
        let pairing = Pairing(mouseAction: nil, pairingStatus: PairingStatus(), networkStatus: NWStatus())
        let conn = Connection(errAlert: errAlert, networkStatus: networkStatus, pairing: pairing)
        
        #expect(conn.nwConnection == nil)
        #expect(conn.endpoint == nil)
        #expect(conn.errAlert === errAlert)
        #expect(conn.networkStatus === networkStatus)
        #expect(conn.pairing === pairing)
    }
    
    @Test
    func loadServerCertHash() {
        let conn = Connection(errAlert: ErrorAlert(), networkStatus: NWStatus(), pairing: nil)
        let arr = conn.loadServerCertHash()
        
        #expect(arr != nil)
        #expect(arr?.count == 32)
        // not all elements are 0, so their sum is not 0
        #expect(arr?.reduce(0, { UInt32($0) + UInt32($1) }) != 0)
    }
    
    @Test
    func loadClientCertificate() {
        let conn = Connection(errAlert: ErrorAlert(), networkStatus: NWStatus(), pairing: nil)
        let identity = conn.loadClientCertificate()
        
        #expect(identity != nil)
        
        var privateKey: SecKey?
        let statusKey = SecIdentityCopyPrivateKey(identity!, &privateKey)
        #expect(statusKey == errSecSuccess)
        #expect(privateKey != nil)
        
        var certificate: SecCertificate?
        let statusCert = SecIdentityCopyCertificate(identity!, &certificate)
        #expect(statusCert == errSecSuccess)
        #expect(certificate != nil)
    }
    
    @Test
    func startConnection() {
        let testEndpoint = NWEndpoint.hostPort(host: "localhost", port: 22222)
        let conn = Connection(errAlert: ErrorAlert(), networkStatus: NWStatus(), pairing: nil)
        conn.startConnection(testEndpoint)
        
        #expect(conn.nwConnection != nil)
        #expect(conn.endpoint == testEndpoint)
        
        // Calling startConnection again should not create a new connection
        let initialConnection = conn.nwConnection
        conn.startConnection(testEndpoint)
        
        #expect(conn.nwConnection === initialConnection)
    }
    
    @Test
    func stopConnection() {
        let testEndpoint = NWEndpoint.hostPort(host: "localhost", port: 22222)
        let conn = Connection(errAlert: ErrorAlert(), networkStatus: NWStatus(), pairing: nil)
        conn.startConnection(testEndpoint)
        
        #expect(conn.nwConnection != nil)
        
        conn.stopConnection()
        
        #expect(conn.nwConnection == nil)
        
    }
    
}

