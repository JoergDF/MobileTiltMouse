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
        val (cert, _) = conn.loadClientCertificateAndKey()
        val certDer = cert.encoded

        val md = MessageDigest.getInstance("SHA-256")
        val hashCert = md.digest(certDer)

        assertArrayEquals(
            "78f63567c0bb005c25cea87a4483eb7f97a31ed65882f3907a4539bde9dfa189".hexToByteArray(),
            hashCert
        )
    }

    @Test
    fun loadClientKey() = runTest {
        val (_, key) = conn.loadClientCertificateAndKey()

        assertEquals("RSA", key.algorithm)
        assertEquals("PKCS#8", key.format)
    }

}