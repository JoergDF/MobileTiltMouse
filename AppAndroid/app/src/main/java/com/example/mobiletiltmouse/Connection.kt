package com.example.mobiletiltmouse

// Quic from
// https://github.com/ptrd/kwik
// https://github.com/ptrd/agent15

import java.net.URI
import android.util.Log
import tech.kwik.agent15.env.PlatformMapping
import tech.kwik.core.QuicClientConnection
import tech.kwik.core.QuicStream
import tech.kwik.core.log.SysOutLogger
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


private const val ALPN = "mobiletiltmouseproto"
private const val TAG = "Connection"

/**
 * Connection handles the establishment and maintenance of a QUIC connection between the client and
 * server.
 *
 * This class uses the tech.kwik QUIC libraries to:
 * - Establish a connection using a custom application protocol (ALPN).
 * - Verify the server certificate against a predefined reference hash.
 * - Authenticate the client to the server via HMAC-SHA256 on a random message.
 * - Transmit data over a QUIC stream.
 * - Manage the connection lifecycle, including starting, sending data, and stopping the connection.
 *
 * Usage:
 * - Instantiate Connection with an optional RemoteAccess handler.
 * - Call startConnection() with the server address (host:port) to initiate a connection.
 * - Use send(data: ByteArray) to send data over the established connection.
 * - Call stopConnection() to cleanly close the connection.
 *
 * Security:
 * - A certificate check is performed to ensure the server's legitimacy.
 * - Client authentication is implemented for additional security.
 *
 * @param remoteAccess An optional RemoteAccess instance to manage network state and reconnection logic.
 */
open class Connection(val remoteAccess: RemoteAccess?) {
    var connection: QuicClientConnection? = null
    var quicStream: QuicStream? = null
    var savedAddressWithPort: String? = null
    private val log = SysOutLogger()

    init {
        PlatformMapping.usePlatformMapping(PlatformMapping.Platform.Android)

        // log.logPackets(true)
        // log.logInfo(true)
    }

    /**
     * Validates the provided server certificate by comparing its SHA-256 hash with a predefined
     * reference hash.
     *
     * The certificate must be in the binary DER format. This function computes the SHA-256 digest
     * of the certificate and checks whether it exactly matches the known reference hash obtained
     * from a trusted source (e.g., output of "shasum -a 256 cert.der").
     *
     * @param serverCert The server certificate in DER format as a ByteArray.
     * @return True if the certificate's hash matches the reference hash; false otherwise.
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun checkCertificate(serverCert: ByteArray): Boolean {
        // reference hash of server certificate (DER format, in file cert.der)
        // can be retrieved from command line tool: shasum -a 256 cert.der
        val referenceCertHash =
            "4f4bf5b86ea43dbcf9ae83d2cb6cfc0ed8c9da9ef027b8026ee184818452f214".hexToByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(serverCert)
        return hash contentEquals referenceCertHash
    }

    /**
     * Authenticates the client to the server.
     *
     * This function performs client authentication using the following steps:
     * 1. Generates a key from a predefined byte array and hashes it using SHA-512.
     * 2. Creates a random 32-byte message.
     * 3. Initializes an HMAC-SHA256 MAC instance with the derived key and computes the digest of
     *    the random message.
     * 4. Sends the authentication data in two parts:
     *    - The random message is sent with header 0xA.
     *    - The computed HMAC digest is sent with header 0xB.
     *
     * The helper function `sendAuthData` is used to split the data into even-sized chunks and
     * transmit each with its header.
     *
     * Authentication packet format:
     * ```
     * +--------------+-------------+----------------+
     * | 4-bit header | 4-bit zeros | 2 bytes data   |
     * +--------------+-------------+----------------+
     * ```
     */
     fun authenticateClientToServer() {
        fun sendAuthData(header: Int, data: ByteArray) {
            check(data.size % 2 == 0) { "Send authentication: Data size must be even" }

            val headDataByte = (header shl 4).toByte()

            val chunks = data.toList().chunked(2)
            chunks.forEach {
                send(byteArrayOf(headDataByte) + it)
            }
        }

        var key = arrayOf(
            0x24, 0xe3, 0x82, 0x41, 0x55, 0xc6, 0x1d, 0xdc,
            0x12, 0xe1, 0x8a, 0xc2, 0x02, 0x9f, 0x66, 0x5f,
            0x24, 0x19, 0xe8, 0x9d, 0x5e, 0x17, 0x6d, 0x55,
            0x07, 0x74, 0x37, 0x0d, 0x0e, 0x5f, 0xb6, 0x58
        ).map { it.toByte() }.toByteArray()

        val md = MessageDigest.getInstance("SHA-512")
        key = md.digest(key)

        val message = ByteArray(32)
        SecureRandom().nextBytes(message)

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val hmac = mac.doFinal(message)

        Log.d(TAG, "Send client authentication")

        sendAuthData(0xA, message)
        sendAuthData(0xB, hmac)
    }

    /**
     * Establishes a new QUIC connection to the server.
     *
     * This function initiates a connection using the tech.kwik QUIC client library. It performs the following steps:
     * - Uses the provided server address (or a previously saved address) to build a connection URI.
     * - Configures and creates a new QUIC client connection with specified timeouts and logging.
     * - Attempts to establish the connection and verifies that it is connected.
     * - Validates the server certificate against a predefined reference hash.
     * - Creates a communication stream and authenticates the client.
     * - Sets the connection as active by updating the network state.
     *
     * If no server address is available, the function logs the issue and aborts without connecting.
     * If any error occurs during connection establishment, the network is restarted via the RemoteAccess instance.
     *
     * @param addressWithPort An optional string specifying the server address (host:port). If null or empty,
     *                        a previously saved address will be used.
     */
    open fun startConnection(addressWithPort: String? = null) {
        if (addressWithPort.isNullOrEmpty()) {
            if (savedAddressWithPort == null) {
                Log.d(TAG, "No saved server address, not connecting")
                return
            }
            Log.d(TAG, "Using saved server address $savedAddressWithPort for connection")
        } else {
            savedAddressWithPort = addressWithPort
        }

        if (connection != null) {
            Log.d(TAG, "Connection already started")
            return
        }

        Log.d(TAG, "Connection start to $savedAddressWithPort")

        try {
            connection = QuicClientConnection.newBuilder()
                .uri(URI("$ALPN://$savedAddressWithPort"))
                .applicationProtocol(ALPN)
                .logger(log)
                .connectTimeout(Duration.ofSeconds(30))
                .noServerCertificateCheck()
                .build()
            Log.d(TAG, "Connection created")
            connection?.connect()
            check(connection?.isConnected == true) { "Connection attempt on start failed" }
        } catch (e: Exception) {
            Log.e(TAG, "Error connect: $e")
            remoteAccess?.restartNetwork()
            throw e
        }

        Log.d(TAG, "Connected to ${connection?.serverAddress}")

        if (!checkCertificate(connection!!.serverCertificateChain[0].encoded)) {
            ErrorAlert.show = true
            ErrorAlert.message = "Check of server certificate failed."
            Log.e(TAG, ErrorAlert.message)
            return
        }
        Log.d(TAG, "Server certificate check passed")

        quicStream = connection?.createStream(false)
        Log.d(TAG, "Stream created")

        authenticateClientToServer()

        NetworkState.isConnected = true
    }

    /**
     * Sends the provided byte array over the established QUIC connection.
     *
     * This function writes the supplied data to the output stream of the QUIC communication stream.
     * If an error occurs during data transmission, it logs the error and triggers a network restart via
     * the RemoteAccess instance.
     *
     * @param data The byte array to be transmitted over the connection.
     */
    open fun send(data: ByteArray) {
        try {
            quicStream?.outputStream?.write(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error send: $e")
            remoteAccess?.restartNetwork()
        }
    }

    /**
     * Closes the active QUIC connection.
     *
     * This function stops sending data immediately by nullifying the stream, then gracefully closes the
     * QUIC client connection using `closeAndWait()` to ensure that resources are properly released.
     * Finally, it updates the network state to indicate that the connection is no longer active.
     */
    fun stopConnection() {
        quicStream = null // stop sending data immediately
        connection?.closeAndWait()
        connection = null
        NetworkState.isConnected = false
        Log.d(TAG, "Connection stop")
    }
}