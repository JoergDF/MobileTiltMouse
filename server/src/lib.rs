use quinn::{Endpoint, ServerConfig};
use quinn::crypto::rustls::QuicServerConfig;
use rustls::client::danger::HandshakeSignatureValid;
use rustls::server::danger::{ClientCertVerified, ClientCertVerifier};
use rustls::pki_types::{CertificateDer, PrivateKeyDer, UnixTime};
use rustls::{DigitallySignedStruct, SignatureScheme};
use sha2::{Sha256, Digest};
use hex_literal::hex;
use std::error::Error;
use std::net::SocketAddr;
use std::net::Ipv6Addr;
use std::sync::Arc;
use zeroconf::prelude::*;
use zeroconf::{MdnsService, ServiceType};
use enigo::Mouse;


mod mouse_control;


const ALPN_QUIC_HTTP: &[&[u8]] = &[b"mobiletiltmouseproto"];
// embed the certificate and key files as byte arrays
const CERT: &[u8] = include_bytes!("cert.der");
const KEY: &[u8]  = include_bytes!("key.der");


/// Starts the QUIC server and handles incoming mouse control data.
///
/// This asynchronous function sets up a QUIC server using [`quinn`](https://docs.rs/quinn)
/// and configures TLS with the provided certificate and key files. It also registers a Bonjour/mDNS service using
/// [`zeroconf`](https://docs.rs/zeroconf) for automatic discovery.
///
/// The function spawns asynchronous tasks to accept incoming connections and associated unidirectional streams.
/// Incoming 3-byte mouse control packets received on these streams are forwarded via a Tokio MPSC channel to be processed
/// by the provided [`enigo::Mouse`](https://docs.rs/enigo) implementation.
/// 
/// # Arguments
/// * `mouse` - A mutable reference to an implementation of the [`enigo::Mouse`](https://docs.rs/enigo) trait used for executing mouse actions.
///
/// # Returns
/// Returns a `Result` that is:
/// - `Ok(())` if the server completes its execution (normally runs indefinitely until an error occurs).
/// - `Err` if an error occurs during setup (e.g., TLS configuration, socket binding, mDNS registration)
///   or during connection handling.
/// 
/// # Errors
/// This function may return errors due to:
/// - Failures in reading TLS certificate or key files.
/// - Issues during QUIC server configuration.
/// - Failures in registering the Bonjour service.
/// - Network or I/O errors when handling connections or streams.
/// 
#[tokio::main]
pub async fn connection_handler(mouse: &mut impl Mouse) -> Result<(), Box<dyn Error + Send + Sync + 'static>> {
    //initialize_logger("trace");

    // mouse handler
    let mut mouse_ctrl = mouse_control::MouseControl::new();

    // set up quic server
    let server_addr = SocketAddr::new(std::net::IpAddr::V6(Ipv6Addr::UNSPECIFIED), 0); 
    let server_config = configure_server()?;
    let endpoint = Endpoint::server(server_config, server_addr)?;
    println!("My address {:?}", endpoint.local_addr().unwrap());

    // bonjour
    let service_type = ServiceType::new("mobiletiltmouse", "udp")?;
    let mut service = MdnsService::new(service_type, endpoint.local_addr()?.port()); 
    service.register()?;


    let (mpsc_tx, mut mpsc_rx) = tokio::sync::mpsc::channel(1); 

    // the while loop needs to run in its own thread, so a failed connection establishment can be repeated, 
    // while the rx-channel-loop can run outside of this loop, not blocking it
    tokio::spawn(async move {
        while let Some(conn) = endpoint.accept().await { 
            let mpsc_tx = mpsc_tx.clone();
            println!("Endpoint accepted");

            tokio::spawn(async move {
                // sometimes connection establishment (of iOS client) cannot be completed and waits here till timeout 
                // (leads to error, which is ignored), but a second attempt is done which then works (in another thread)
                let connection = conn.await;
                if let Ok(connection) = connection {
                    println!("Connection accepted from {}", connection.remote_address());
                    
                    while let Ok(mut recv) = connection.accept_uni().await {
                        println!("Incoming uni-directional stream accepted");
                        let mut data= [0u8; 3];
                        while let Ok(()) = recv.read_exact(&mut data).await {
                            // forward received data to mpsc channel
                            mpsc_tx.send(data).await.unwrap();
                        }
                    }
                } else {
                    eprintln!("Failed to establish connection: {:?}", connection.err());
                }
            });
        }
    });

    // mouse pointer in library enigo does not implement Send, therefore there is a compiler error,
    // if there are MouseControl parts before and within await-loop 
    // hence channels are used to make things work
    while let Some(data) = mpsc_rx.recv().await {   
        mouse_ctrl.mouse_action(data, mouse);
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
///
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
    ) -> Result<ClientCertVerified, rustls::Error>
    {
        let hash = Sha256::digest(end_entity);
        if hash[..] == hex!("019641942271cf481efdb9416b0a06e5ae42f1d8d28dd30ecf6946149fdbc002") {
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
    ) -> Result<HandshakeSignatureValid, rustls::Error>
    {
        // ignored
        Ok(HandshakeSignatureValid::assertion())
    }
    
    fn verify_tls13_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error>
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
/// - Loads the server certificate and private key from embedded DER files.
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
///
fn configure_server() -> Result<ServerConfig, Box<dyn Error + Send + Sync + 'static>> {
    let cert_der = CertificateDer::from(CERT);
    let priv_key = PrivateKeyDer::try_from(KEY)?;

    let mut server_crypto = rustls::ServerConfig::builder()
        .with_client_cert_verifier(Arc::new(ClientCert))
        .with_single_cert(vec![cert_der.clone()], priv_key.into())?;
    server_crypto.alpn_protocols = ALPN_QUIC_HTTP.iter().map(|&x| x.into()).collect();

    let mut server_config =
        quinn::ServerConfig::with_crypto(Arc::new(QuicServerConfig::try_from(server_crypto)?));

    let transport_config = Arc::get_mut(&mut server_config.transport).unwrap();
    // Only 1 incoming unidirectional stream is allowed. 
    // But there could be multiple connections with 1 stream each
    transport_config.max_concurrent_uni_streams(1_u8.into());
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