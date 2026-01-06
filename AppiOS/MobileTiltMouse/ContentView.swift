import SwiftUI


/// Error alert state for displaying connection and certificate errors
///
/// Properties:
/// - error: Boolean flag to show/hide the alert
/// - message: Text to display in the alert
@Observable class ErrorAlert {
    var error = false
    var message = ""
}

/// Network status state tracking connection and interface availability
///
/// Properties:
/// - connected: True when connected to a server
/// - interfaceDisabled: True when WiFi interface is unavailable
@Observable class NWStatus {
    var connected = false
    var interfaceDisabled = false
}

/// Pairing status for managing device pairing UI
///
/// Properties:
/// - showCodeEntry: A boolean to control the visibility of the pairing code entry view.
/// - codeRejected: A boolean that is true when the entered pairing code was rejected by the server.
@Observable class PairingStatus {
    var showCodeEntry: Bool = false
    var codeRejected: Bool = false
}


/// The main view of the app that coordinates mouse controls and settings.
///
/// The view manages several key components:
/// - Mouse button controls via ``MouseButtonsView``
/// - Mouse toggles via ``MouseTogglesView``
/// - Settings sheet for configuration via ``SettingsView``
/// - Pairing fullscreen cover for code entry via ``PairingView``
/// - Network status indicators
/// - Error alerts
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
/// In testing mode (UITesting/testRun), network functionality is disabled.
/// For UI testing settings are reset and special buttons are added
/// 
struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    
    @AppStorage("UITesting")  private var UITesting  = false
    @AppStorage("stopCursor") private var stopCursor = false
    @AppStorage("scrollPage") private var scrollPage = false
    @AppStorage("mouseSpeed") private var mouseSpeed = 5.0
    @State private var showMouseButton = UserDefaults.standard.array(forKey: "showMouseButton") as? [Bool] ?? [true, true, true]
    @State private var showSettings = false
    
    @Bindable var errAlert = ErrorAlert()
    @Bindable var networkStatus = NWStatus()
    @Bindable var pairingStatus = PairingStatus()
    
    @State private var remoteAccess: RemoteAccess?
    @State private var testRun: Bool = ProcessInfo.processInfo.arguments.contains("TESTRUN")  // set in .xctestplan (for unit tests, not UI/XCTestCase tests)
    
       
    var body : some View {
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
                    Button("UITestPairing") {
                        pairingStatus.showCodeEntry = true
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
                    SettingsView(
                        pairing: remoteAccess?.pairing,
                        mouseAction: remoteAccess?.mouseAction,
                        showMouseButton: $showMouseButton,
                        mouseSpeed: $mouseSpeed
                    ).presentationDetents([.medium, .large])
                    Spacer()
                }.background(Color.blue.opacity(0.2))
            }
            .fullScreenCover(isPresented: $pairingStatus.showCodeEntry) {
                PairingView(pairing: remoteAccess?.pairing, pairingStatus: pairingStatus)
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
                    remoteAccess = RemoteAccess(errAlert: errAlert, networkStatus: networkStatus, pairingStatus: pairingStatus)
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
