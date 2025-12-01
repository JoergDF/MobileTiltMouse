package com.example.mobiletiltmouse

import org.junit.Test
import org.junit.Assert.*
import tech.kwik.core.QuicClientConnection
import tech.kwik.core.QuicStream
import org.mockito.kotlin.*

class ConnectionTest {

    @Test
    fun init() {
        val mockRemoteAccess =  mock<RemoteAccess>()
        val conn = Connection(null, mockRemoteAccess, null)

        assertNull(conn.connection)
        assertNull(conn.quicStream)
        assertNull(conn.savedAddressWithPort)
        assertEquals(mockRemoteAccess, conn.remoteAccess)
    }

    @Test
    fun startConnection() {
        val conn = Connection(null, null, null)

        conn.startConnection(null)
        assertNull(conn.savedAddressWithPort)
        assertNull(conn.connection)

        conn.startConnection("")
        assertNull(conn.savedAddressWithPort)
        assertNull(conn.connection)

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
        val conn = Connection(null, null, null)

        conn.connection = mockQuicClientConnection
        conn.quicStream = mockQuicStream

        conn.stopConnection()

        verify(mockQuicClientConnection).closeAndWait()
        assertNull(conn.connection)
        assertNull(conn.quicStream)
        assertFalse(NetworkState.isConnected)
    }

    @Test
    fun checkServerCertificate() {
        val conn = Connection(null, null, null)

        // empty or invalid certificate
        assertFalse(conn.checkServerCertificate(byteArrayOf()))
        assertFalse(conn.checkServerCertificate(byteArrayOf(0x01, 0x02, 0x03)))
    }
}