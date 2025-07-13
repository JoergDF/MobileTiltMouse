package com.example.mobiletiltmouse

import org.junit.Test
import org.junit.Assert.*
import tech.kwik.core.QuicClientConnection
import tech.kwik.core.QuicStream
//import org.mockito.Mockito.*
import org.mockito.kotlin.*

class ConnectionTest {

    class MockConnection : Connection(null, null) {
        var sendData = mutableListOf<ByteArray>()
        override fun send(data: ByteArray) {
            sendData.add(data)
        }
    }

    @Test
    fun init() {
        val mockRemoteAccess =  mock<RemoteAccess>()
        val conn = Connection(null, mockRemoteAccess)

        assertEquals(conn.connection, null)
        assertEquals(conn.quicStream, null)
        assertEquals(conn.savedAddressWithPort, null)
        assertEquals(conn.remoteAccess, mockRemoteAccess)
    }

    @Test
    fun startConnection() {
        val conn = Connection(null, null)

        conn.startConnection(null)
        assertEquals(conn.savedAddressWithPort, null)
        assertEquals(conn.connection, null)

        conn.startConnection("")
        assertEquals(conn.savedAddressWithPort, null)
        assertEquals(conn.connection, null)

        // already connected, prevent a new connect attempt
        val mockQuicClientConnection = mock<QuicClientConnection>()
        conn.connection = mockQuicClientConnection
        val addressWithPort = "localhost:22222"
        conn.startConnection(addressWithPort)
        assertEquals(addressWithPort, conn.savedAddressWithPort)
    }

    @Test
    fun stopConnection() {
        val mockQuicClientConnection = mock<QuicClientConnection>()
        val mockQuicStream = mock<QuicStream>()
        val conn = Connection(null, null)

        conn.connection = mockQuicClientConnection
        conn.quicStream = mockQuicStream

        conn.stopConnection()

        verify(mockQuicClientConnection).closeAndWait()
        assertEquals(conn.connection, null)
        assertEquals(conn.quicStream, null)
        assertFalse(NetworkState.isConnected)
    }

    @Test
    fun checkServerCertificate() {
        val conn = Connection(null, null)

        // empty or invalid certificate
        assertFalse(conn.checkServerCertificate(byteArrayOf()))
        assertFalse(conn.checkServerCertificate(byteArrayOf(0x01, 0x02, 0x03)))
    }
}