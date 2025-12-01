package com.example.mobiletiltmouse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import javax.crypto.AEADBadTagException
import kotlin.experimental.inv


class PairingTest {
    private lateinit var userSettings: UserSettings
    private lateinit var context: Context

    class MockConnection : Connection(null, null, null) {
        var sendData = mutableListOf<ByteArray>()
        override fun send(data: ByteArray) {
            sendData.add(data.clone())
        }

        var readB: Byte = 0x00
        override fun readByte(): Byte {
            return readB
        }

        var readBuf = byteArrayOf()
        override fun readBuffer(buffer: ByteArray): Int? {
            readBuf.copyInto(buffer)
            return readBuf.size
        }

    }

    @Before
    fun setup()  {
        context = ApplicationProvider.getApplicationContext()
        userSettings = UserSettings(context)
        runBlocking { userSettings.deleteAll() }
        PairingStatus.codeRejected = false
        PairingStatus.showCodeEntry = false
    }

    @After
    fun tearDown() = runTest {
        userSettings.deleteAll()
    }

    @Test
    fun crypto() = runTest {
        val crypt = Crypto("test_crypto")
        val data = byteArrayOf(1, 2 ,3)
        val encrypted = crypt.encrypt(data)
        val decrypted = crypt.decrypt(encrypted)
        assertArrayEquals(decrypted, data)

        // Decryption with a different key should fail
        val crypt2 = Crypto("test_crypto2")
        assertThrows(AEADBadTagException::class.java) {
            crypt2.decrypt(encrypted)
        }

        // Encrypting the same data twice should produce different results due to random IV
        val encryptedAgain = crypt.encrypt(data)
        assertFalse(encrypted.contentEquals(encryptedAgain))

        // Empty data
        val emptyData = byteArrayOf()
        val encryptedEmpty = crypt.encrypt(emptyData)
        val decryptedEmpty = crypt.decrypt(encryptedEmpty)
        assertArrayEquals( emptyData, decryptedEmpty)

        // Corrupted data should fail
        val corruptedEncryptedData = encrypted.clone()
        corruptedEncryptedData[corruptedEncryptedData.lastIndex] = corruptedEncryptedData[corruptedEncryptedData.lastIndex].inv()
        assertThrows(AEADBadTagException::class.java) {
            crypt.decrypt(corruptedEncryptedData)
        }
    }

    @Test
    fun serverIdRepository() = runTest {
        val serverId = byteArrayOf(1,2,3)
        val serverIdRepository = ServerIdRepository(
            userSettings,
            StandardTestDispatcher(testScheduler)
        )

        // hashing
        val hashedData = serverIdRepository.hash(serverId)
        assertEquals(32, hashedData.size)
        assertTrue(hashedData.all { it != 0x00.toByte() })
        assertFalse(hashedData.sliceArray(0..serverId.size).contentEquals(serverId))

        // initial data
        val initIDs = serverIdRepository.getIDs()
        assertEquals(serverIdRepository.maxIDs, initIDs.size)
        for (i in 0..<serverIdRepository.maxIDs) {
            assertEquals(32, initIDs[i].size)
            assertFalse(initIDs[i].contentEquals(initIDs[(i + 1) % serverIdRepository.maxIDs]))
        }

        // id not stored
        assertFalse(serverIdRepository.isKnownID(serverId))

        // id stored
        serverIdRepository.storeID(serverId)
        assertTrue(serverIdRepository.isKnownID(serverId))

        // another instance (simulating app restart) should find the ID
        val serverIdRepository2 = ServerIdRepository(
            userSettings,
            StandardTestDispatcher(testScheduler)
        )
        assertTrue(serverIdRepository2.isKnownID(serverId))

        // push saved id out of storage
        for (i in 0..<serverIdRepository.maxIDs) {
            assertTrue(serverIdRepository.isKnownID(serverId))
            serverIdRepository.storeID(byteArrayOf(i.toByte(), i.toByte(), i.toByte()))
        }
        assertFalse(serverIdRepository.isKnownID(serverId))

        // empty ID should not be stored
        val preIds = serverIdRepository.getIDs()
        serverIdRepository.storeID(byteArrayOf())
        val postIds = serverIdRepository.getIDs()
        for (i in 0..<serverIdRepository.maxIDs) {
            assertArrayEquals(preIds[i], postIds[i])
        }

        // corrupt userSettings of ServerIdRepository
        userSettings.setServerIds(byteArrayOf(9,8,7,6,5))
        val ids = serverIdRepository.getIDs()
        assertTrue(ids.isEmpty())
        assertFalse(serverIdRepository.isKnownID(serverId))
        // should be fixed by storing an ID
        serverIdRepository.storeID(serverId)
        assertTrue(serverIdRepository.isKnownID(serverId))
    }

    @Test
    fun remoteKey() = runTest {
        val pairing = Pairing(null, userSettings,StandardTestDispatcher(testScheduler))

        // key should not change
        val key1 = pairing.getRemoteKey()
        val key2 = pairing.getRemoteKey()
        assertEquals(32, key1.size)
        assertArrayEquals(key1, key2)

        // corrupted userSettings should result in new random key
        userSettings.setRemoteKey(byteArrayOf(1,2,3))
        val key3 = pairing.getRemoteKey()
        assertEquals(32, key3.size)
        assertFalse(key3.contentEquals(key1))

        val key4 = pairing.getRemoteKey()
        assertArrayEquals(key3, key4)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startPairing() = runTest {
        val pairing = Pairing(null, userSettings, StandardTestDispatcher())
        val mockConn = MockConnection()
        pairing.startPairing(mockConn)
        advanceUntilIdle()  // on API29 this works only, if StandardTestDispatcher() is used and not StandardTestDispatcher(testScheduler)

        assertEquals(mockConn, pairing.connection)
    }

    @Test
    fun requestServerId() = runTest {
        val pairing = Pairing(null, userSettings,StandardTestDispatcher(testScheduler))
        val mockConn = MockConnection()
        pairing.connection = mockConn

        // server ID unknown
        //
        val serverId = ByteArray(32) { 0x5A }
        mockConn.readBuf = byteArrayOf(0x41) + serverId
        pairing.requestServerId()
        // should send request id message
        assertEquals(0x40.toByte(), mockConn.sendData[0][0])
        assertEquals(33, mockConn.sendData[0].size)
        // should send that server is unknown
        assertEquals(0x43.toByte(), mockConn.sendData[1][0])
        assertEquals(1, mockConn.sendData[1].size)
        assertArrayEquals(serverId, pairing.newServerID)
        // sendDeviceId() should be called, it sends a first message with header 0x50
        assertEquals(0x50.toByte(), mockConn.sendData[2][0])

        // server ID is known
        //
        mockConn.sendData = mutableListOf<ByteArray>()
        pairing.serverIdRepository.storeID(serverId)
        pairing.requestServerId()
        // should send request id message
        assertEquals(0x40.toByte(), mockConn.sendData[0][0])
        assertEquals(33, mockConn.sendData[0].size)
        // check for server-ID-is-known message
        assertEquals(0x42.toByte(), mockConn.sendData[1][0])
        assertEquals(1, mockConn.sendData[1].size)
        // sendDeviceId() should be called, it sends a first message with header 0x50
        assertEquals(0x50.toByte(), mockConn.sendData[2][0])

        // invalid server responses, hence no server-id-status message should be sent back to server
        //
        // server response too short
        mockConn.sendData = mutableListOf<ByteArray>()
        mockConn.readBuf = byteArrayOf(1,2,3)
        pairing.requestServerId()
        // only request-server-id message was sent, nothing else
        assertEquals(1, mockConn.sendData.size)

        // server response with invalid header
        mockConn.sendData = mutableListOf<ByteArray>()
        mockConn.readBuf = ByteArray(33) { 0x51 }
        pairing.requestServerId()
        // only request-server-id message was sent, nothing else
        assertEquals(1, mockConn.sendData.size)
    }

    @Test
    fun createDeviceId() {
        val pairing = Pairing(null, userSettings)
        val deviceId1 = pairing.createDeviceId()
        assertEquals(32, deviceId1.size)
        val deviceId2 = pairing.createDeviceId()
        assertFalse(deviceId1.contentEquals(deviceId2))
    }

    @Test
    fun getDeviceId() = runTest {
        val pairing = Pairing(null, userSettings,StandardTestDispatcher(testScheduler))

        val id1 = pairing.getDeviceId()
        val id2 = pairing.getDeviceId()
        assertEquals(32, id1.size)
        assertArrayEquals(id1, id2)

        // corrupted userSettings should result in new random device ID
        userSettings.setDeviceId(byteArrayOf(7,8,9))
        val id3 = pairing.getDeviceId()
        assertEquals(32, id3.size)
        assertFalse(id3.contentEquals(id2))

        val id4 = pairing.getDeviceId()
        assertArrayEquals(id3, id4)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun sendDeviceId_knownIDs() = runTest {
        val mouseActions = MouseActions(null)
        val pairing = Pairing(mouseActions, userSettings,StandardTestDispatcher(testScheduler))
        val mockConn = MockConnection()
        pairing.connection = mockConn
        mockConn.readB = 0x51
        pairing.sendDeviceId()
        advanceUntilIdle() // required as of scope.launch in exitPairing()

        // should send device id message and exit
        assertEquals(0x50.toByte(), mockConn.sendData[0][0])
        assertEquals(33, mockConn.sendData[0].size)
        assertFalse(PairingStatus.showCodeEntry)
        assertFalse(PairingStatus.codeRejected)
        assertEquals(mockConn, pairing.mouseActions?.connection)
        assertNull(pairing.connection)
   }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun sendDeviceId_unknownIDs() = runTest {
        val mouseActions = MouseActions(null)
        val pairing = Pairing(mouseActions, userSettings, StandardTestDispatcher(testScheduler))
        val mockConn = MockConnection()
        pairing.connection = mockConn

        // invalid server response: nothing should change
        mockConn.readB = 0xFF.toByte()
        pairing.sendDeviceId()
        // should send device id message and trigger code entry UI
        assertEquals(0x50.toByte(), mockConn.sendData[0][0])
        assertEquals(33, mockConn.sendData[0].size)
        assertFalse(PairingStatus.showCodeEntry)
        assertFalse(PairingStatus.codeRejected)
        assertNull(pairing.mouseActions?.connection)
        assertEquals(mockConn, pairing.connection)

        // server response: server ID or device ID unknown
        mockConn.readB = 0x52
        pairing.sendDeviceId()
        // should send device id message and trigger code entry UI
        assertEquals(0x50.toByte(), mockConn.sendData[1][0])
        assertEquals(33, mockConn.sendData[1].size)
        assertTrue(PairingStatus.showCodeEntry)
        assertFalse(PairingStatus.codeRejected)
        assertNull(pairing.mouseActions?.connection)
        assertEquals(mockConn, pairing.connection)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun sendPairingCode() = runTest {
        val mouseActions = MouseActions(null)
        val pairing = Pairing(mouseActions, userSettings, StandardTestDispatcher(testScheduler))
        val mockConn = MockConnection()
        pairing.connection = mockConn
        pairing.setPairingStatus(showCodeEntry = true, codeRejected = false)

        // invalid input code
        pairing.sendPairingCode(listOf("A", " ", "F", "+", "E"))
        assertArrayEquals(byteArrayOf(0x6F, 0xFF.toByte(), 0xFF.toByte()), mockConn.sendData[0])

        // invalid server response: nothing should change
        mockConn.readB = 0xFF.toByte()
        pairing.sendPairingCode(listOf("1", "2", "3", "4", "5"))
        assertFalse(PairingStatus.codeRejected)
        assertTrue(PairingStatus.showCodeEntry)
        assertNull(mouseActions.connection)

        // server response: code rejected
        mockConn.readB = 0x62
        pairing.sendPairingCode(listOf("1", "2", "3", "4", "5"))
        assertArrayEquals(byteArrayOf(0x65, 0x43, 0x21), mockConn.sendData[1])
        assertTrue(PairingStatus.codeRejected)
        assertTrue(PairingStatus.showCodeEntry)
        assertNull(mouseActions.connection)

        // server response: code accepted
        mockConn.readB = 0x61
        val newServerID = ByteArray(32) { 0x45 }
        pairing.newServerID = newServerID
        pairing.sendPairingCode(listOf("9", "2", "3", "4", "0"))
        advanceUntilIdle()
        assertArrayEquals(byteArrayOf(0x60, 0x43, 0x29), mockConn.sendData[3])
        assertFalse(PairingStatus.codeRejected)
        assertFalse(PairingStatus.showCodeEntry)
        assertTrue(pairing.serverIdRepository.isKnownID(newServerID))
        // check mouseActions got a connection
        assertEquals(mockConn, mouseActions.connection)
        assertNull(pairing.connection)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun exitPairing() = runTest {
        val mouseActions = MouseActions(null)
        val pairing = Pairing(mouseActions, userSettings, StandardTestDispatcher(testScheduler))
        val mockConn = MockConnection()
        pairing.connection = mockConn
        PairingStatus.showCodeEntry = true

        pairing.exitPairing(false)
        advanceUntilIdle()
        assertFalse(PairingStatus.showCodeEntry)
        assertEquals(mockConn, pairing.mouseActions?.connection)
        assertNull(pairing.connection)
    }

    @Test
    fun setPairingStatus() = runTest {
        val pairing = Pairing(null, userSettings)
        PairingStatus.showCodeEntry = false
        PairingStatus.codeRejected = false
        pairing.setPairingStatus(showCodeEntry = true, codeRejected = true)
        assertTrue(PairingStatus.showCodeEntry)
        assertTrue(PairingStatus.codeRejected)
        pairing.setPairingStatus(showCodeEntry = null, codeRejected = null)
        assertTrue(PairingStatus.showCodeEntry)
        assertTrue(PairingStatus.codeRejected)
        pairing.setPairingStatus(showCodeEntry = false, codeRejected = true)
        assertFalse(PairingStatus.showCodeEntry)
        assertTrue(PairingStatus.codeRejected)
        pairing.setPairingStatus(showCodeEntry = true, codeRejected = false)
        assertTrue(PairingStatus.showCodeEntry)
        assertFalse(PairingStatus.codeRejected)
        pairing.setPairingStatus(showCodeEntry = null, codeRejected = true)
        assertTrue(PairingStatus.showCodeEntry)
        assertTrue(PairingStatus.codeRejected)
        pairing.setPairingStatus(showCodeEntry = false, codeRejected = null)
        assertFalse(PairingStatus.showCodeEntry)
        assertTrue(PairingStatus.codeRejected)
    }

    @Test
    fun resetPairing() = runTest {
        val pairing = Pairing(null, userSettings, StandardTestDispatcher(testScheduler))

        val initialDeviceId = byteArrayOf(1, 2, 3)
        val initialServerIds = byteArrayOf(4, 5, 6)
        val initialRemoteKey = byteArrayOf(7, 8, 9)

        userSettings.setDeviceId(initialDeviceId)
        userSettings.setServerIds(initialServerIds)
        userSettings.setRemoteKey(initialRemoteKey)

        // Ensure the initial values are set correctly
        assertArrayEquals(initialDeviceId, userSettings.getDeviceId.first())
        assertArrayEquals(initialServerIds, userSettings.getServerIds.first())
        assertArrayEquals(initialRemoteKey, userSettings.getRemoteKey.first())

        pairing.resetPairing()

        assertNull(userSettings.getDeviceId.first())
        assertNull(userSettings.getServerIds.first())
        assertNull(userSettings.getRemoteKey.first())
    }
}