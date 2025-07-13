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
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration


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
open class Connection(private val context: Context?, val remoteAccess: RemoteAccess?) {
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
        // reference hash of server certificate (DER format, in file cert.der)
        // can be retrieved from command line tool: shasum -a 256 cert.der
        val referenceCertHash =
            "4f4bf5b86ea43dbcf9ae83d2cb6cfc0ed8c9da9ef027b8026ee184818452f214".hexToByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(serverCert)
        return hash contentEquals referenceCertHash
    }

    /**
     * Loads the client certificate from the app's assets.
     *
     * This function reads the "cert_client.der" file from the application's assets directory,
     * parses it as an X.509 certificate, and returns it as an X509Certificate object.
     * The certificate is expected to be in DER (binary) format.
     *
     * @return The loaded X509Certificate instance representing the client certificate.
     */
    fun loadClientCertificate(): X509Certificate {
        val inputStream = context!!.assets.open("cert_client.der")
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(inputStream) as X509Certificate
    }

    /**
     * Loads the client private key from the app's assets.
     *
     * This function reads the "key_client.der" file from the application's assets directory,
     * interprets it as a PKCS#8 encoded RSA private key, and returns it as a PrivateKey object.
     * The key file must be in DER (binary) format.
     *
     * @return The loaded PrivateKey instance representing the client's private key.
     */
     fun loadClientKey(): PrivateKey {
         val keyBytes = context!!.assets.open("key_client.der").readBytes()
         val keySpec = PKCS8EncodedKeySpec(keyBytes)
         val keyFactory = KeyFactory.getInstance("RSA")
         return keyFactory.generatePrivate(keySpec)
    }

    /**
     * Establishes a new QUIC connection to the server.
     *
     * This function initiates a connection using the tech.kwik QUIC client library. It performs the following steps:
     * - Uses the provided server address (or a previously saved address) to build a connection URI.
     * - Loads the client certificate and private key from the app's assets and configures them for mutual TLS authentication.
     * - Configures and creates a new QUIC client connection with specified timeouts and logging.
     * - Attempts to establish the connection and verifies that it is connected.
     * - Validates the server certificate against a predefined reference hash.
     * - Creates a communication stream.
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

        // load client certificate and key
        val clientCert: X509Certificate
        val clientKey: PrivateKey
        try {
            clientCert = loadClientCertificate()
            clientKey = loadClientKey()
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

        quicStream = connection?.createStream(false)
        Log.d(TAG, "Stream created")

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