use quinn::{Endpoint, ServerConfig};
use quinn::crypto::rustls::QuicServerConfig;
use rustls::pki_types::{CertificateDer, PrivateKeyDer};
use std::error::Error;
use std::net::SocketAddr;
use std::net::Ipv6Addr;
//use std::net::Ipv4Addr;
use std::sync::Arc;
use zeroconf::prelude::*;
use zeroconf::{MdnsService, ServiceType};
use enigo::Mouse;


mod mouse_control;
mod auth_client;


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
    //let server_addr = SocketAddr::new(std::net::IpAddr::V4(Ipv4Addr::new(192,168,178,34)), 20000); 
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

            // reset client authentication, required after connection has been lost, 
            // otherwise unauthorized clients could connect without authentication
            mpsc_tx.send([0xF0, 0x00, 0x00]).await.unwrap();

            tokio::spawn(async move {
                // sometimes connection establishment (of iOS client) cannot be completed and waits here till timeout 
                // (leads to error, which is ignored), but a second attempt is done which then works (in another thread)
                let connection = conn.await;

                println!("Connection accepted from {}", connection.clone().unwrap().remote_address());
                
                while let Ok(mut recv) = connection.clone().unwrap().accept_uni().await {
                    println!("Incoming uni-directional stream accepted");
                    let mut data= [0u8; 3];
                    while let Ok(()) = recv.read_exact(&mut data).await {
                        // forward received data to mpsc channel
                        mpsc_tx.send(data).await.unwrap();
                    }
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


/// Configures the QUIC server with TLS certificates and transport settings.
fn configure_server() -> Result<ServerConfig, Box<dyn Error + Send + Sync + 'static>> {
    let cert_der = CertificateDer::from(CERT);
    let priv_key = PrivateKeyDer::try_from(KEY)?;

    let mut server_crypto = rustls::ServerConfig::builder()
        .with_no_client_auth()
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
    // connection timeout (disable: None, default: 30 seconds)
    //transport_config.max_idle_timeout( None );
    
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