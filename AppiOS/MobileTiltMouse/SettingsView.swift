import SwiftUI

/// A view that provides settings controls for mouse behavior and button visibility.
///
/// The SettingsView manages two main settings areas:
/// - Mouse movement sensitivity using a slider
/// - Toggle switches for showing/hiding individual mouse buttons
///
/// Mouse speed changes are immediately applied through [`MouseActions`](MouseActions.swift).
/// Button visibility settings are saved to UserDefaults when the view disappears
///
/// The view integrates with:
/// - [`MouseActions`](MouseActions.swift) for real-time cursor sensitivity updates
/// - UserDefaults to persist button visibility settings
/// - [`ContentView`](ContentView.swift) for presenting settings UI
///
struct SettingsView: View {
    var mouseAction: MouseActions?
    @Binding var showMouseButton: [Bool]
    @Binding var mouseSpeed: Double
    
    var body: some View {
        VStack {
            VStack {
                Image(systemName: "cursorarrow.motionlines")
                    .scaleEffect(1.5)
                Slider(value: $mouseSpeed,
                       in: 1...10,
                       step: 1) {
                    Text("Mouse Cursor Speed")
                } minimumValueLabel: {
                    Image(systemName: "tortoise")
                } maximumValueLabel: {
                    Image(systemName: "hare.fill")
                } onEditingChanged: { _ in
                    mouseAction?.setSpeed(mouseSpeed)
                    lgg.info("Mouse speed changed to \(mouseSpeed)")
                }
                .accessibilityIdentifier("SpeedSlider")
            }
            .padding()
            
            VStack {
                Image(systemName: "computermouse.fill").scaleEffect(2)
                Toggle(isOn: $showMouseButton[0]) {
                    HStack(spacing: 0) {
                        Image(systemName: "rectangle.portrait.fill")
                        Image(systemName: "rectangle.portrait")
                        Image(systemName: "rectangle.portrait")
                    }
                }
                .accessibilityLabel("Show left mouse button")
                .accessibilityIdentifier("ShowLeftMouseButton")
                
                Toggle(isOn: $showMouseButton[1]) {
                    HStack(spacing: 0) {
                        Image(systemName: "rectangle.portrait")
                        Image(systemName: "rectangle.portrait.fill")
                        Image(systemName: "rectangle.portrait")
                    }
                }
                .accessibilityLabel("Show middle mouse button")
                .accessibilityIdentifier("ShowMiddleMouseButton")
                
                Toggle(isOn: $showMouseButton[2]) {
                    HStack(spacing: 0) {
                        Image(systemName: "rectangle.portrait")
                        Image(systemName: "rectangle.portrait")
                        Image(systemName: "rectangle.portrait.fill")
                    }
                }
                .accessibilityLabel("Show right mouse button")
                .accessibilityIdentifier("ShowRightMouseButton")
            }
            .tint(.blue)
            .padding()
        }
        .padding()
        .onDisappear {
            UserDefaults.standard.set(showMouseButton, forKey: "showMouseButton")
        }
    
        
    }
      
}

#Preview {
    @Previewable @State var showMouseButton = [true, true, true]
    @Previewable @State var mouseSpeed = 5.0
    SettingsView(mouseAction: nil, showMouseButton: $showMouseButton, mouseSpeed: $mouseSpeed)
}
