
# <img src="images/app_icon/icon_mtm_ios.png" alt="App Icon" style="width:5%; height:auto;"> MobileTiltMouse
Transform your phone into a wireless mouse for your computer. The [iOS app](./AppiOS/) or [Android app](./AppAndroid/) establishes a secure connection via WiFi to a [server application](./server/) running on your computer in your local network. Simply incline your phone to move the mouse - the mouse pointer responds smoothly to the angle and tilt of your phone, providing natural and precise control. Perfect for presentations, media control, or whenever a traditional mouse isn't handy.


## Features

- Control the mouse cursor using your device’s motion sensors
- Freeze the cursor with a dedicated switch  
- Switch on scroll mode and tilt your phone for horizontal or vertical scrolling
- Smooth cursor acceleration for precise movement
- Adjustable cursor speed
- Enable or disable left, middle, and right mouse buttons individually
- Haptic feedback (vibration) on mouse button press and release
- WiFi connection status indicated
- Automatic server discovery 
- Connection via WiFi, in local network only
- Encrypted connection with verification of self-signed certificates of server and client
- Authentication by pairing: On initial connection a user must enter a numeric code on the phone shown by the server
- Prevents device sleep while the app is active
- Supports three basic positions of holding your phone: horizontal (display upwards), vertical (display left), vertical (display right)
- Mouse pointer can be moved within the current computer monitor only, even if multiple monitors are used
- Server runs on MacOS, Linux (with X11), Windows



## Screenshots of User Interface

![Main UI](images/readme/iOS/screenshot.png) 
![Settings UI](images/readme/iOS/screenshot_settings.png)


## Phone Positions

The illustrations below show the three basic positions in which you can hold your phone. They also show how different tilts of the phone move the cursor. If the phone is tilted more than 45 degrees, basic positions are automatically switched.

![Horizontal Usage](images/readme/iOS/phone_horizontal.png) 
![Vertical Usage Left](images/readme/iOS/phone_vertical_left.png) 
![Vertical Usage Right](images/readme/iOS/phone_vertical_right.png)


## Getting Started

1. Start the server in a terminal on the computer, on which you want to control the mouse.
2. Launch the app on your phone. The app will automatically connect to the server.
3. Use your phone's tilt motion to control the mouse cursor.
4. When finished, exit server with Ctrl-C.

Your computer's firewall must be configured to allow incoming connections for the server.


### Mobile App Usage
- **Motion Control**: Tilt device to move cursor
- **Mouse Buttons**: Tap buttons for click actions
- **Freeze**: Toggle for disabling cursor movement
- **Scroll Mode**: Toggle for scrolling instead of cursor movement
- **Settings**: Adjust sensitivity and button visibility, reset pairings 


## Technical Details

### Network Architecture
- QUIC protocol for secure, low-latency communication
- Automatic service discovery using Bonjour/mDNS
- WiFi-only connection in local network

### Security
- Encrypted QUIC transport
- Self-signed certificates for server and client 
- Verification of both certificates by checking their SHA-256 hashes against reference values
- Authentication by exchanging server ID and client ID on connection establishment. If an ID is unknown to server or to client, usage is blocked and correct pairing code, shown by server, must be entered on client/phone
- Encrypted storage of the authenticated server/client IDs

### Motion Control
- Device motion tracking for cursor movement and scrolling
- Adjustable speed multiplier $s$
- Smooth acceleration curve: $f(x) = s * x^3$
- Continuously transmits non-zero pitch and roll angles, transforming them into relative X and Y mouse cursor movements (e.g. no data transmission when the device is stationary on a flat surface).


## License

This project is licensed under the [MIT license](./LICENSE).
