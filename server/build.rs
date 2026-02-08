use rcgen::{CertifiedKey, RsaKeySize, generate_simple_self_signed, CertificateParams, Certificate, KeyPair, PKCS_RSA_SHA256};
use std::path::{Path, PathBuf};
use std::fs;
use p12_keystore::{PrivateKeyChain, KeyStore, KeyStoreEntry, EncryptionAlgorithm::PbeWithShaAnd3KeyTripleDesCbc, MacAlgorithm::HmacSha1};
use sha2::{Digest, Sha256, Sha512};
use rand_core::{SeedableRng, RngCore};
use rand_pcg::Pcg64Mcg;
use rand;

// View print output: use option -vv
// e.g. cargo -vv b 

const SERVER_P12_FILE: &str  = "server_cert.p12";
const SERVER_HASH_FILE: &str = "server_cert_hash.txt";
const CLIENT_P12_FILE: &str  = "client_cert.p12";
const CLIENT_HASH_FILE: &str = "client_cert_hash.txt";
	

fn main() {
	// directory where certificate files are saved
    let out_dir = std::env::var_os("OUT_DIR").unwrap();
	let out_path = Path::new(&out_dir);

	let server_p12_outfile  = out_path.join(SERVER_P12_FILE);
	let server_hash_outfile = out_path.join(SERVER_HASH_FILE);
	let client_p12_outfile  = out_path.join(CLIENT_P12_FILE);
	let client_hash_outfile = out_path.join(CLIENT_HASH_FILE);

	println!("========");
	println!("OUT_DIR:  {:?}", out_dir);
	println!("========");

	// create server's self-signed certificate and key
    let CertifiedKey { cert: server_cert, signing_key: server_key } = generate_simple_self_signed(
		["localhost".to_string()]
	).unwrap();

	// create client's self-signed certificate and key
	// Use RSA, as EC fails on clients for client authentication
	let params = CertificateParams::new(["localhost".to_string()]).unwrap();
	let client_key = KeyPair::generate_rsa_for(&PKCS_RSA_SHA256, RsaKeySize::_4096).unwrap();
	let client_cert = params.self_signed(&client_key).unwrap();

	// create sha256 hashes of DER certificates
	let server_cert_hash = create_cert_hash(&server_hash_outfile, server_cert.der());
	let client_cert_hash = create_cert_hash(&client_hash_outfile, client_cert.der());

	// create keystores of certificates and keys
	create_server_keystore(&server_p12_outfile, &server_cert, &server_key, &client_cert_hash);
	create_client_keystore(&client_p12_outfile, &client_cert, &client_key, &server_cert_hash);

	// copy files to AppiOS and AppAndroid
	copy_file_to_clients(out_path,SERVER_HASH_FILE);
	copy_file_to_clients(out_path,CLIENT_P12_FILE);

	// compile and run this file, if it changed, but not if other project files changed
	println!("cargo::rerun-if-changed=build.rs");
}


/// Calculates sha256 hash of a DER certificate, converts the hash to a hex string and writes that to file.
/// 
/// # Arguments
/// 
/// * `hash_outfile` - path with file where hash should be saved
/// * `cert_der`     - certificate in DER format
///
/// # Return
/// 
/// *  hash as array
fn create_cert_hash(hash_outfile: &Path, cert_der: &[u8]) -> [u8; 32] {
	let cert_der_hash = Sha256::digest(cert_der);
	let hash_hexstring = cert_der_hash.into_iter().map(|i| format!("{:02x}", i)).collect::<String>();
	fs::write(hash_outfile, hash_hexstring).unwrap();
	cert_der_hash.into()
}

/// Adds an entry to the provided p12-keystore
/// 
/// # Arguments
/// 
/// * `key_store` - p12 keystore
/// * `cert` - certificate to be added
/// * `key` - key of certificate to be added
/// 
/// # Return
/// 
/// * keystore with added entry
fn add_keystore_entry(mut key_store: KeyStore, cert: &Certificate, key: &KeyPair) -> KeyStore {
	let ks_cert = p12_keystore::Certificate::from_der(cert.der()).unwrap();
	let ks_key = key.serialized_der();
	let local_key_id: [u8; 20] = rand::random();
	let key_chain = PrivateKeyChain::new(ks_key, local_key_id, [ks_cert]);
	key_store.add_entry("entry", KeyStoreEntry::PrivateKeyChain(key_chain));
	key_store
}

/// Creates an encrypted p12 keystore of the server certificate and its key 
/// and writes it to a file.
/// 
/// # Arguments
/// 
/// * `p12_outfile` - path with file where p12 keystore should be saved
/// * `cert` - certificate generated with rcgen
/// * `key` - key of certificate 
fn create_server_keystore(p12_outfile: &Path, cert: &Certificate, key: &KeyPair, client_cert_hash: &[u8; 32]) {
	let mut key_store = KeyStore::new();
	key_store = add_keystore_entry(key_store, cert, key);

	// seed state: concat 8 bytes to an u64-integer
	let seed_state: u64 = client_cert_hash[..8].iter().fold(0, |acc, elem| acc * 256 + u64::from(*elem));
	let mut rng = Pcg64Mcg::seed_from_u64(seed_state);
	let mut rng_data = [0u8; 32];
	rng.fill_bytes(&mut rng_data);
	// hash server's DER-certificate-sha256 hash and pseudo random array
	let mut hasher = Sha512::new();
	hasher.update(&client_cert_hash);
	hasher.update(rng_data);
	let dat = hasher.finalize();
	let dat_str = dat.into_iter().map(|i| format!("{:02x}", i)).collect::<String>();

	let p12 = key_store.writer(&dat_str).write().unwrap();
	fs::write(&p12_outfile, p12).unwrap();
}

/// Creates an encrypted p12 keystore of the client certificate and its key 
/// and writes it to a file.
/// 
/// Older Android versions (like Android 10/API 29) do not support current default algorithms for encryption 
/// (which is SHA256/AES256, 10000 iterations), therefore legacy mode is required (using SHA1, 3KeyTripleDesCbc 
/// or 40BitRc4Cbc, 2048 iterations).
/// 
/// # Arguments
/// 
/// * `p12_outfile` - path with file where p12 keystore should be saved
/// * `cert` - certificate generated with rcgen
/// * `key` - key of certificate 
fn create_client_keystore(p12_outfile: &Path, cert: &Certificate, key: &KeyPair, server_cert_hash: &[u8; 32]) {
	let mut key_store = KeyStore::new();
	key_store = add_keystore_entry(key_store, cert, key);

	let random_data: Vec<u8> = (0..32).map(|i| {
		let mut hasher = Sha256::new();
		hasher.update(server_cert_hash);
		hasher.update(&[i]);
		hasher.finalize()[0]
	}).collect();
	let mut hasher = Sha512::new();
	hasher.update(server_cert_hash);
	hasher.update(random_data);
	let dat = hasher.finalize();
	let dat_str = dat.into_iter().map(|i| format!("{:02x}", i)).collect::<String>();

	let p12 = key_store
						.writer(&dat_str)
						.encryption_algorithm(PbeWithShaAnd3KeyTripleDesCbc)
						.encryption_iterations(2048)
						.mac_algorithm(HmacSha1)
						.mac_iterations(2048)
						.write()
						.unwrap();
	fs::write(&p12_outfile, p12).unwrap();
}

/// Copy certificate related files relevant for iOS and Android to the apps' assets directories.
/// 
/// # Arguments
/// 
/// * `out_path` - path of out directory (OUT_DIR)
/// * `filename` - name of file to be copied
fn copy_file_to_clients(out_path: &Path, filename: &str) {
	let source_outfile = out_path.join(filename);
	let working_dir = std::env::var_os("CARGO_MANIFEST_DIR").unwrap();
	let project_root = Path::new(&working_dir).join("..");

	// copy file to AppiOS, the p12 file is in a different directory than the hash txt file
	let target_ios_path_file = if filename.ends_with("p12") {
		project_root.join(PathBuf::from_iter(["AppiOS", "MobileTiltMouse", "Assets.xcassets", "ClientCertificate.dataset", filename]))
	} else if filename.ends_with("txt") {
		project_root.join(PathBuf::from_iter(["AppiOS", "MobileTiltMouse", "Assets.xcassets", "ServerCertHash.dataset", filename]))
	} else {
		panic!("Error: Invalid filename {} to be copied", filename)
	};
	fs::copy(source_outfile.as_path(), target_ios_path_file.as_path()).unwrap();

	// copy file to AppAndroid (p12 and hash file are in the same directory)
	let target_android_path_file = project_root.join(PathBuf::from_iter(["AppAndroid", "app", "src", "main", "assets", filename]));
	fs::copy(source_outfile.as_path(), target_android_path_file.as_path()).unwrap();
}