# MobileTiltMouse

An Android application that lets you control your computer's mouse using its corresponding [server application](../server).

## Features

- Kotlin, Jetpack Compose, Android Studio
- Minimum SDK: API 29 (Android 10)
- QUIC transport protocol of [Kwik library](https://github.com/ptrd/kwik) 
- Checks self-signed server certificate
- Provides self-signed client certificate
- Device pairing, device IDs stored encrypted
- Tested on API 29 phone and API 35 emulator (API 29 emulator fails: Network service cannot be discovered)


## Screenshots of User Interface

![Main UI](../images/readme/Android/screenshot.png) 
![Settings UI](../images/readme/Android/screenshot_settings.png) 
![2 Buttons UI](../images/readme/Android/screenshot_2buttons.png) 
![1 Button UI](../images/readme/Android/screenshot_1button.png)
![Pairing UI](../images/readme/Android/screenshot_pairing.png)


## Phone Positions

The illustrations below show the three basic positions in which you can hold your phone. 

![Horizontal Usage](../images/readme/Android/phone_horizontal.png) 
![Vertical Usage Left](../images/readme/Android/phone_vertical_left.png) 
![Vertical Usage Right](../images/readme/Android/phone_vertical_right.png)


## Getting Started

1. Start the [server](../server) on your computer.
2. Build and run the app on your phone.
3. The app will automatically connect to the server. On very first connection, the pairing code of the server display need to be entered.
4. Use your phone's motion to control the mouse cursor.


## Client certificate

The creation of the self-signed client certificate of the QUIC connection is described in the [certificate section of the server](../server/README.md#self-signed-certificates).


## Acknowledgements

This project uses the following libraries:

- [Kwik](https://github.com/ptrd/kwik) – QUIC protocol
- [Mockito-Kotlin](https://github.com/mockito/mockito-kotlin) - mock for testing


