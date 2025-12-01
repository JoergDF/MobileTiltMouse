package com.example.mobiletiltmouse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

class ClientCertificateTest {
    private lateinit var conn: Connection
    private lateinit var context: Context


    @Before
    fun setup()  {
        context = ApplicationProvider.getApplicationContext()
        conn = Connection(context, null, null)
    }



    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun loadClientCertificate() = runTest {
        val cert = conn.loadClientCertificate()
        val certDer = cert.encoded

        val md = MessageDigest.getInstance("SHA-256")
        val hashCert = md.digest(certDer)

        assertArrayEquals(
            "019641942271cf481efdb9416b0a06e5ae42f1d8d28dd30ecf6946149fdbc002".hexToByteArray(),
            hashCert
        )
    }

    @Test
    fun loadClientKey() = runTest {
        val key = conn.loadClientKey()

        assertEquals("RSA", key.algorithm)
        assertEquals("PKCS#8", key.format)
    }

}