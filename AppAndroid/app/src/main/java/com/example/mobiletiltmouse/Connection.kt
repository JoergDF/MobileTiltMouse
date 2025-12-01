package com.example.mobiletiltmouse

// Quic from
// https://github.com/ptrd/kwik
// https://github.com/ptrd/agent15

import android.content.Context
import java.net.URI
import android.util.Log
import tech.kwik.agent15.env.PlatformMapping
import tech.kwik.core.QuicClientConnection
import tech.kwik.core.QuicStream
import tech.kwik.core.log.SysOutLogger
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Duration


private const val ALPN = "mobiletiltmouseproto"
private const val TAG = "Connection"
// reference hash of server certificate (DER format, in file cert.der)
// can be retrieved from command line tool: shasum -a 256 cert.der
private const val serverCertHashString = "50f3eea7c26b1c2a22d3593c37e8f66afcc431265c93c0ce1f4a7544aa7a6c55"
// client PKCS#12-cert password
private const val clientCertPassword = "mtm_client"

/**
 * Manages the QUIC connection to the server.
 *
 * This class encapsulates the logic for establishing, maintaining, and communicating
 * over a secure QUIC connection using the `tech.kwik` library. It handles
 * server authentication, client authentication, data transmission, and error handling.
 *
 * Key responsibilities include:
 * - Connection Lifecycle: Starting, stopping, and re-establishing the connection.
 * - Security:
 *    - Verifying the server's identity by matching its certificate against a known hash.
 *    - Authenticating the client using a bundled certificate and private key.
 * - Data Transfer: Sending and receiving data over the QUIC stream.
 * - Pairing: Initiating the device pairing process with the server upon a successful connection.
 * - Error Handling: Detecting connection issues and triggering recovery mechanisms.
 *
 * @param context The Android application context, used to access assets like certificates.
 * @param remoteAccess An optional handler for managing network state and orchestrating connection restarts.
 * @param pairing An optional handler for the device pairing process.
 */
open class Connection(private val context: Context?, val remoteAccess: RemoteAccess?, val pairing: Pairing?) {
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
     * Shows an alert to the user when client certificate loading fails.
     *
     * The alert uses the global ErrorAlert object to trigger the alert display
     * in the UI layer.
     */
    private fun clientCertificateAlert() {
        ErrorAlert.show = true
        ErrorAlert.message = "Loading of client certificate failed."
    }

    /**
     * Shows an alert to the user when server certificate verification fails.
     *
     * The alert uses the global ErrorAlert object to trigger the alert display
     * in the UI layer.
     */
    private fun serverCertificateAlert() {
        ErrorAlert.show = true
        ErrorAlert.message = "Check of server certificate failed."
    }

    /**
     * Validates the provided server certificate by comparing its SHA-256 hash with a predefined
     * reference hash.
     *
     * The certificate must be in the binary DER format. This function computes the SHA-256 digest
     * of the certificate and checks whether it exactly matches the known reference hash.
     *
     * @param serverCert The server certificate in DER format as a ByteArray.
     * @return True if the certificate's hash matches the reference hash; false otherwise.
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun checkServerCertificate(serverCert: ByteArray): Boolean {
        val referenceCertHash = serverCertHashString.hexToByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(serverCert)
        return hash contentEquals referenceCertHash
    }

    /**
     * Loads the client certificate and private key from the "cert.p12" file in assets.
     *
     * This function reads the PKCS#12 file, parses it using the provided password,
     * and extracts the first certificate and private key entry it finds.
     *
     * @return A Pair containing the client's X509Certificate and PrivateKey.
     */
    private fun loadClientCertificateAndKey(): Pair<X509Certificate, PrivateKey> {
        val inputStream = context!!.assets.open("cert.p12")
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(inputStream, clientCertPassword.toCharArray())

        // Get the first and only alias
        val alias = keyStore.aliases().nextElement()

        val certificate = keyStore.getCertificate(alias) as X509Certificate
        val privateKey = keyStore.getKey(alias, clientCertPassword.toCharArray()) as PrivateKey

        return Pair(certificate, privateKey)
    }

    /**
     * Establishes a QUIC connection to the server and initiates a communication stream.
      *
     * The process is as follows:
     * 1.  Determines the target server address: If `addressWithPort` is provided, it is used
     *     and saved for future connections. Otherwise, a previously saved address is used.
     * 2.  Loads the client certificate and private key for client authentication.
     * 3.  Builds and establishes a `QuicClientConnection`. The connection is configured with
     *     the appropriate application protocol (ALPN) and a 30-second timeout.
     * 4.  Manually verifies the server's certificate against a hardcoded hash to prevent
     *     man-in-the-middle attacks.
     * 5.  Creates a bidirectional QUIC stream for sending and receiving data.
     * 6.  Updates the global `NetworkState` to reflect the active connection.
     * 7.  Initiates the pairing process via the `pairing` handler.
     *
     * If a connection is already active, the function returns immediately. If errors occur
     * during connection (e.g., certificate loading, connection failure), it may trigger a
     * network restart via the `remoteAccess` handler and/or display an alert.
     *
     * @param addressWithPort The server address in "host:port" format. If null or empty,
     *                        the last successfully used address is tried.
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

        // load client certificate and key
        val (clientCert, clientKey) = try {
            loadClientCertificateAndKey()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading client certificate: $e")
            clientCertificateAlert()
            throw e
        }

        Log.d(TAG, "Connection start to $savedAddressWithPort")

        // establish connection
        try {
            connection = QuicClientConnection.newBuilder()
                .uri(URI("$ALPN://$savedAddressWithPort"))
                .applicationProtocol(ALPN)
                .logger(log)
                .connectTimeout(Duration.ofSeconds(30))
                .noServerCertificateCheck()
                .clientCertificate(clientCert)
                .clientCertificateKey(clientKey)
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

        // verify server certificate
        if (!checkServerCertificate(connection!!.serverCertificateChain[0].encoded)) {
            Log.e(TAG, ErrorAlert.message)
            serverCertificateAlert()
            return
        }
        Log.d(TAG, "Server certificate check passed")

        quicStream = connection?.createStream(true)
        Log.d(TAG, "Stream created")

        NetworkState.isConnected = true

        pairing?.startPairing(this)
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
     * Reads a single byte from the QUIC stream.
     *
     * This function attempts to read one byte from the input stream of the established
     * QUIC connection. If the stream is not available or the end of the stream has
     * been reached, it returns null.
     *
     * @return The byte read from the stream, or null if no byte could be read.
     */
    open fun readByte(): Byte? {
        try {
            return quicStream?.inputStream?.read()?.toByte()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading byte: $e")
            return null
        }
    }

    /**
     * Reads a sequence of bytes from the QUIC stream into the provided buffer.
     *
     * This function attempts to fill the entire `buffer` with data from the input stream.
     * It returns the number of bytes actually read. If the number of bytes read is less
     * than the buffer's capacity or an I/O error occurs, it returns null.
     *
     * @param buffer The byte array to fill with data from the stream.
     * @return The number of bytes read into the buffer, or `null` if an exception occurred.
     */
    open fun readBuffer(buffer: ByteArray): Int? {
        try {
            val len = quicStream?.inputStream?.read(buffer)
            if (len == null || len < buffer.size) {
                Log.e(TAG, "Error reading enough bytes: $len < ${buffer.size}")
            }
            return len
        } catch (e: Exception) {
            Log.e(TAG, "Error reading buffer: $e")
            return null
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