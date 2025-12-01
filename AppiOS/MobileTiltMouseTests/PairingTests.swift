import Testing
import Foundation
@testable import MobileTiltMouse

class MockConnection: Connection {
    var sentData: Data?
    var receiveHandler: ((Data?) -> Void)?
    var sentDataOnly: Data?
    
    init() {
        super.init(errAlert: ErrorAlert(), networkStatus: NWStatus(), pairing: nil)
    }
    
    override func sendReceive(sendMessage: Data, receive: @escaping (Data?) -> Void) {
        sentData = sendMessage
        receiveHandler = receive
    }
    
    override func send(_ message: Data) {
        sentDataOnly = message
    }
}

// do not run these tests in parallel as there are permanently stored values in UserDefaults and Keychain
@Suite(.serialized) class PairingTests {  // deinit requires class instead of struct

    init() {
        
    }
    
    deinit {
        
    }
    
    @Test
    func initialize() {
        let ma = MouseActions()
        let ps = PairingStatus()
        let ns = NWStatus()
        let pairing = Pairing(mouseAction: ma, pairingStatus: ps, networkStatus: ns)
        #expect(pairing.mouseAction === ma)
        #expect(pairing.pairingStatus === ps)
        #expect(pairing.networkStatus === ns)
    }
    
    @Test
    func getRemoteKey() {
        UserDefaults.standard.removeObject(forKey: "remoteKey")
        let pairing = Pairing(mouseAction: nil, pairingStatus: PairingStatus(), networkStatus: NWStatus())
        let key1 = pairing.getRemoteKey()
        #expect(key1.count == 32)
        let key2 = pairing.getRemoteKey()
        #expect(key1 == key2)
        
        // corrupted UserDefaults should result in new random remote key
        UserDefaults.standard.set([1,2,3], forKey: "remoteKey")
        let key3 = pairing.getRemoteKey()
        #expect(key3.count == 32)
        #expect(key1 != key3)
        
        let key4 = pairing.getRemoteKey()
        #expect(key3 == key4)
        
        // cleanup
        UserDefaults.standard.removeObject(forKey: "remoteKey")
    }
    
    @Test
    func requestServerId() {
        let mockConnection = MockConnection()
        let pairing = Pairing(mouseAction: MouseActions(), pairingStatus: PairingStatus(), networkStatus: NWStatus())
        pairing.connection = mockConnection
        
        pairing.requestServerId()
        
        #expect(mockConnection.sentData != nil)
        #expect(mockConnection.sentData?.count == 33)
        #expect(mockConnection.sentData?[0] == 0x40)
        
        pairing.serverIDs.storeID(Data([11,22,33]))
        
        mockConnection.receiveHandler!(Data([0x41, 11,22,33]))
        #expect(mockConnection.sentDataOnly == Data([0x42]))
        
        // unknown server id
        pairing.requestServerId()
        mockConnection.receiveHandler!(Data([0x41, 1,2,3]))
        #expect(pairing.newServerId == Data([1,2,3]))
        #expect(mockConnection.sentDataOnly == Data([0x43]))
    }
    
    @Test
    func createDeviceId() {
        let pairing = Pairing(mouseAction: nil, pairingStatus: PairingStatus(), networkStatus: NWStatus())
        let deviceId1 = pairing.createDeviceId()
        #expect(deviceId1.count == 32)
        let deviceId2 = pairing.createDeviceId()
        #expect(deviceId1 != deviceId2)
    }
    
    @Test
    func getDeviceId() {
        UserDefaults.standard.removeObject(forKey: "deviceId")
        let pairing = Pairing(mouseAction: nil, pairingStatus: PairingStatus(), networkStatus: NWStatus())
        let deviceId1 = pairing.getDeviceId()
        #expect(deviceId1.count == 32)
        let deviceId2 = pairing.getDeviceId()
        #expect(deviceId1 == deviceId2)
        
        // corrupted UserDefaults should result in new random device ID
        UserDefaults.standard.set([1,2,3], forKey: "deviceId")
        let deviceId3 = pairing.getDeviceId()
        #expect(deviceId3.count == 32)
        #expect(deviceId1 != deviceId3)
        
        let deviceId4 = pairing.getDeviceId()
        #expect(deviceId3 == deviceId4)
        
        // cleanup
        UserDefaults.standard.removeObject(forKey: "deviceId")
    }

    @Test
    func sendDeviceId() {
        let mockConnection = MockConnection()
        let pairing = Pairing(mouseAction: MouseActions(), pairingStatus: PairingStatus(), networkStatus: NWStatus())
        pairing.connection = mockConnection
                
        pairing.sendDeviceId()

        #expect(mockConnection.sentData != nil)
        #expect(mockConnection.sentData?.count == 33)
        #expect(mockConnection.sentData?[0] == 0x50)
        
        // receive invalid response: nothing should change
        mockConnection.receiveHandler!(Data([0xFF]))
        #expect(pairing.pairingStatus.showCodeEntry == false)
        #expect(pairing.connection === mockConnection)
        #expect(pairing.mouseAction?.connection == nil)
        
        // receive: unknown device
        mockConnection.receiveHandler!(Data([0x52]))
        #expect(pairing.pairingStatus.showCodeEntry == true)
        #expect(pairing.mouseAction?.connection == nil)
        
        // receive: known device
        mockConnection.receiveHandler!(Data([0x51]))
        #expect(pairing.pairingStatus.showCodeEntry == false)
        #expect(pairing.connection == nil)
        #expect(pairing.mouseAction?.connection === mockConnection)
    }
    
    @Test
    func sendPairingCode() {
        let mockConnection = MockConnection()
        let pairing = Pairing(mouseAction: MouseActions(), pairingStatus: PairingStatus(), networkStatus: NWStatus())
        pairing.connection = mockConnection
        
        pairing.sendPairingCode([" 1", " 2", " 9", " 0", " 5"])
        #expect(mockConnection.sentData == Data([0x65, 0x09, 0x21]))
       
        // invalid input code 
        pairing.sendPairingCode([" A", "  ", "3 ", " +", " F"])
        #expect(mockConnection.sentData == Data([0x6F, 0xFF, 0xFF]))
        
        // receive invalid response: nothing should change
        mockConnection.receiveHandler!(Data([0xFF]))
        #expect(pairing.pairingStatus.showCodeEntry == false)
        #expect(pairing.pairingStatus.codeRejected == false)
        #expect(pairing.mouseAction?.connection == nil)
        
        // receive: code wrong
        mockConnection.receiveHandler!(Data([0x62]))
        #expect(pairing.pairingStatus.showCodeEntry == true)
        #expect(pairing.pairingStatus.codeRejected == true)
        #expect(pairing.mouseAction?.connection == nil)
        
        // receive: code correct
        mockConnection.receiveHandler!(Data([0x61]))
        #expect(pairing.pairingStatus.showCodeEntry == false)
        #expect(pairing.pairingStatus.codeRejected == false)
        #expect(pairing.connection == nil)
        #expect(pairing.mouseAction?.connection === mockConnection)
    }
    
    @Test
    func startPairing() {
        let conn = MockConnection()
        let pairing = Pairing(mouseAction: MouseActions(), pairingStatus: PairingStatus(), networkStatus: NWStatus())
        pairing.startPairing(connection: conn)
        #expect(conn === pairing.connection)
    }
    
    @Test
    func serverIDs() {
        UserDefaults.standard.removeObject(forKey: "serverIDs")
        let data = Data([3,4,5])
        let servId = ServerIDs()
        
        // initial data
        let ids = servId.getIDs()
        #expect(ids.count == servId.maxIDs)
        for i in ids.indices {
            #expect(ids[i].count > 32)
            #expect(ids[i] != ids[(i+1) % servId.maxIDs])
        }
        
        // hashing
        let hashedData = servId.hash(data)
        #expect(hashedData.count == 32)
        #expect(hashedData.allSatisfy({$0 != 0}))
        #expect(data != hashedData[0...data.count])
        
        // store ID
        #expect(servId.isKnownID(data) == false)
        servId.storeID(data)
        #expect(servId.isKnownID(data))
        
        // another instance (simulating app restart) should find the ID
        let servId2 = ServerIDs()
        #expect(servId2.isKnownID(data))
        
        // push saved id out of storage
        for i in 0..<servId.maxIDs {
            #expect(servId.isKnownID(data))
            servId.storeID( Data([i,i,i].map{UInt8($0)}) )
        }
        #expect(servId.isKnownID(data) == false)
        
        // empty ID should not be stored
        let preIDs = servId.getIDs()
        servId.storeID(Data())
        let postIDs = servId.getIDs()
        for i in 0..<servId.maxIDs {
            #expect(preIDs[i] == postIDs[i])
        }
        
        // corrupt userSettings
        UserDefaults.standard.set(0, forKey: "serverIDs")
        #expect(servId.getIDs().isEmpty)
        #expect(servId.isKnownID(data) == false)
        // should be fixed by storing an ID
        servId.storeID(data)
        #expect(servId.isKnownID(data))
        
        // cleanup
        UserDefaults.standard.removeObject(forKey: "serverIDs")
    }
    
    @Test
    func Crypto() {
        let crypt = MobileTiltMouse.Crypto(keyChainService: "testService", keyChainAccount: "testKey")
        
        let key1 = crypt.getKey()
        let key2 = crypt.getKey()
        #expect(key1 == key2)
        
        let data = Data([1,2,3])
        let encrypted = crypt.encrypt(data)
        #expect(encrypted != nil)
        let decrypted = crypt.decrypt(encrypted!)
        #expect(decrypted == data)
        
        // Encrypting the same data twice should produce different results
        let encrypted2 = crypt.encrypt(data)
        #expect(encrypted != encrypted2)
        
        // Decryption with a different key should fail
        let crypt2 = MobileTiltMouse.Crypto(keyChainService: "testService", keyChainAccount: "wrongKey")
        let decryptedWrongKey = crypt2.decrypt(encrypted!)
        #expect(decryptedWrongKey == nil)
        
        // Empty data
        let emptyData = Data([])
        let encryptedEmpty = crypt.encrypt(emptyData)
        let decryptedEmpty = crypt.decrypt(encryptedEmpty!)
        #expect(decryptedEmpty == emptyData)
        
        // Corrupted data should fail
        let corruptedEncryptedData = encrypted!.dropLast(1) + Data([encrypted!.last! ^ 0xFF])
        let decryptedCorruptedData = crypt.decrypt(corruptedEncryptedData)
        #expect(decryptedCorruptedData == nil)
    }
    
    @Test
    func exitPairing() {
        let conn = MockConnection()
        let pairing = Pairing(mouseAction: MouseActions(), pairingStatus: PairingStatus(), networkStatus: NWStatus())
        pairing.connection = conn
        pairing.networkStatus.connected = false
        pairing.pairingStatus.showCodeEntry = true

        pairing.exitPairing()
        #expect(pairing.mouseAction?.connection != nil)
        #expect(pairing.connection == nil)
        #expect(pairing.networkStatus.connected)
        #expect(pairing.pairingStatus.showCodeEntry == false)
    }
    
    @Test
    func restartPairing() {
        let pairing = Pairing(mouseAction: MouseActions(), pairingStatus: PairingStatus(), networkStatus: NWStatus())
        
        let initialDeviceId = Data([1,2,3])
        let initialServerId = Data([4,5,6])
        let initialRemoteKey = Data([7,8,9])
        
        UserDefaults.standard.set(initialDeviceId, forKey: "deviceId")
        UserDefaults.standard.set(initialServerId, forKey: "serverIDs")
        UserDefaults.standard.set(initialRemoteKey, forKey: "remoteKey")
        
        // Ensure the initial values are set correctly
        #expect(UserDefaults.standard.data(forKey: "deviceId") == initialDeviceId)
        #expect(UserDefaults.standard.data(forKey: "serverIDs") == initialServerId)
        #expect(UserDefaults.standard.data(forKey: "remoteKey") == initialRemoteKey)
        
        pairing.resetPairing()
        
        #expect(UserDefaults.standard.data(forKey: "deviceId") == nil)
        #expect(UserDefaults.standard.data(forKey: "serverIDs") == nil)
        #expect(UserDefaults.standard.data(forKey: "remoteKey") == nil)
    }
}
