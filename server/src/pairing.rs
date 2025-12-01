use quinn::{RecvStream, SendStream};
use tokio::time::{sleep, Duration};
use sha2::{Sha256, Digest};
use hkdf::Hkdf;
use rand::Rng;
use aes_gcm_siv::{
    aead::{rand_core::RngCore, Aead, KeyInit, OsRng}, AeadCore, Aes256GcmSiv, Nonce 
};
use zeroize::{Zeroize, Zeroizing};
use std::fs;
use std::io::prelude::*;
use std::time::SystemTime;
use crate::Result;


// number of digits of pairing code
const CODE_SIZE: usize = 5;
// filename for saving client IDs
const FILENAME_CLIENTS: &str = "clients.bin";
// filename for saving server IDs
const FILENAME_SERVER: &str = "server.bin";
// number of pairing code rejections before giving up
const REJECT_COUNT: i32 = 3; 
// maximum number of latest client/server IDs, oldest IDs are deleted
const ID_HISTORY: usize = 128;
// length of byte array of encrypted server ID + client ID
const ENCRYPTED_ID_SIZE: usize = 72;


/// Manages the entire pairing process between the server and a client.
///
/// This function orchestrates the steps required to establish a trusted relationship
/// between the server and a connecting client. The process involves exchanging and
/// verifying identities, and if necessary, performing a manual pairing code verification.
///
/// 1. Exchange IDs:
///    - Server receives key for its encrypted ID and sends its ID
///    - Client indicates if it knows the server
///    - Server receives client's ID and checks if client is known
/// 2. If both parties know each other:
///    - Send confirmation to client (0x51)
///    - Return successfully
/// 3. If either party is unknown:
///    - Send "start pairing" to client (0x52)
///    - Generate and display a random numeric pairing code
///    - Loop until correct code received or too many rejections:
///      * Receive client's code attempt
///      * If correct: save unknown client ID and return
///      * If incorrect: notify client (0x62), wait timeout, retry or, 
///        after a set number of failed attempts, generate new pairing code
/// 
/// # Arguments
///
/// * `recv_stream` - A mutable reference to the QUIC receiving stream for communication from the client.
/// * `send_stream` - A mutable reference to the QUIC sending stream for communication to the client.
///
/// # Errors
///
/// Returns an error if:
/// * Network communication fails (reading/writing streams)
/// * Invalid/unexpected message headers are received
/// * Reading/writing IDs from/to storage fails
pub async fn pairing(recv_stream: &mut RecvStream, send_stream: &mut SendStream) -> Result<()> {
    // send server id, receive client id, handle their storage and check if they are already known
    let (known_server, mut key) = handle_server_id(recv_stream, send_stream).await?;
    let (known_client, client_id) = handle_client_id(recv_stream, &key).await?;

    // check results and send them to client
    if known_client && known_server {
        // client and server know each other, pairing code is not needed
        send_stream.write_all(&[0x51u8]).await?;
        println!("Server and client already know each other.");
        return Ok(());
    } else {
        send_stream.write_all(&[0x52u8]).await?;
        println!("New devices, starting pairing process");
    }

    //
    // unknown client, start pairing process
    //
    let mut code = [0xFu8; CODE_SIZE];
    let mut reject_countdown = REJECT_COUNT;
    loop {
        // show pairing code that should be entered by the client
        if reject_countdown == REJECT_COUNT {
            generate_pairing_code(&mut code);
            println!("*************");
            println!("PAIRING CODE: {:?}", code);
            println!("*************");
        }
        
        // receive pairing code
        let mut data= [0u8; 3];
        recv_stream.read_exact(&mut data).await?;

        // check message header
        if (data[0] & 0xF0) != 0x60 {
            return Err(format!("Invalid message header of pairing code: 0x{:02x}", data[0]).into());
        }

        let code_rejected = !check_received_code(data, code);

        // send pairing code check result
        let data  = if code_rejected { [0x62u8] } else { [0x61u8] };
        send_stream.write_all(&data).await?;
        
        if code_rejected {
            println!("Pairing code rejected!");
            reject_countdown -= 1;
            if reject_countdown == 0 {
                reject_countdown = REJECT_COUNT;
                println!("Pairing code rejected too many times. Retry with new code.");
            }
            // if code was wrong, wait for a while before a new attempt is allowed: this is for security reasons
            sleep(Duration::from_secs(2)).await;
        } else {
            // pairing code was correct
            // if client was new (maybe only server id was new), save the client UUID to file
            if !known_client {
                save_id(&client_id, &key, FILENAME_CLIENTS)?;
            }
            key.zeroize();
            println!("Pairing successful.");
            return Ok(())
        }
    }
}

/// Handles the server ID exchange with the client.
///
/// This asynchronous function manages the initial part of the pairing process where
/// the server identifies itself to the client.
/// 
/// 1. Receives a 33-byte message from the client containing:
///    - Header byte (0x40) indicating "server-id-request"
///    - 32-byte key for encryption/decryption
/// 2. Uses the received key to retrieve or create the server's unique ID via `get_server_id()`
/// 3. Sends a 33-byte response to the client containing:
///    - Header byte (0x41) indicating "server-id"
///    - 32-byte server ID
/// 4. Waits for a 1-byte "server-id-status" response from client:
///    - 0x42: client knows this server
///    - 0x43: server is new to client
/// 
/// # Arguments
///
/// * `recv_stream` - A mutable reference to the QUIC receiving stream to read data from the client.
/// * `send_stream` - A mutable reference to the QUIC sending stream to send data to the client.
///
/// # Returns
/// 
/// A `Result` containing a tuple with two values:
/// * `Ok((true, [u8; 32]))` if client knows the server, received key from client.
/// * `Ok((false, [u8; 32]))` if server is new to client, received key from client.
/// * `Err` if there is a communication error or unexpected message header.
async fn handle_server_id(recv_stream: &mut RecvStream, send_stream: &mut SendStream) -> Result<(bool, [u8; 32])> {
    // receive key and client id
    let mut req= Zeroizing::new([0u8; 33]);
    recv_stream.read_exact( req.as_mut()).await?;
    // check message id
    if req[0] != 0x40 {
        return Err(format!("Invalid header of client id message: 0x{:02x}", req[0]).into());
    }
    
    let mut key = Zeroizing::new([0u8; 32]);
    key.copy_from_slice(&req[1..33]);
    let server_id = get_server_id(key.as_ref(), FILENAME_SERVER)?;
    
    // send server ID
    let mut data = [0u8; 33];
    data[0] = 0x41; // header
    data[1..].copy_from_slice(&server_id);
    send_stream.write_all(&data).await?;

    // receive server id status
    let mut server_id_status= [0u8; 1];
    recv_stream.read_exact(&mut server_id_status).await?;

    let known_server = match server_id_status[0] {
        0x42 => true,
        0x43 => false,
        other => return Err(format!("Invalid server-id-status message: 0x{:02x}", other).into())
    };

    return Ok((known_server, *key));
}

/// Handles receiving and verifying the client's ID.
///
/// This asynchronous function waits to receive the client's unique ID
/// and checks if the client is already known to the server.
///
/// 1. Receives a 33-byte message from the client containing:
///    - Header byte (0x50) indicating "client-id"
///    - 32-byte client ID
/// 2. Validates the message header.
/// 3. Extracts the client ID and checks if this ID is present in the server's storage.
///
/// # Arguments
///
/// * `recv_stream` - A mutable reference to the QUIC receiving stream to read data from the client.
/// * `key` - A reference to the key material used for decryption.
///
/// # Returns
///
/// A `Result` containing a tuple with two values:
/// * `Ok((true, [u8; 32]))` if the client is known, received client ID.
/// * `Ok((false, [u8; 32]))` if the client is unknown, received client ID.
/// * `Err` if there is a communication error or an unexpected message is received.
async fn handle_client_id(recv_stream: &mut RecvStream, key: &[u8]) -> Result<(bool, [u8; 32])> {
    // receive client ID message
    let mut data= [0u8; 33];
    recv_stream.read_exact(&mut data).await?;
    // check message header
    if (data[0] & 0xF0) != 0x50 {
        return Err(format!("Invalid message header of uuid: {:02x}", data[0]).into());
    }

    let client_id: [u8; 32] = data[1..33].try_into()?;

    let known_client = is_client_id_known(&client_id, key, FILENAME_CLIENTS)?;
    
    return Ok((known_client, client_id));
}

/// Creates a unique server ID.
///
/// This function generates a 32-byte server ID by concatenating a 32-byte
/// random array and the current time in nanoseconds since the UNIX epoch.
/// The resulting data is then hashed using SHA-256 to produce the final ID.
/// This ensures that the ID is unique.
///
/// # Returns
///
/// A 32-byte array representing the server ID.
fn create_server_id() -> [u8; 32] {
    let rand_array: [u8; 32] = rand::random();
    let nanos = SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).unwrap().as_nanos();

    let id_raw = [rand_array.as_slice(), nanos.to_ne_bytes().as_slice()].concat();
    let id = Sha256::digest(id_raw);
    return id.into();
} 

/// Obtain the server ID associated with `key` from `filename`, creating and
/// persisting a new one if none is found.
///
/// # Arguments
/// * `key`: key material used to decrypt/encrypt the IDs.
/// * `filename`: path to the file storing encrypted IDs.
///
/// # Returns
/// * Ok([u8; 32]) with the existing or newly created server ID on success.
/// * Err: IO and cryptographic errors.
fn get_server_id(key: &[u8], filename: &str) -> Result<[u8; 32]> {
    if let Some(id) = load_id(key, filename)? {
        Ok(id)
    } else {
        let new_server_id = create_server_id();
        save_id(&new_server_id, &key, filename)?;
        Ok(new_server_id) 
    }
}

/// Check whether `client_id` is already known (stored) for the provided `key`
/// and `filename`.
///
/// # Arguments
///
/// * `client_id`: 32-byte client identifier to check for presence.
/// * `key`: key material used to decrypt the on-disk entries.
/// * `filename`: path to the file storing encrypted IDs.
///
/// # Returns
///
/// * `Ok(true)` if the client ID matches the stored ID.
/// * `Ok(false)` if not found or does not match.
/// * Err: IO and cryptographic errors.
fn is_client_id_known(client_id: &[u8; 32], key: &[u8], filename: &str) -> Result<bool> {
    if let Some(id) = load_id(key, filename)? {
        if id == *client_id {
            return Ok(true)
        }
    }
    Ok(false)
}

/// Generates a random pairing code.
///
/// The code consists of `CODE_SIZE` digits, with each digit being a random
/// number between 0 and 9.
///
/// # Arguments
///
/// * `pairing_code` - A mutable byte slice of size `CODE_SIZE` to be filled
///   with the generated code.
fn generate_pairing_code(pairing_code: &mut [u8; CODE_SIZE]) {
    let mut rng = rand::rng();
    for i in 0..CODE_SIZE {
        pairing_code[i] = rng.random_range(0..10);
    }
}

/// Checks if the received pairing code matches the generated one.
///
/// This function extracts the 5-digit pairing code from the received 3-byte
/// data array and compares it digit by digit with the generated code.
///
/// # Arguments
///
/// * `data` - A 3-byte array containing the pairing code received from the client.
/// * `code` - A byte slice of size `CODE_SIZE` representing the generated pairing code.
///
/// # Returns
///
/// * `true` if the received code matches the generated code.
/// * `false` otherwise.
fn check_received_code(data: [u8; 3], code: [u8; CODE_SIZE]) -> bool {
    // get pairing code from received data
    let mut recv_code = [0u8; CODE_SIZE];
    recv_code[0] =  data[2] & 0x0F;
    recv_code[1] = (data[2] & 0xF0) >> 4;
    recv_code[2] =  data[1] & 0x0F;
    recv_code[3] = (data[1] & 0xF0) >> 4;
    recv_code[4] =  data[0] & 0x0F;

    // check pairing code
    for i in 0..CODE_SIZE {
        if recv_code[i] != code[i] {
           return false;
        }
    }
    return true;
}

/// Save a 32‑byte ID to `filename` in encrypted form and maintain a rolling history.
///
/// - Encrypts `id` with `key` using `Crypto.encrypt`
/// - If the file exists: reads the next (ID_HISTORY - 1) encrypted entries into a buffer,
///   truncates the file and writes the new encrypted entry first, followed by the previous buffer.
///   This keeps the most recent entry at the beginning and drops the oldest entry when history is full.
/// - If the file does not exist: creates it, writes the encrypted `id` as the first entry and
///   fills the remaining (ID_HISTORY - 1) slots with encrypted random dummy IDs. The dummy entries
///   make it harder to distinguish which entry is the real one on disk.
/// - Asserts that the produced encrypted `id` is ENCRYPTED_ID_SIZE bytes long. Only then the data can 
///   be read as expected.
///
/// # Arguments
/// 
/// * `id`: A reference to a 32-byte array containing the ID to be saved.
/// * `key`: A reference to the key material used for encryption.
/// * `filename`: A string slice representing the path to the file where the ID will be saved.
///
/// # Errors
/// 
/// IO and cryptographic errors.
fn save_id(id: &[u8; 32], key: &[u8], filename: &str) -> Result<()> {
    let encrypted_ids = Crypto.encrypt(key, id)?;
    assert!(encrypted_ids.len() == ENCRYPTED_ID_SIZE);

    if fs::exists(filename)? {
        let mut file = fs::File::options().read(true).write(true).open(filename)?;

        let mut buf = [0u8; ENCRYPTED_ID_SIZE * (ID_HISTORY - 1)];
        file.read_exact(&mut buf)?;
        
        file.set_len(0)?;
        file.rewind()?;
        
        file.write_all(&encrypted_ids)?;
        file.write_all(&buf)?;
    } else {
        // file missing
        // create file, add first the encrypted ids, fill remaining data of history with random content
        let mut file = fs::File::create(filename)?;
        file.write_all(&encrypted_ids)?;
        for _ in 1..ID_HISTORY {
            let dummy_data: [u8; 32] = rand::random();
            let dummy_key: [u8; 32] = rand::random();
            let encrypted_data = Crypto.encrypt(&dummy_key, &dummy_data)?;
            file.write_all(&encrypted_data)?;
        }
    }
    Ok(())
}

/// Load the first decryptable 32‑byte ID from `filename`.
///
/// Reads up to ID_HISTORY entries from the file, each of ENCRYPTED_ID_SIZE bytes,
/// and attempts to decrypt each entry. Returns the first successfully decrypted plaintext.
/// If no entry decrypts successfully, returns Ok(None).
///
/// This function tolerates tampered/invalid entries: failed decryption simply results in skipping
/// that entry and continuing with the next entry.
/// 
/// # Arguments
///
/// * `key`: A reference to the key material used for decryption.
/// * `filename`: A string slice representing the path to the file from which the ID will be loaded.
///
/// # Returns
///
/// * `Ok(Some([u8; 32]))` if a valid ID is successfully decrypted.
/// * `Ok(None)` if no valid ID can be decrypted or file cannot be opened.
/// * `Err` if there are IO or parsing errors.
fn load_id(key: &[u8], filename: &str) -> Result<Option<[u8; 32]>> {
    if let Ok(mut file) = fs::File::open(filename) {
        for _ in 0..ID_HISTORY {
            let mut encrypted_ids = [0u8; ENCRYPTED_ID_SIZE];
            file.read_exact(&mut encrypted_ids)?;
            if let Some(id) = Crypto.decrypt(key, &encrypted_ids)? {
                if id.len() == 32 {
                    return Ok(Some(id.try_into().unwrap()));
                }
            }
        }
    }
    Ok(None)
}

/// Utility for encrypting and decrypting data.
///
/// Crypto is a tiny stateless helper that derives a one‑time encryption key
/// with HKDF and then encrypts/decrypts the payload with AES‑256‑GCM‑SIV. 
/// Each encryption produces a unique 12‑byte HKDF info and a random 12‑byte nonce; 
/// the returned buffer is laid out as: info(12) || nonce(12) || ciphertext. 
/// For a 32‑byte plaintext the resulting encrypted length is expected to be 
/// 72 bytes (12 + 12 + 32 + 16).
///
/// Security notes:
/// - The provided `key` is treated as the HKDF PRK; it must already contain
///   sufficient entropy (randomness). No additional salt is applied here.
/// - Derived key material (OKM) is zeroized to reduce exposure in memory.
struct Crypto;

impl Crypto {
    /// Encrypt `data` with a key derived from `key`.
    ///
    /// Steps:
    /// 1. Generate 12 random bytes for `info` (used as HKDF info).
    /// 2. Derive a 32‑byte one‑time key (OKM) from `key` and `info` using HKDF‑SHA256.
    /// 3. Create a random 12‑byte nonce for AES‑GCM‑SIV and encrypt `data`.
    /// 4. Return a concatenation of info || nonce || ciphertext.
    ///
    /// # Arguments
    /// 
    /// * `key`: secret key material (used as PRK for HKDF).
    /// * `data`: plaintext to encrypt.
    ///
    /// # Returns
    /// 
    /// * Ok(Vec<u8>) containing info(12) || nonce(12) || ciphertext.
    /// * Err on cryptographic or IO errors.    
    pub fn encrypt(&mut self, key: &[u8], data: &[u8]) -> Result<Vec<u8>> {
        let mut info = [0u8; 12];
        let mut okm = Zeroizing::new([0u8; 32]); // output key material

        // create random info for HKDF
        OsRng.fill_bytes(&mut info);
        
        // derive a different random key for each encryption
        self.key_derivation(key, &info, okm.as_mut())?;

        // encrypt data
        let cipher = Aes256GcmSiv::new_from_slice(okm.as_ref())?; // okm must not be longer than 32 bytes
        let nonce = Aes256GcmSiv::generate_nonce(OsRng);
        let encrypted_data = cipher.encrypt(&nonce, data)
            .map_err(|e| format!("Failed to encrypt data: {:?}", e))?;

        // combine info, nonce and data into a single vector, which is returned
        let combined_data = [&info[..], &nonce[..], &encrypted_data[..]].concat();
        return Ok(combined_data);
    }

    /// Decrypt data produced by `encrypt`.
    ///
    /// Parses the input as info(12) || nonce(12) || ciphertext, re-derives the
    /// same OKM with HKDF using `key` and `info`, and attempts AES‑GCM‑SIV
    /// decryption.
    ///
    /// # Arguments
    /// 
    /// * `key`: secret key material used as PRK for HKDF.
    /// * `combined_data`: buffer produced by `encrypt`.
    ///
    /// # Returns
    /// 
    /// * Ok(Some(plaintext)) if decryption and authentication succeed.
    /// * Ok(None) if authentication fails (ciphertext tampered or wrong key).
    /// * Err on invalid input lengths or other errors.
    pub fn decrypt(&mut self, key: &[u8], combined_data: &[u8]) -> Result<Option<Vec<u8>>> {
        // get info, nonce and encrypted data from input
        let info: [u8; 12] = combined_data[0..12].try_into()?;
        let nonce: [u8; 12] = combined_data[12..24].try_into()?; 
        let encrypted_data: Vec<u8> = combined_data[24..].try_into()?; 

        let mut okm = Zeroizing::new([0u8; 32]); // output key material
        self.key_derivation(key, &info, okm.as_mut())?;

        // decrypt data
        let cipher = Aes256GcmSiv::new_from_slice(okm.as_ref())?;
        let nonce_cipher = Nonce::from(nonce);
        let decrypted_data = match cipher.decrypt(&nonce_cipher, encrypted_data.as_slice()) {
            Ok(data) => Some(data),
            _ => None,
        };
        return Ok(decrypted_data);
    }

    /// Derive an OKM (output key material) using HKDF‑SHA256.
    /// HKDF: Hashed Message Authentication Code (HMAC)-based key derivation function
    ///
    /// Behavior:
    /// - Treats `key` as the PRK (pseudo random key). This means the caller is
    ///   expected to provide a key that already has high entropy.
    /// - Expands with the provided `info` into `okm` (the caller supplies the
    ///   output buffer and its desired length).
    ///
    /// Arguments:
    /// - `key`: PRK for HKDF.
    /// - `info`: info bytes for HKDF expand.
    /// - `okm`: mutable output buffer to fill with derived key material.
    ///
    /// Returns:
    /// - Ok(()) on success
    /// - Err on invalid PRK length or requested output length.
    fn key_derivation(&mut self, key: &[u8], info: &[u8], okm: &mut [u8]) -> Result<()> {
        let hk = Hkdf::<Sha256>::from_prk(key)
            .map_err(|e| format!("Invalid PRK length for HKDF: {:?}", e))?;
        hk.expand(&info, okm)
            .map_err(|e| format!("Invalid length for HKDF expand: {:?}", e))?;
        Ok(())
    }
}



// Unit tests

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_create_server_id() {
        let id  = create_server_id();
        assert_eq!(id.len(), 32);
        let sum: u32 = id.iter().map(|x| *x as u32).sum();
        assert_ne!(sum, 0);

        let id2  = create_server_id();
        assert_ne!(id, id2);
    }

    #[test]
    fn test_get_server_id() {
        let key: [u8; 32] = rand::random();
        let filename = "test_server.bin";

        // file must not exist, ignore error
        let _ = std::fs::remove_file(filename);

        let id_new = get_server_id(&key, filename).unwrap();
        let id_restored = get_server_id(&key, filename).unwrap();
        assert_eq!(id_new, id_restored);
        
        // id must not change on additional call
        let id_restored2 = get_server_id(&key, filename).unwrap();
        assert_eq!(id_restored, id_restored2);

        // a new id is random, therefore different to previous id
        std::fs::remove_file(filename).unwrap();
        let id_new2 = get_server_id(&key, filename).unwrap();
        assert_ne!(id_new, id_new2);

        std::fs::remove_file(filename).unwrap();
    }

    #[test]
    fn test_generate_pairing_code() {
        let mut code = [0xFu8; CODE_SIZE];
        generate_pairing_code(&mut code);
        assert!(!code.is_empty());
        assert!(code.into_iter().all(|x| x <= 9u8));

        let mut code2 = [0xFu8; CODE_SIZE];
        generate_pairing_code(&mut code2);
        assert!(code2.into_iter().all(|x| x <= 9u8));
        assert!(code != code2);
    }

    #[test]
    fn test_check_received_code() {
        let code = [1, 2, 3, 4, 5];
        let data = [0x65, 0x43, 0x21];
        assert!(check_received_code(data, code));

        let wrong_data = [0x65, 0x43, 0x22];
        assert!(!check_received_code(wrong_data, code));
    }

    #[test]
    fn test_crypto() {
        let key: [u8; 32] = rand::random();
        let data: [u8; 32] = rand::random();

        let encrypt_data = Crypto.encrypt(&key, &data).unwrap();
        let decrypt_data = Crypto.decrypt(&key, &encrypt_data).unwrap().unwrap();
        assert_eq!(data, decrypt_data[0..32]);

        // second encryption must produce different output pof the same input
        let encrypt_data2 = Crypto.encrypt(&key, &data).unwrap();
        // info part must be different
        assert_ne!(encrypt_data[0..12], encrypt_data2[0..12]);
        // nonce part must be different
        assert_ne!(encrypt_data[12..24], encrypt_data2[12..24]);
        // encrypted data part must be different
        assert_ne!(encrypt_data[24..], encrypt_data2[24..]);

        // corrupt 'info' (length: 12 byte)
        let mut bad_data = encrypt_data.clone();
        bad_data[0] ^= 0xFF;
        let decrypt_data = Crypto.decrypt(&key, &bad_data).unwrap();
        assert_eq!(decrypt_data, None);

        // corrupt 'nonce' (length: 12 byte)
        let mut bad_data = encrypt_data.clone();
        bad_data[15] ^= 0xFF;
        let decrypt_data = Crypto.decrypt(&key, &bad_data).unwrap();
        assert_eq!(decrypt_data, None);

        // corrupt 'data'
        let mut bad_data = encrypt_data.clone();
        bad_data[30] ^= 0xFF;
        let decrypt_data = Crypto.decrypt(&key, &bad_data).unwrap();
        assert_eq!(decrypt_data, None);

        // wrong key
        let mut bad_key = key.clone();
        bad_key[0] ^= 0xFF;
        let decrypt_data = Crypto.decrypt(&bad_key, &encrypt_data).unwrap();
        assert_eq!(decrypt_data, None);
    }


     #[test]
    fn test_save_and_load_id() {
        let filename = "test_id.bin";
        let id_org: [u8; 32] = rand::random();
        let key: [u8; 32] = rand::random();

        let _ = std::fs::remove_file(filename);

        // file created
        save_id(&id_org, &key, filename).unwrap();
        let loaded_id = load_id(&key, filename).unwrap();
        assert_ne!(loaded_id, None);
        let id = loaded_id.unwrap();
        assert_eq!(id_org, id);

        // add more IDs, so first entry is moved to the end of file
        for _ in 0..(ID_HISTORY-1) {
            let id_tmp: [u8; 32] = rand::random();
            let key_tmp: [u8; 32] = rand::random();
            save_id(&id_tmp, &key_tmp, filename).unwrap();
        }
        let loaded_id = load_id(&key, filename).unwrap();
        assert_ne!(loaded_id, None);
        let id = loaded_id.unwrap();
        assert_eq!(id_org, id);

        // move first entry out of file
        save_id(&[0u8; 32], &[0u8; 32], filename).unwrap();
        let loaded_id = load_id(&key, filename).unwrap();
        assert_eq!(loaded_id, None);

        // cleanup
        let _ = std::fs::remove_file(filename);
    }


     #[test]
    fn test_load_id_corrupted_file() {
        let filename = "test_id_cor.bin";
        let id: [u8; 32] = rand::random();
        let key: [u8; 32] = rand::random();

        let _ = std::fs::remove_file(filename);

        // file missing
        let ids = load_id(&key, filename).unwrap();
        assert_eq!(ids, None);

        // create correct file and check correct functioning
        save_id(&id, &key, filename).unwrap();
        let loaded_id = load_id(&key, filename).unwrap().unwrap();
        assert_eq!(loaded_id, id);

        // corrupt first byte in file
        let data = fs::read(filename).unwrap();
        let mut bad_data = data.clone();
        bad_data[0] ^= 0xFF;
        fs::write(filename, bad_data).unwrap();
        let loaded_id = load_id(&key, filename).unwrap();
        assert_eq!(loaded_id, None);

        // offset by 1 byte
        let mut bad_data = data.clone();
        bad_data.push(0);
        fs::write(filename, &bad_data[1..]).unwrap();
        let loaded_id = load_id(&key, filename).unwrap();
        assert_eq!(loaded_id, None);

        // file too short
        let data = fs::read(filename).unwrap();
        let mut bad_data = data.clone();
        bad_data = bad_data[0..ENCRYPTED_ID_SIZE].to_vec();
        fs::write(filename, bad_data).unwrap();
        assert!(load_id(&key, filename).is_err());

        // file empty
        fs::write(filename, &[]).unwrap();
        assert!(load_id(&key, filename).is_err());

        // cleanup
        let _ = std::fs::remove_file(filename);
    }
    
    #[test]
    fn test_save_id_no_write_permission_in_dir() { 
        let dir = "test_clients_no_write_dir";
        let filename = format!("{}/test_clients.bin", dir);
        let _ = fs::remove_dir_all(dir);
        fs::create_dir(dir).unwrap();
        let mut permissions = fs::metadata(dir).unwrap().permissions();

        permissions.set_readonly(true);
        fs::set_permissions(dir, permissions.clone()).unwrap();

        assert!(save_id(&[1u8; 32], &[2u8; 32], &filename).is_err());

        // cleanup
        permissions.set_readonly(false);
        fs::set_permissions(dir, permissions).unwrap();
        fs::remove_dir_all(dir).unwrap();
    }

    #[test]
    fn test_is_client_id_known() {
        let filename = "test_client_known.bin";
        let client_id: [u8; 32] = rand::random();
        let key: [u8; 32] = rand::random();
        let other_key: [u8; 32] = rand::random();

        let _ = std::fs::remove_file(filename);

        // 1. File doesn't exist
        assert_eq!(is_client_id_known(&client_id, &key, filename).unwrap(), false);

        // 2. File exists, ID is known
        save_id(&client_id, &key, filename).unwrap();
        assert_eq!(is_client_id_known(&client_id, &key, filename).unwrap(), true);

        // 3. Known ID, but wrong key
        assert_eq!(is_client_id_known(&client_id, &other_key, filename).unwrap(), false);

        // 4. Different ID, correct key
        let other_client_id: [u8; 32] = rand::random();
        assert_eq!(is_client_id_known(&other_client_id, &key, filename).unwrap(), false);

        // cleanup
        let _ = std::fs::remove_file(filename);
    }

}
