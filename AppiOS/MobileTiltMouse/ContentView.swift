import SwiftUI

/// The main view of the app that coordinates mouse controls and settings.
///
/// The view manages several key components:
/// - Mouse button controls via [`MouseButtonsView`](MouseButtonsView.swift)
/// - Mouse toggles via [`MouseTogglesView`](MouseTogglesView.swift)
/// - Settings sheet for configuration via [`SettingsView`](SettingsView.swift)
/// - Network status indicators
/// - Error alerts
///
/// The view integrates with:
/// - [`RemoteAccess`](RemoteAccess.swift) for network and mouse control functionality
/// - [`networkStatus`](MobileMouseApp.swift) for connection state
/// - [`errAlert`](MobileMouseApp.swift) for error handling
///
/// Scene Phase Handling:
/// - `.active`: Prevents sleep mode, starts remote access
/// - `.inactive`/`.background`: Stops remote access, allows sleep
///
/// Settings persistence:
/// - Mouse speed, Cursor/scroll mode, UI Testing mode via @AppStorage
/// - Button visibility via UserDefaults
///
/// Network Status:
/// - Shows WiFi indicator in toolbar
/// - Displays connection status through icon changes
/// - Presents error alerts for connection issues
///
/// - Note: In UI testing mode, network functionality is disabled and settings are reset
struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    
    @AppStorage("UITesting") private var UITesting = false
    @AppStorage("stopCursor") private var stopCursor = false
    @AppStorage("scrollPage") private var scrollPage = false
    @AppStorage("mouseSpeed") private var mouseSpeed = 5.0
    @State private var showMouseButton = UserDefaults.standard.array(forKey: "showMouseButton") as? [Bool] ?? [true, true, true]
    @State private var showSettings = false
    @State private var remoteAccess: RemoteAccess?
    @State private var testRun: Bool = ProcessInfo.processInfo.arguments.contains("TESTRUN")  // set in .xctestplan (for unit tests, not UI/XCTestCase tests)
    
       
    var body : some View {
        @Bindable var errAlert = errAlert
        @Bindable var networkStatus = networkStatus
        
        NavigationStack {
            VStack {
                MouseTogglesView(mouseAction: remoteAccess?.mouseAction,
                                 stopCursor: $stopCursor,
                                 scrollPage: $scrollPage)

                MouseButtonsView(mouseAction: remoteAccess?.mouseAction,
                                 disable: scrollPage || !networkStatus.connected || networkStatus.interfaceDisabled,
                                 showMouseButton: showMouseButton)
                Spacer()
                // Only for UI tests add special buttons that help in testing
                if UITesting {
                    Button("UITestWifi") {
                        networkStatus.interfaceDisabled.toggle()
                    }
                    Button("UITestError") {
                        errAlert.message = "UITestError"
                        errAlert.error = true
                    }
                }
            }
            .background(Color.blue.opacity(0.2))
            .toolbar {
                if !networkStatus.connected {
                    if networkStatus.interfaceDisabled {
                        Image(systemName: "wifi.exclamationmark.circle")
                            .foregroundStyle(.red)
                            .accessibilityLabel("Wifi disabled")
                            .accessibilityIdentifier("WifiDisabled")
                    } else {
                        Image(systemName: "wifi.circle")
                            .symbolEffect(.variableColor)
                            .accessibilityLabel("Trying to establish connection")
                            .accessibilityIdentifier("WifiEnabledConnecting")
                    }
                }
            
                Button(action: { showSettings = true }) {
                    Image(systemName: "gear")
                        .foregroundColor(Color.primary)
                }
                .accessibilityLabel("Settings")
                .accessibilityIdentifier("Settings")
            }
            .sheet(isPresented: $showSettings) {
                VStack {
                    SettingsView(mouseAction: remoteAccess?.mouseAction, showMouseButton: $showMouseButton, mouseSpeed: $mouseSpeed)
                        .presentationDetents([.medium, .large])
                    Spacer()
                }.background(Color.blue.opacity(0.2))
            }
            .alert("Error",
                   isPresented: $errAlert.error,
                   actions: {} ) {
                Text(errAlert.message)
            }
            .onAppear {
                if UITesting {
                    // For UI testing reset all variables which are read from UserDefaults
                    stopCursor = false
                    scrollPage = false
                    mouseSpeed = 5.0
                    showMouseButton = [true, true, true]
                    // Do not start network related code
                    remoteAccess = nil
                } else if testRun {
                    remoteAccess = nil
                } else {
                    remoteAccess = RemoteAccess()
                }
                remoteAccess?.mouseAction?.enableStopCursor(stopCursor)
                remoteAccess?.mouseAction?.enableScrollPage(scrollPage)
                remoteAccess?.mouseAction?.setSpeed(mouseSpeed)
            }
            .onChange(of: scenePhase) { oldState, newState  in
                lgg.info("Scene phase changed from \(String(describing: oldState), privacy: .public) to \(String(describing: newState), privacy: .public)")
                
                switch (oldState, newState) {
                case (.inactive, .active), (.background, .active):
                    // app gets active
                    
                    // prevent sleep mode
                    UIApplication.shared.isIdleTimerDisabled = true
                    
                    remoteAccess?.startRemoteAccess()
                case (.active, .inactive), (.active, .background):
                    // Cancel connection when this app goes inactive or to background,
                    // as underlying network socket would be disconnected anyway.
                    // Server detects that and gets ready to establich a new connection,
                    // otherwise it would wait to receive more data and reconnect would mostly fail.
                    remoteAccess?.stopRemoteAccess()
                    UIApplication.shared.isIdleTimerDisabled = false
                default:
                    break // do nothing
                }
            }
        }
    }
}


#Preview {
    ContentView()
}
