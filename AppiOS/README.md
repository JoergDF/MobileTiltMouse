# MobileTiltMouse

An iOS application that lets you control your computer's mouse using its corresponding [server application](../server).


## Features
- Swift
- SwiftUI
- Xcode
- Minimum deployment: iOS 18.0
- QUIC transport protocol of Apple's network framework
- Check of self-signed server certificate
- Client authentication with self-signed client certificate
- Successfully tested on hardware/iPhone with iOS 18.x (Fails on simulator since iOS 18.4 and MacOS 15.5: network browsing for DNS service - policy denied; Looks like this issue will be fixed in MacOS 15.6 according to [this thread](https://developer.apple.com/forums/thread/780655).)


## Screenshots of User Interface

![Main UI](../images/readme/iOS/screenshot.png) 
![Settings UI](../images/readme/iOS/screenshot_settings.png)
![2 Buttons UI](../images/readme/iOS/screenshot_2buttons.png) 
![1 Button UI](../images/readme/iOS/screenshot_1button.png) 

## Phone Positions

The illustrations below show the three positions in which you can hold your phone. 

![Horizontal Usage](../images/readme/iOS/phone_horizontal.png) 
![Vertical Usage Left](../images/readme/iOS/phone_vertical_left.png) 
![Vertical Usage Right](../images/readme/iOS/phone_vertical_right.png)


## Getting Started

1. Start the server on your computer
2. Build and run the app on your phone. The app will automatically connect to the server.
3. Use your phone's motion to control the mouse cursor.

When you start the app for the first time on your phone, it will request permission to access the local network. Please confirm this permission.


## Client certificate

The creation of the self-signed client certificate of the QUIC connection is described in the [certificate section of the server](../server/README.md#self-signed-certificates). The iOS code requires the PKCS#12 format.