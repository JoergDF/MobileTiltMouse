import SwiftUI
import os

/// Logger instance for debug messages throughout the app
/// Uses the app's bundle identifier as subsystem and "mmdbg" as category
let lgg = Logger(subsystem: Bundle.main.bundleIdentifier!, category: "mmdbg")

/// Global error alert state for displaying connection and certificate errors
/// 
/// Properties:
/// - error: Boolean flag to show/hide the alert
/// - message: Text to display in the alert
@Observable class errorAlert {
    var error = false
    var message = ""
}

/// Global network status state tracking connection and interface availability
/// 
/// Properties:
/// - connected: True when connected to a server
/// - interfaceDisabled: True when WiFi interface is unavailable
@Observable class NWStatus {
    var connected = false
    var interfaceDisabled = false
}

/// Global instances of state objects
/// Accessible throughout the app for status updates and error handling
var errAlert = errorAlert()
var networkStatus = NWStatus()

/// Main app structure conforming to SwiftUI's App protocol
/// 
/// Creates the main window containing ContentView
/// Entry point for the application
@main
struct MobileMouseApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

