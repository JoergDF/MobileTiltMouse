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

