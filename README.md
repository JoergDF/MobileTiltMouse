
# <img src="images/app_icon/icon_mtm_ios.png" alt="App Icon" style="width:5%; height:auto;"> MobileTiltMouse
Transform your phone into a wireless mouse for your computer. The [iOS app](./AppiOS/README.md) or [Android app](./AppAndroid/README.md) establishes a secure connection via WiFi to a [server application](./server/README.md) running on your computer. Simply incline your phone to move the mouse - the mouse pointer responds smoothly to the angle and tilt of your phone, providing natural and precise control. Perfect for presentations, media control, or whenever a traditional mouse isn't handy.


## Features

- Control the mouse cursor using your device’s motion sensors
- Freeze the cursor with a dedicated switch  
- Switch on scroll mode and tilt your phone for horizontal or vertical scrolling
- Smooth cursor acceleration for precise movement
- Adjustable cursor speed
- Enable or disable left, middle, and right mouse buttons individually
- Haptic feedback (vibration) on mouse button press and release
- WiFi connection status indicated
- Automatic server discovery within the local WiFi network
- Encrypted connection with server certificate verification and client authentication
- Prevents device sleep while the app is active
- Supports three positions based on how you prefer to hold your device: horizontal (display upwards) or vertical (display to the left or right)
- Mouse pointer can be moved only within a single monitor


## Screenshots of User Interface (Examples)

![Main UI](images/readme/iOS/screenshot.png) 
![Settings UI](images/readme/iOS/screenshot_settings.png)


## Phone Positions

The illustrations below show the three basic positions in which you can hold your phone. They also show how different tilts of the phone move the cursor. If the phone is tilted more than 45 degrees, basic positions are automatically changed.

![Horizontal Usage](images/readme/iOS/phone_horizontal.png) 
![Vertical Usage Left](images/readme/iOS/phone_vertical_left.png) 
![Vertical Usage Right](images/readme/iOS/phone_vertical_right.png)


## Getting Started

1. Start the server in a terminal on the computer, on which you want to control the mouse.
2. Launch the app on your phone. The app will automatically connect to the server.
3. Use your phone's motion to control the mouse cursor.
4. When finished, exit server with Ctrl-C.


### Mobile App Usage
- **Motion Control**: Tilt device to move cursor
- **Mouse Buttons**: Tap buttons for click actions
- **Freeze**: Toggle for disabling cursor movement
- **Scroll Mode**: Toggle for scrolling instead of cursor movement
- **Settings**: Adjust sensitivity and button visibility


## Technical Details

### Network Architecture
- QUIC protocol for secure, low-latency communication
- Automatic service discovery using Bonjour/mDNS
- WiFi-only connection in local network

### Security
- Encrypted QUIC transport
- Verification of self-signed server certificate by checking its SHA-256 hash against a reference value
- HMAC-SHA256 client authentication

### Motion Control
- Device motion tracking for cursor movement and scrolling
- Adjustable speed multiplier $s$
- Smooth acceleration curve: $f(x) = s * x^3$
- Continuously transmits non-zero pitch and roll angles, transforming them into relative X and Y mouse cursor movements (e.g. no data transmission when the device is stationary on a flat surface).


## License

This project is licensed under the [MIT license](./LICENSE).
