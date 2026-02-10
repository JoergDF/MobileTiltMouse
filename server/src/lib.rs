use quinn::{Endpoint, ServerConfig};
use quinn::crypto::rustls::QuicServerConfig;
use rustls::client::danger::HandshakeSignatureValid;
use rustls::server::danger::{ClientCertVerified, ClientCertVerifier};
use rustls::pki_types::{CertificateDer, PrivateKeyDer, UnixTime};
use rustls::{DigitallySignedStruct, SignatureScheme};
use p12_keystore::KeyStore;
use sha2::{Sha256, Sha512, Digest};
use rand_core::{SeedableRng, RngCore};
use rand_pcg::Pcg64Mcg;
use std::error::Error;
use std::net::SocketAddr;
use std::net::Ipv6Addr;
use std::sync::Arc;
use zeroconf::prelude::*;
use zeroconf::{MdnsService, ServiceType};
use hex::{FromHex, ToHex};

mod mouse_control;
mod pairing;

pub type Result<T> = std::result::Result<T, Box<dyn Error + Send + Sync + 'static>>;

const ALPN_QUIC_HTTP: &[&[u8]] = &[b"mobiletiltmouseproto"];
// embed the p12-file (containing certificate and private key) as byte array
const SERVER_CERT_P12: &[u8] = include_bytes!(concat!(env!("OUT_DIR"), "/server_cert.p12"));
// SHA256-hash of client certificate's DER-formatted file
const CLIENT_CERT_HASH: &str = include_str!(concat!(env!("OUT_DIR"), "/client_cert_hash.txt"));


/// Handles incoming QUIC connections and mouse control data.
///
/// This asynchronous function orchestrates the entire server functionality. It performs
/// the following key tasks:
///
/// 1.  **QUIC Server Setup**: Initializes and configures a QUIC server using the `quinn`
///     library. The server is bound to an unspecified IPv6 address on a system-chosen port.
///
/// 2.  **Service Discovery**: Registers a Bonjour/mDNS service of type `_mobiletiltmouse._udp`
///     to make the server discoverable on the local network.
///
/// 3.  **Connection Handling**: Spawns a primary task to continuously accept incoming QUIC
///     connections. For each accepted connection, it spawns a new concurrent task to
///     handle the session.
///
/// 4.  **Pairing and Data Transfer**: Within each connection task, it first performs a
///     pairing handshake using a bidirectional stream. If pairing is successful, it
///     transitions to uni-directional to receiving 3-byte mouse control packets.
///
/// 5.  **Mouse Control**: Received mouse data is sent to the provided `mouse` 
///     implementation (from the `enigo` crate) to execute the corresponding mouse actions 
///     (e.g., move, click).
///
/// # Arguments
/// * `mouse` - A mutable reference to an object implementing the `enigo::Mouse` trait,
///    which will be used to execute mouse control actions.
/// * `test` - indicate whether in integration test mode or not
///
/// # Returns
/// Returns `Ok(())` upon successful initialization. The server itself runs indefinitely.
/// 
/// # Errors
/// 
/// An `Err` is returned if there is a failure during the initial setup of the server
/// or the mDNS service.
#[tokio::main]
pub async fn connection_handler(test: bool) -> Result<()> {
    //initialize_logger("trace");

    // set up quic server
    let server_addr = SocketAddr::new(std::net::IpAddr::V6(Ipv6Addr::UNSPECIFIED), 0); 
    
    let server_config = configure_server()?;
    let endpoint = Endpoint::server(server_config, server_addr)?;
    println!("My address {:?}", endpoint.local_addr()?);

    // bonjour
    let service_type = ServiceType::new("mobiletiltmouse", "udp")?;
    let mut service = MdnsService::new(service_type, endpoint.local_addr()?.port()); 
    service.register()?;

    while let Some(conn) = endpoint.accept().await { 
        println!("Endpoint accepted");

        tokio::spawn(async move {
            // sometimes connection establishment (of iOS client) cannot be completed and waits here till timeout 
            // (leads to error, which is ignored), but a second attempt is done which then works (in another thread)
            let connection = conn.await;
            if let Ok(connection) = connection {
                println!("Connection accepted from {}", connection.remote_address());
                
                while let Ok((mut send_stream, mut recv_stream)) = connection.accept_bi().await {
                    println!("Incoming stream accepted");

                    if let Err(e) = pairing::pairing(&mut recv_stream, &mut send_stream, test).await {
                        eprintln!("Pairing failed: {:?}", e); 
                        break;
                    }

                    // send-direction not needed anymore when pairing is done
                    send_stream.finish().unwrap();

                    let mut mouse_ctrl = mouse_control::MouseControl::default();
                    let mut data= [0u8; 3];
                    while let Ok(()) = recv_stream.read_exact(&mut data).await {
                        mouse_ctrl.mouse_action(data);             
                    }
                }
            } else {
                eprintln!("Failed to establish connection: {:?}", connection.err());
            }
        });
    }

    Ok(())
}


/// A custom client certificate verifier for the QUIC server.
///
/// `ClientCert` implements the [`rustls::server::danger::ClientCertVerifier`] trait,
/// allowing the server to authenticate clients using a specific certificate fingerprint.
/// This is used to restrict access so that only clients with the correct certificate can connect.
///
/// The verification logic checks the SHA-256 hash of the presented client certificate
/// against a hardcoded value. If the hash matches, the client is accepted; otherwise,
/// the connection is rejected. 
/// 
/// The hash is derived from the DER format of the client certificate by the command:
/// `shasum -a 256 client_cert.der`
/// 
/// # Usage
/// Used internally in [`configure_server`] to enforce client authentication.
#[derive(Debug)]
struct ClientCert;

#[allow(unused_variables)]
impl ClientCertVerifier for ClientCert {
    fn root_hint_subjects(&self) -> &[rustls::DistinguishedName] 
    {
        // not required for this project
        &[]
    }

    fn verify_client_cert(
        &self,
        end_entity: &CertificateDer<'_>,
        intermediates: &[CertificateDer<'_>],
        now: UnixTime,
    ) -> std::result::Result<ClientCertVerified, rustls::Error>
    {
        let ref_hash = <[u8; 32]>::from_hex(CLIENT_CERT_HASH).unwrap();
        let hash = Sha256::digest(end_entity);

        if hash[..] == ref_hash {
            println!("Client certificate verified successfully");
            Ok(ClientCertVerified::assertion())
        } else {
            Err(rustls::Error::General("Client certificate verification failed".into()))
        }
    }
    
    fn verify_tls12_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> std::result::Result<HandshakeSignatureValid, rustls::Error>
    {
        // ignored
        Ok(HandshakeSignatureValid::assertion())
    }
    
    fn verify_tls13_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> std::result::Result<HandshakeSignatureValid, rustls::Error>
    {
        // ignored
        Ok(HandshakeSignatureValid::assertion())
    }
    
    fn supported_verify_schemes(&self) -> Vec<SignatureScheme>
    {
        // a signature scheme supported by the client need to be sent, 
        // otherwise the client will not send its certificate
        vec![SignatureScheme::RSA_PSS_SHA256]
    }
}


/// Configures the QUIC server with TLS certificates, client authentication, and transport settings.
///
/// This function prepares a [`quinn::ServerConfig`] for use with a QUIC server. It:
/// - Loads the server certificate and private key from embedded pkcs#12 file.
/// - Sets up a custom client certificate verifier (`ClientCert`) to restrict access to clients
///   presenting a certificate with a specific SHA-256 fingerprint.
/// - Configures ALPN protocols for QUIC negotiation.
/// - Limits the number of concurrent incoming unidirectional streams to 1 per connection.
/// - Enables keep-alive to maintain connections.
///
/// # Returns
/// * `Ok(ServerConfig)` if configuration succeeds.
/// * `Err` if certificate, key, or transport configuration fails.
///
/// # Errors
/// Returns an error if:
/// - The certificate or key cannot be parsed.
/// - The QUIC or TLS configuration fails.
fn configure_server() -> Result<ServerConfig> {
    let client_cert_hash = <[u8; 32]>::from_hex(CLIENT_CERT_HASH).unwrap();
    // concat 8 bytes to an u64-integer
    let seed_state: u64 = client_cert_hash[..8].iter().fold(0, |acc, elem| acc * 256 + u64::from(*elem));
    let mut rng = Pcg64Mcg::seed_from_u64(seed_state);
    let mut rng_data = [0u8; 32];
	rng.fill_bytes(&mut rng_data);
	let mut hasher = Sha512::new();
	hasher.update(client_cert_hash);
	hasher.update(rng_data);
	let dat = hasher.finalize();
	let dat_str = dat.encode_hex::<String>();

    let keystore = KeyStore::from_pkcs12(SERVER_CERT_P12, &dat_str)?;
    let key_chain = keystore.private_key_chain().unwrap().1;
    let cert_der = CertificateDer::from(key_chain.chain()[0].as_der()).into_owned();
    let priv_key_der = PrivateKeyDer::try_from(key_chain.key())?.clone_key();

    let mut server_crypto = rustls::ServerConfig::builder()
        .with_client_cert_verifier(Arc::new(ClientCert))
        .with_single_cert(vec![cert_der.clone()], priv_key_der)?;
    server_crypto.alpn_protocols = ALPN_QUIC_HTTP.iter().map(|&x| x.into()).collect();

    let mut server_config =
        quinn::ServerConfig::with_crypto(Arc::new(QuicServerConfig::try_from(server_crypto)?));

    let transport_config = Arc::get_mut(&mut server_config.transport).unwrap();
    // Only 1 incoming unidirectional stream is allowed. 
    // But there could be multiple connections with 1 stream each
    //transport_config.max_concurrent_uni_streams(1_u8.into());
    // enable keep alive
    transport_config.keep_alive_interval(Some(std::time::Duration::from_secs(20)));
    
    Ok(server_config)
}


/// Initializes a file-based logger for tokyo/quinn library.
///
/// # Arguments
/// * `endpoint` - String that will be used as the log file name (with .txt extension)
pub fn initialize_logger(endpoint: &str) {
    use std::sync::Once;

    static TRACING: Once = Once::new();

    TRACING.call_once(|| {
        let file_appender = tracing_appender::rolling::never(".", format!("{endpoint}.txt"));

        tracing_subscriber::fmt()
            .with_max_level(tracing::Level::DEBUG)
            .with_ansi(false)
            .with_writer(file_appender)
            .init();
    });
}