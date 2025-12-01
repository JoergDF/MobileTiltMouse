import SwiftUI

/// A view that provides toggle controls for cursor freeze and scroll mode.
///
/// The view contains two toggle switches:
/// - Cursor freeze: Disables cursor movement
/// - Scroll mode: Switches between cursor movement and page scrolling
/// - Changes are immediately applied through ``MouseActions``
///
/// - Parameters:
///   - mouseAction: Optional reference to ``MouseActions`` for applying toggles
///   - stopCursor: Boolean Binding to freeze cursor
///   - scrollPage: Boolean Binding to enable page scrolling
///
struct MouseTogglesView: View {
    var mouseAction: MouseActions?
    @Binding var stopCursor: Bool
    @Binding var scrollPage: Bool
    
    var body: some View {
        HStack {
            // view should cover full width, otherwise background color is
            // not applied to full width, if buttons are invisible.
            Spacer()
            
            VStack {
                Toggle(isOn: $stopCursor) {
                    Image(systemName: "pin")
                        .scaleEffect(1.5)
                        .accessibilityLabel("Freeze cursor")
                }
                .accessibilityIdentifier("stopCursor")
                .onChange(of: stopCursor) { oldValue, newValue in
                    mouseAction?.enableStopCursor(newValue)
                }
                
                Spacer()

                Toggle(isOn: $scrollPage) {
                    Image(systemName:
                            "arrow.up.and.down.and.arrow.left.and.right")
                    .scaleEffect(1.5)
                    .accessibilityLabel("Scroll mode")
                }
                .accessibilityIdentifier("scrollPage")
                .onChange(of: scrollPage) { oldValue, newValue in
                    mouseAction?.enableScrollPage(newValue)
                }
            }
            .tint(Color.blue)
            .frame(width: 90, height: 80)
            .padding()
            
            // view should cover full width
            Spacer()
        }
    }
}

#Preview {
    @Previewable @State var stopCursor = false
    @Previewable @State var scrollPage = false
    MouseTogglesView(mouseAction: nil, stopCursor: $stopCursor, scrollPage: $scrollPage)
}
