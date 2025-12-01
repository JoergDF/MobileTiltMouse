import SwiftUI

/// A view that provides settings controls for mouse behavior and button visibility, and allows for resetting device pairings.
///
/// This view lets the user configure three main aspects of the application:
/// - **Mouse Speed:** Adjust the sensitivity of the mouse cursor movement using a slider. Changes are applied immediately.
/// - **Button Visibility:** Toggle the visibility of the left, middle, and right mouse buttons in the main user interface.
///                    Visibility is persisted to `UserDefaults` when the view disappears.
/// - **Reset Device Pairings:** Delete all stored device pairings. A confimration dialog is presented which resets the pairings on confirmation.
///
/// - Parameters:
///     - pairing: An optional ``Pairing`` object used to reset device pairings.
///     - mouseAction: An optional ``MouseActions`` object used to apply mouse settings changes.
///     - showMouseButton: A binding to an array of booleans that determines the visibility of each mouse button.
///     - mouseSpeed: A binding to a double that controls the speed of the mouse cursor.
///
struct SettingsView: View {
    let pairing: Pairing?
    var mouseAction: MouseActions?
    @Binding var showMouseButton: [Bool]
    @Binding var mouseSpeed: Double
    @State var showPairingResetConfirmation = false
    @State var showPairingResetDone = false
    
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
            
            Image(systemName: "link").scaleEffect(2).overlay(content: {
                Image(systemName: "line.diagonal").scaleEffect(2.5).rotationEffect(.degrees(45))
            }).rotationEffect(.degrees(45))
            Button(action: {showPairingResetConfirmation = true}) {
                Text("Delete all paired devices").padding(8)
            }
            .buttonStyle(.borderedProminent)
            .padding()
            .confirmationDialog(
                "Are you sure you want to reset all pairings?",
                isPresented: $showPairingResetConfirmation,
                titleVisibility: .visible
            ) {
                Button("Reset Pairings", role: .destructive) {
                    pairing?.resetPairing()
                    showPairingResetDone = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                        showPairingResetDone = false
                    }
                }
            }
            .padding(.bottom, 20)
            .overlay(alignment: .bottom) {
                if (showPairingResetDone) {
                    Text("Reset done")
                        .padding(4)
                        .padding(.horizontal)
                        .foregroundColor(Color.white)
                        .background(Color.secondary)
                        
                }
            }
            .animation(.default, value: showPairingResetDone)
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
    SettingsView(pairing: nil, mouseAction: nil, showMouseButton: $showMouseButton, mouseSpeed: $mouseSpeed)
}
