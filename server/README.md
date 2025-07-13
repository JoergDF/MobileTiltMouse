# Mobile Mouse

A server application that allows you to control your computer's mouse using a mobile device with the apps for iOS [AppiOS](../AppiOS) or Android [AppAndroid](../AppAndroid).

Tested on MacOS (Sequoia, 15). But probably also works on Windows and Linux.

## Features

- Rust
- Mouse pointer is bound to the monitor in which the server is started.


## Installation

1. Clone the repository:
   ```
   git clone https://github.com/JoergDF/MobileTiltMouse.git
   cd MobileTiltMouse/server
   ```

2. Build and run the application:
   ```
   cargo run --release
   ```

## Usage

1. Start the server in a terminal on the computer, on which you want to control the mouse.
2. Connect from your mobile device using a compatible client.
3. Control your computer's mouse remotely.
4. When finished, exit server with Ctrl-C.

On MacOS you need to allow the terminal to control your computer in order to move the mouse pointer.

You might need to allow this app to accept incoming connections in your computer's firewall.


## Tests

- **Unit tests:**  
  To run unit tests:
  ```
  cargo test
  ```

- **Integration tests:**  
  Integration tests are available for both iOS and Android. These tests launch an iOS simulator or Android emulator on the same computer, where a test case of the apps (simulating a button press) interacts with the server. The server then verifies if the correct signal is received.

  **Prerequisite:**  
  The appropriate development environment (Xcode for iOS, Android Studio for Android) must be installed on the test machine.

  Integration tests are not run by default with other tests and must be explicitly invoked.

  - To run both iOS and Android integration tests:
    ```
    cargo test --test integration_tests -- --ignored --test-threads=1
    ```

  - To run only the iOS integration test:
    ```
    cargo test --test integration_tests -- test_ios_receive_button_clicks --ignored
    ```

  - To run only the Android integration test:
    ```
    cargo test --test integration_tests -- test_android_receive_button_clicks --ignored
    ```

## Protocol

The QUIC connection is configured as a unidirectional stream from client to server.

### Establishment of Connection

1. Server advertises itself via *Bonjour* as `_mobiletiltmouse._udp`. When client finds that name in the local network, it gets IP address and port number of the server.
2. Client establishes a QUIC connection to the server (ALPN: `mobiletiltmouseproto`) and verifies the server's certificate.
3. Client authenticates itself to the server.


### Data Packets of Mouse Control

Each packet is 3 bytes with the following format:
- Byte 2: Header (4 bits) | Data (4 bits)
- Byte 1: Data
- Byte 0: Data


|*Client authenticated*       | Header | Payload ||
|:-                           |-:|-:|-:|
|*bit position*               |*23...20*|*19...10*|*9...0*|
|move                         |0x0|$\Delta$ y|$\Delta$ x|
|scroll                       |0x1|$\Delta$ y|$\Delta$ x|
|left button press            |0x2||0|
|left button release          |0x2||1|
|middle button press          |0x2||2|
|middle button release        |0x2||3|
|right button press           |0x2||4|
|right button release         |0x2||5|
|client authentication reset  |0xF||-|

|*Client not authenticated*     | Header | Payload ||
|:-                             |-:|-:|-:|
|*bit position*                 |*23...20*|*19...16*|*15...0*|
|client authentication message  |0xA|-|random|
|client authentication HMAC     |0xB|-|hmac|
|client authentication reset    |0xF|-|-|

- Payload "-" means bits are ignored.
- Data packets with unknown headers are ignored.
- "client authentication reset" is used internally by the server only.


## Self-signed certificates

QUIC connection uses self-signed certificates for server and client. 

- Create certificate and its private key (output files: cert.pem, key.pem):
  ``` 
  openssl req -x509 -newkey rsa:4096 -keyout key.pem -out cert.pem -sha256 -days 100000 -nodes -subj '/CN=localhost'
  ```

- Convert certificate from PEM to DER format:
  ```
  openssl x509 -outform der -in cert.pem -out cert.der
  ```
  
- Convert private key from PEM to DER format:
  ```
  openssl rsa -inform pem -in key.pem -outform der -out key.der
  ```
  
- Convert certificate and private file to PKCS#12 (with password `mtm`):
  ```
  openssl pkcs12 -export -in cert.pem -inkey key.pem -out cert.p12 -passout pass:mtm
  ```

- Get hash value of cert.der:
  ```
  shasum -a 256 cert.der
  ```
  The hash is used to verify the certificate.
  

## Client Authentication

The client is authenticated by requesting its certificate on connection setup. The hash of this self-signed certificate is compared against a reference hash. If this check fails, the connection is not established.


## Acknowledgements

This project uses the following libraries:

- [enigo](https://crates.io/crates/enigo) - mouse control
- [quinn](https://crates.io/crates/quinn) – QUIC protocol implementation
- [rustls](https://crates.io/crates/rustls) - TLS configuration
- [tokio](https://crates.io/crates/tokio) - connection handling
- [zeroconf](https://crates.io/crates/zeroconf) - Bonjour/mDNS service
- [hex-literal](https://https://crates.io/crates/hex-literal) - hex conversion in client authentication 
- [sha2](https://crates.io/crates/sha2) - client authentication with RustCrypto's hashes
- [dirs](https://crates.io/crates/dirs) - home directory in integration test

See `Cargo.toml` and `Cargo.lock` for a full list of dependencies.
