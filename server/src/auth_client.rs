use sha2::{Sha256, Sha512, Digest};
use hmac::{Hmac, Mac};

const KEY: [u8; 32] = [
    0x24, 0xe3, 0x82, 0x41, 0x55, 0xc6, 0x1d, 0xdc,
    0x12, 0xe1, 0x8a, 0xc2, 0x02, 0x9f, 0x66, 0x5f,
    0x24, 0x19, 0xe8, 0x9d, 0x5e, 0x17, 0x6d, 0x55,
    0x07, 0x74, 0x37, 0x0d, 0x0e, 0x5f, 0xb6, 0x58
];

/// Handles client authentication using HMAC-SHA256 with a SHA512-hashed key.
///
/// The authentication process works as follows:
/// 1. Client sends a 32-byte message in 2-byte chunks
/// 2. Client sends its calculated 32-byte HMAC in 2-byte chunks
/// 3. Server verifies the HMAC using the shared key
///
/// # Authentication Protocol
///
/// ```text
/// Client                     Server
/// ------                     ------
/// Generate Message
/// Calculate HMAC
/// Send Message (0xA) ------> Store Message
/// Send HMAC (0xB)    ------> Verify HMAC
///                            Set verified=true
/// ```
///
/// # Fields
///
/// * `message` - Buffer for storing the received 32-byte message
/// * `message_idx` - Current position in message buffer
/// * `hmac` - Buffer for storing the received 32-byte HMAC
/// * `hmac_idx` - Current position in HMAC buffer
/// * `verified` - Authentication state (true = authenticated)
///
#[derive(Default)]
pub struct ClientAuthentication {
    message: [u8; 32],
    message_idx: usize,
    hmac: [u8; 32],
    hmac_idx: usize,
    pub verified: bool,
}


impl ClientAuthentication {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn reset(&mut self) {
        println!("Reset client authentication");
        self.verified = false;
        self.message_idx = 0;
        self.hmac_idx = 0;
    }

    /// Adds a 2-byte chunk to the message buffer for HMAC verification.
    /// The buffer wraps around when full.
    ///
    /// # Arguments
    /// * `data` - 2-byte message chunk
    pub fn add_message(&mut self, data: [u8; 2]) {
        for d in data {
            self.message[self.message_idx] = d;
            self.message_idx = (self.message_idx + 1) % self.message.len()
        }
    }

    /// Adds a 2-byte HMAC chunk and triggers verification when complete.
    /// When the buffer is full, automatically verifies the HMAC against the stored message.
    ///
    /// # Arguments
    /// * `data` - 2-byte HMAC chunk
    ///
    /// # Panics
    /// Panics with "Client authentication failed" if HMAC verification fails
    pub fn add_hmac(&mut self, data: [u8; 2]) {
        for d in data {
            self.hmac[self.hmac_idx] = d;
            self.hmac_idx += 1;
        }

        if self.hmac_idx >= self.hmac.len() {
            self.hmac_idx = 0;
            self.verify_hmac();
        }
    }

    /// Verifies the received HMAC against the stored message using HMAC-SHA256.
    ///
    /// The verification process:
    /// 1. Hash the shared key using SHA-512
    /// 2. Create HMAC-SHA256 with the hashed key
    /// 3. Update HMAC with stored message
    /// 4. Verify received HMAC matches calculated HMAC
    ///
    /// # Panics
    /// Panics with "Client authentication failed" if HMAC verification fails
    fn verify_hmac(&mut self) {
        let mut hasher = Sha512::new();
        hasher.update(&KEY);
        let key = hasher.finalize();
        
        type HmacSha256 = Hmac<Sha256>;
        let mut mac = HmacSha256::new_from_slice(&key).unwrap();
        mac.update(&self.message);
        // failed verification: raise exception and end/exit executable
        mac.verify_slice(&self.hmac).expect("Client authentication failed");

        self.verified = true;
        println!("Client authentication verified")
    }
}



#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reset_test() {
        let mut ca = ClientAuthentication::new();
        ca.verified = true;
        ca.message_idx = 10;
        ca.hmac_idx = 5;
        ca.reset();
        assert_eq!(ca.verified, false);
        assert_eq!(ca.message_idx, 0);
        assert_eq!(ca.hmac_idx, 0);
    }

    #[test]
    fn add_message_test() {
        let mut ca = ClientAuthentication::new();

        let data = [0x01, 0x02];
        ca.add_message(data);
        assert!(ca.message[0..2].eq(&data[0..2]));
        assert_eq!(ca.message_idx, 2);
    
        let data = [0x03, 0x04];
        ca.add_message(data);
        assert!(ca.message[2..4].eq(&data[0..2]));
        assert_eq!(ca.message_idx, 4);

        // test wrap around
        for _ in 0..16 {
            let data = [5, 5];
            ca.add_message(data);
        }
        assert!(ca.message[0..4].eq(&[5; 4]));
    }

    #[test]
    fn add_hmac_test() {
        let mut ca = ClientAuthentication::new();

        let data = [0x01, 0x02];
        ca.add_hmac(data);
        assert!(ca.hmac[0..2].eq(&data[0..2]));
        assert_eq!(ca.hmac_idx, 2);

        let data = [0x03, 0x04];
        ca.add_hmac(data);
        assert!(ca.hmac[2..4].eq(&data[0..2]));
        assert_eq!(ca.hmac_idx, 4);
    }

    #[test]
    #[should_panic(expected = "Client authentication failed")]
    fn hmac_failure_test() {
        let mut ca = ClientAuthentication::new();

        for _ in 0..16 {
            ca.add_hmac([5, 5]);
        }
    }

    #[test]
    fn hmac_test() {
        let mut ca = ClientAuthentication::new();

        let msg = [0xA5; 32];

        let mut hasher = Sha512::new();
        hasher.update(&KEY);
        let key = hasher.finalize();
        
        type HmacSha256 = Hmac<Sha256>;
        let mut mac = HmacSha256::new_from_slice(&key).unwrap();
        mac.update(&msg);
        let hmac_input = mac.finalize().into_bytes();


        for i in (0..32).step_by(2) {
            ca.add_message([msg[i], msg[i + 1]]);
        }

        for i in (0..32).step_by(2) {
            ca.add_hmac([hmac_input[i], hmac_input[i + 1]]);
        }

        assert_eq!(ca.verified, true);
    }
}