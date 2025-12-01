package com.example.mobiletiltmouse

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.collections.toByteArray
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.random.Random
import android.security.keystore.KeyProperties
import android.security.keystore.KeyGenParameterSpec
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec


private const val TAG = "Pairing"


/**
 * Manages the device pairing process with a server.
 *
 * This class orchestrates the entire pairing flow, from initial handshake to successful
 * pairing. It handles the creation and secure storage of a client device ID and a remote key,
 * communicates with the server to exchange identification, and manages the user-facing
 * pairing code entry if required.
 *
 * Key responsibilities:
 * - **Key Management**: Securely creates, stores, and retrieves a persistent remote key
 *   and a device ID using the [Crypto] class.
 * - **Server Trust**: Utilizes a [ServerIdRepository] to maintain a list of trusted servers.
 * - **Pairing Flow**:
 *   1. Requests a server ID by sending a securely generated remote key.
 *   2. Checks if the server's ID is already known.
 *   3. Sends its own device ID to the server.
 *   4. If the server and client do not recognize each other, it prompts the user to enter a
 *      pairing code by updating [PairingStatus].
 *   5. Transmits the user-entered code and handles the server's response.
 * - **State Management**: Updates [PairingStatus] to control the UI, such as showing or
 *   hiding the pairing code entry view.
 *
 * @property mouseActions An optional [MouseActions] instance to which the connection is passed
 *                        after a successful pairing to begin sending mouse events.
 * @property userSettings The data store for persisting encrypted keys and IDs.
 * @property ioDispatcher The coroutine dispatcher for background I/O operations, defaulting
 *                        to `Dispatchers.IO`, changed by unit tests.
 */
class Pairing(
    val mouseActions: MouseActions?,
    val userSettings: UserSettings,
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    var connection: Connection? = null
    var serverIdRepository = ServerIdRepository(userSettings, ioDispatcher)
    var newServerID = byteArrayOf()

    private val scope = CoroutineScope(ioDispatcher)

    /**
     * Updates the global pairing status to control the UI.
     *
     * This function safely updates the UI state from any coroutine context by switching
     * to the main dispatcher. It can modify whether the pairing code entry view is visible
     * or if the last entered code was rejected.
     *
     * @param showCodeEntry If not null, sets the visibility of the pairing code entry view.
     * @param codeRejected If not null, sets the status indicating if the pairing code was rejected.
     */
    suspend fun setPairingStatus(showCodeEntry: Boolean?, codeRejected: Boolean?) {
        withContext(Dispatchers.Main) {
            if (showCodeEntry != null) {
                PairingStatus.showCodeEntry = showCodeEntry
            }
            if (codeRejected != null) {
                PairingStatus.codeRejected = codeRejected
            }
        }
    }

    /**
     * Retrieves the remote key from secure storage or creates a new one.
     *
     * This key is used for decrypting the server ID on the server and it is send in the
     * server ID request message to the server. If a key is not already stored, a new 32-byte
     * random key is generated, encrypted, and persisted in [UserSettings]. If decryption of an
     * existing key fails, it generates and stores a new one to recover from potential data corruption.
     *
     * @return The 32-byte remote key.
     */
    suspend fun getRemoteKey(): ByteArray = withContext(ioDispatcher) {
        val cryptRemoteKey = Crypto("remote_key")
        val remoteKeyEncrypted = userSettings.getRemoteKey.first()
        if (remoteKeyEncrypted != null) {
            try {
                return@withContext cryptRemoteKey.decrypt(remoteKeyEncrypted)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt remote key, a new one will be generated.", e)
            }
        }

        val newRemoteKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val newRemoteKeyEncrypted = cryptRemoteKey.encrypt(newRemoteKey)
            userSettings.setRemoteKey(newRemoteKeyEncrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt and save new remote key", e)
        }
        return@withContext newRemoteKey
    }


    /**
     * Initiates the handshake by requesting the server's ID.
     *
     * It sends the client's remote key to the server and waits for a response containing
     * the server's ID. Upon receiving the ID, it checks if the server is already trusted
     * using [ServerIdRepository] and notifies the server of its status. It then proceeds
     * to send the client's device ID.
     */
    suspend fun requestServerId() {
        val header = byteArrayOf(0x40)
        val message = header + getRemoteKey()
        connection?.send(message)
        message.fill(0) // clear key

        val response = ByteArray(33)
        val len = connection?.readBuffer(response)
        if (len == response.size) {
            // check header
            if (response[0].toInt() == 0x41) {
                // get server ID
                val servId = response.slice(1..response.lastIndex).toByteArray()
                // send server-ID-status message
                if (serverIdRepository.isKnownID(servId)) {
                    connection?.send(byteArrayOf(0x42))
                    Log.d(TAG, "Server ID is known")
                } else {
                    connection?.send(byteArrayOf(0x43))
                    newServerID = servId
                    Log.d(TAG, "Server ID is new")
                }
                sendDeviceId()
            } else {
                Log.e(TAG, "Error receiving server ID, invalid header: ${response[0]}")
            }
        } else {
            Log.e(TAG, "Error receiving server ID, invalid length: $len")
        }
    }


    /**
     * Generates a unique SHA-256 hash to be used as the device ID.
     *
     * The ID is created by hashing a combination of a random UUID and a 32-byte
     * random array, ensuring a high degree of uniqueness.
     *
     * @return [ByteArray] The generated 32-byte SHA-256 hash.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun createDeviceId(): ByteArray {
        val uuid = Uuid.random()
        val randomArray = ByteArray(32) { Random.nextInt(0, 255).toByte() }

        val md = MessageDigest.getInstance("SHA-256")
        md.update(uuid.toByteArray())
        md.update(randomArray)
        val hash = md.digest()
        return hash
    }


    /**
     * Retrieves the device ID from secure storage or creates a new one.
     *
     * If a device ID is not already stored in [UserSettings], it calls [createDeviceId]
     * to generate a new one, which is then encrypted and persisted. If decryption of an
     * existing ID fails, it attempts to create and store a new ID to recover from potential
     * data corruption.
     *
     * @return The 32-byte device ID.
     */
    suspend fun getDeviceId(): ByteArray = withContext(ioDispatcher) {
        val crypt = Crypto("client_id_key")
        val deviceIdEncrypted = userSettings.getDeviceId.first()
        if (deviceIdEncrypted != null) {
            try {
                return@withContext crypt.decrypt(deviceIdEncrypted)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt device ID, a new one will be generated.", e)
            }
        }

        val newDeviceId = createDeviceId()
        try {
            val newDeviceIdEncrypted = crypt.encrypt(newDeviceId)
            userSettings.setDeviceId(newDeviceIdEncrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt and save device ID", e)
        }
        return@withContext newDeviceId
    }


    /**
     * Sends the device ID to the server and handles its response.
     *
     * The server's response determines the next step:
     * - If both client and server are known to each other, pairing is complete, and it exits.
     * - If not, it prompts the user for a pairing code by updating [PairingStatus].
     */
    suspend fun sendDeviceId() {
        val header = byteArrayOf(0x50)
        val message = header + getDeviceId()
        connection?.send(message)

        val response = connection?.readByte()
        when (response?.toInt()) {
            0x51 -> {
                Log.d(TAG, "Server response: Server and client ID are known.")
                exitPairing(false)
            }
            0x52 -> {
                Log.d(TAG, "Server response: Unknown server ID or client ID.")
                setPairingStatus(showCodeEntry = true, codeRejected = false)
            }
            else -> Log.e(TAG, "Unknown Device ID response: $response")
        }
    }

    /**
     * Sends the user-entered pairing code to the server.
     *
     * The code is packed into a compact byte array format. It handles the server's
     * response, either exiting the pairing process on acceptance or signaling
     * rejection via [PairingStatus]. On success, the new server ID is stored.
     *
     * @param code A list of strings representing the digits of the pairing code in range 0..9.
     *             The code must have exactly 5 digits, because of the message format.
     */
    suspend fun sendPairingCode(code: List<String>) = withContext(ioDispatcher) {
        assert(code.size == 5) { "Invalid count of code digits - cannot be sent." }
        assert(code.all { it.length == 1 }) { "Invalid code digit length." }

        // each digit must fit into 4 bits
        val codi = code.map { it.toIntOrNull() ?: 0xF }
        Log.d(TAG, "Sending pairing code: ${codi.toList()}")

        val header = 0x60
        val message = byteArrayOf(
            (header or (codi[4] and 0x0F)).toByte(),
            ((codi[3] shl 4) or codi[2]).toByte(),
            ((codi[1] shl 4) or codi[0]).toByte()
        )

        connection?.send(message)
        val response = connection?.readByte()
        when (response?.toInt()) {
            0x61 -> {
                Log.d(TAG, "Pairing code accepted.")
                serverIdRepository.storeID(newServerID)
                setPairingStatus(showCodeEntry = null, codeRejected = false)
                exitPairing(true)
            }
            0x62 -> {
                Log.d(TAG, "Pairing code rejected.")
                setPairingStatus(showCodeEntry = null, codeRejected = true)
            }
            else -> Log.e(TAG, "Unknown Device ID response: $response")
        }
    }

    /**
     * Starts the pairing process with the given connection.
     *
     * This function initializes the pairing flow by setting the active connection
     * and launching a coroutine to begin the server ID handshake.
     *
     * @param connection The [Connection] instance to be used for communication with the server.
     */
    fun startPairing(connection: Connection) {
        Log.d(TAG, "Starting pairing...")
        this.connection = connection
        scope.launch {
            requestServerId()
        }
    }

    /**
     * Finalizes and exits the pairing process.
     *
     * It passes the active connection to the [MouseActions] handler to commence mouse
     * event transmission. The UI for code entry is hidden, with an optional delay to
     * improve user experience by showing the final input.
     *
     * @param closeViewWithDelay If true, delays hiding the code entry UI to ensure
     *                           the user sees the feedback.
     */
    suspend fun exitPairing(closeViewWithDelay: Boolean) {
        mouseActions?.startMouseActionsConnection(this.connection)
        this.connection = null
        if (closeViewWithDelay) {
            delay(500)
        }
        setPairingStatus(showCodeEntry = false, codeRejected = null)
        Log.d(TAG, "Exited pairing.")
    }

    /**
     * Resets all pairing-related data stored in [UserSettings].
     *
     * This function clears the device ID, the list of known server IDs, and the remote key
     * by setting their values to `null` in the persistent storage. This effectively
     * un-pairs the device, requiring the pairing process to be completed again on the
     * next connection attempt.
     */
    suspend fun resetPairing() = withContext(ioDispatcher) {
        userSettings.setDeviceId(null)
        userSettings.setServerIds(null)
        userSettings.setRemoteKey(null)
        Log.d(TAG, "Resetpairing")
    }
}

/**
 * Manages the secure storage and retrieval of server IDs.
 *
 * This repository is responsible for maintaining a list of trusted server IDs. It encrypts the
 * list of IDs before persisting them in [UserSettings] and decrypts them upon retrieval.
 * The repository ensures a fixed number of IDs are stored, cycling out the oldest ones
 * when a new ID is added and the list is full. Server IDs are hashed before storage
 * for uniform size and to obscure the original value.
 *
 * @property userSettings The data store for persisting encrypted server IDs.
 * @property ioDispatcher The coroutine dispatcher for access to [UserSettings]
 */
class ServerIdRepository(
    val userSettings: UserSettings,
    val ioDispatcher: CoroutineDispatcher
) {
    val maxIDs = 5
    val crypt = Crypto("server_id_key")

    /**
     * Retrieves the list of known server ID hashes from storage.
     *
     * It decrypts the stored data from [UserSettings]. If no data exists (e.g., on first
     * app launch), it initializes the store with a list of dummy hashes. If decryption fails,
     * it logs an error and returns an empty list.
     *
     * @return A list of stored server ID hashes.
     */
    suspend fun getIDs(): List<ByteArray> = withContext(ioDispatcher) {
        var serverIDs = listOf<ByteArray>()
        val encryptedBytes = userSettings.getServerIds.first()
        if (encryptedBytes == null) {
            // first call after app installation: no server IDs stored yet, fill with dummy data
            serverIDs = List(maxIDs) {
                hash(ByteArray(32).also { SecureRandom().nextBytes(it) })
            }
            val flatIDs = serverIDs.flatMap { it.toList() }.toByteArray()
            val encryptedData = crypt.encrypt(flatIDs)
            userSettings.setServerIds(encryptedData)
        } else {
            try {
                val flatIDs = crypt.decrypt(encryptedBytes)
                serverIDs = flatIDs.asList().chunked(32).map { it.toByteArray() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read or parse server IDs from file.", e)
            }
        }
        serverIDs
    }


    /**
     * Checks if a given server ID is already known.
     *
     * The method hashes the provided `serverID` and compares it against the list of
     * stored hashes.
     *
     * @param serverID The raw server ID to check.
     * @return `true` if the hash of the ID is found in the repository, `false` otherwise.
     */
    suspend fun isKnownID(serverID: ByteArray): Boolean {
        val hashedServerID = hash(serverID)
        return getIDs().any { it.contentEquals(hashedServerID) }
    }

    /**
     * Stores a new server ID, making it a known ID for future connections.
     *
     * The ID is hashed and added to the beginning of the list. This speeds up search process, as
     * the latest added ID is found first. If the repository is already at maximum capacity (`maxIDs`),
     * the oldest ID is removed. The updated list is then encrypted and saved.
     *
     * @param serverID The raw server ID to store.
     */
    suspend fun storeID(serverID: ByteArray) = withContext(ioDispatcher) {
        if (serverID.isEmpty()) {
            return@withContext
        }

        val serverIDs = getIDs().toMutableList()

        while (serverIDs.size >= maxIDs) {
            serverIDs.removeAt(serverIDs.lastIndex)
        }
        serverIDs.add(0, hash(serverID))

        val flatIDs = serverIDs.flatMap { it.toList() }.toByteArray()

        try {
            val encryptedData = crypt.encrypt(flatIDs)
            userSettings.setServerIds(encryptedData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt and store server IDs", e)
        }
   }

    /**
     * Computes a SHA-256 hash of the given byte array.
     *
     * This is used to create a uniform, fixed-size representation of a server ID before
     * it is stored.
     *
     * @param id The input byte array to hash.
     * @return The 32-byte SHA-256 hash.
     */
    fun hash(id: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(id)
    }
}

/**
 * Handles AES/GCM encryption and decryption using the Android KeyStore.
 *
 * This class provides methods for encrypting and decrypting data using a secret key
 * stored securely in the Android KeyStore. It automatically creates a 256-bit AES key
 * for the given `keyAlias` if one doesn't already exist. The initialization
 * vector (IV) is prepended to the ciphertext and automatically handled during
 * decryption.
 *
 * @property keyAlias A unique alias to identify the cryptographic key in the Android KeyStore.
 */
class Crypto(val keyAlias: String) {
    val androidKeyStore = "AndroidKeyStore"
    val cipherAlgorithm = "AES/GCM/NoPadding"

    /**
     * Creates and stores a new AES-256 secret key in the Android KeyStore.
     *
     * The key is configured for both encryption and decryption using GCM block mode,
     * which provides authenticated encryption.
     *
     * @return The newly generated [SecretKey].
     */
    private fun createSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            androidKeyStore
        )
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Retrieves the secret key from the Android KeyStore or creates it if it doesn't exist.
     *
     * It attempts to load the key associated with the `keyAlias`. If no key is found,
     * it proceeds to generate a new one.
     *
     * @return The existing or newly created [SecretKey].
     */
    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(androidKeyStore).apply {
            load(null)
        }
        return (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: createSecretKey()
    }

    /**
     * Encrypts the given byte array.
     *
     * The method prepends the initialization vector (IV) and its size to the ciphertext,
     * creating a self-contained payload.
     *
     * @param data The plaintext data to encrypt.
     * @return A byte array containing the IV size, the IV, and the ciphertext.
     */
    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(cipherAlgorithm)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val encryptedData = cipher.doFinal(data)
        val iv = cipher.iv
        val ivSize = iv.size
        return byteArrayOf(ivSize.toByte()) + iv + encryptedData
    }

    /**
     * Decrypts a byte array that was previously encrypted by this class.
     *
     * It reads the prepended initialization vector (IV) size and the IV from the payload
     * to correctly initialize the cipher for decryption.
     *
     * @param encryptedData The byte array containing the IV and ciphertext.
     * @return The original decrypted plaintext data.
     */
    fun decrypt(encryptedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(cipherAlgorithm)
        val ivSize = encryptedData[0].toInt()
        val iv = encryptedData.copyOfRange(1, ivSize+1)
        val dataToDecrypt = encryptedData.copyOfRange(ivSize+1, encryptedData.size)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(dataToDecrypt)
    }
}