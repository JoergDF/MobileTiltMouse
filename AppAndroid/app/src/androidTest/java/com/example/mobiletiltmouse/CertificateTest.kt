package com.example.mobiletiltmouse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class CertificateTest {
    private lateinit var conn: Connection
    private lateinit var context: Context


    @Before
    fun setup()  {
        context = ApplicationProvider.getApplicationContext()
        conn = Connection(context, null, null)
    }

    @Test
    fun loadServerCertificateHash() = runTest {
        val hash = conn.loadServerCertificateHash()
        assertEquals(32, hash.size)
        assertNotEquals(0, hash.sum())
    }

    @Test
    fun loadClientCertificateAndKey() = runTest {
        val (cert, key) = conn.loadClientCertificateAndKey()

        // certificate
        assertEquals("CN=rcgen self signed cert", cert.subjectX500Principal.name)
        assertEquals("CN=rcgen self signed cert", cert.issuerX500Principal.name)
        assertTrue(cert.subjectAlternativeNames.contains(listOf(2, "localhost")))

        // key
        assertEquals("RSA", key.algorithm)
        assertEquals("PKCS#8", key.format)
    }

    @Test
    fun checkServerCertificate() = runTest {
        // empty or invalid certificate
        assertFailsWith<InvalidCertificateException>() {
            conn.checkServerCertificate(byteArrayOf())
        }
        assertFailsWith<InvalidCertificateException>() {
            conn.checkServerCertificate(byteArrayOf(0x01, 0x02, 0x03))
        }
    }
}