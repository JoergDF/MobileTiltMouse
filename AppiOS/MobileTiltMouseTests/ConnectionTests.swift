import Testing
import Network
import Foundation
@testable import MobileTiltMouse


@Suite struct ConnectionTests {
    
    class MockRemoteAccess: RemoteAccess {
        override init() {
            
        }
    }

    class MockSendOfConnection: Connection {
        var sendData: [Data] = []
        override func send(_ payload: Data) {
            sendData.append(payload)
        }
    }
    
    
    @Test
    func initialisation() {
        let remoteAccess = MockRemoteAccess()
        let conn = Connection(remoteAccess)
        
        #expect(conn.connection == nil)
        #expect(conn.endpoint == nil)
        #expect(conn.remoteAccess === remoteAccess)
    }
    
    @Test
    func startConnection() {
        let testEndpoint = NWEndpoint.hostPort(host: "localhost", port: 22222)
        let conn = Connection(nil)
        conn.startConnection(testEndpoint)
        
        #expect(conn.connection != nil)
        #expect(conn.endpoint == testEndpoint)
        
        // Calling startConnection again should not create a new connection
        let initialConnection = conn.connection
        conn.startConnection(testEndpoint)
        
        #expect(conn.connection === initialConnection)
    }
    
    @Test
    func stopConnection() {
        let testEndpoint = NWEndpoint.hostPort(host: "localhost", port: 22222)
        let conn = Connection(nil)
        conn.startConnection(testEndpoint)
        
        #expect(conn.connection != nil)
        
        conn.stopConnection()
        
        #expect(conn.connection == nil)
        
    }
    
    @Test
    func authenticateClientToServer() {
        let conn = MockSendOfConnection(nil)
        conn.authenticateClientToServer()
        
        #expect(conn.sendData.count == 32)
     
        // Check headers
        #expect(conn.sendData[ 0...15].allSatisfy { $0.first == 0xA0 })
        #expect(conn.sendData[16...31].allSatisfy { $0.first == 0xB0 })
    }
    
}

