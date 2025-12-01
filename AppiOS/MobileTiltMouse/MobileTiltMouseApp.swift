import SwiftUI
import os

/// Logger instance for debug messages throughout the app
let lgg = Logger(subsystem: Bundle.main.bundleIdentifier!, category: "mtmdbg")


/// Main app structure conforming to SwiftUI's App protocol
@main
struct MobileTiltMouseApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

