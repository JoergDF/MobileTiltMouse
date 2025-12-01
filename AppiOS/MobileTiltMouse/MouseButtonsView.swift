import SwiftUI

/// A view that provides customizable mouse button controls.
///
/// The view displays up to three mouse buttons with the following features:
/// - Press-and-hold functionality
/// - Visual press/release feedback with animations
/// - Haptic feedback on button interactions
/// - Dynamic sizing based on visible button configuration
/// - Configurable button visibility
///
/// Press-and-hold interaction:
/// 1. Touch down on button → Mouse button pressed
/// 2. Keep finger on screen → Mouse button stays pressed
/// 3. Lift finger → Mouse button released
///
/// - Parameters:
///   - mouseAction: Optional reference to `MouseActions` for button events
///   - disable: Boolean flag to disable all buttons
///   - showMouseButton: Array of 3 booleans controlling button visibility [left, middle, right]
///
struct MouseButtonsView: View {
    var mouseAction: MouseActions?
    let disable: Bool
    let showMouseButton: [Bool]
    
    @State private var tapL = false
    @State private var tapM = false
    @State private var tapR = false
    
    var body: some View  {
        VStack {
            if showMouseButton[0] {
                let frameheight = if (!showMouseButton[1] && !showMouseButton[2]) { 300.0 } else { 150.0 }
                ZStack {
                    RoundedRectangle(cornerRadius: 16.0)
                        .fill(tapL ? Color.blue.opacity(0.4) : Color.blue)
                        .frame(height: frameheight)
                        .accessibilityLabel("Left mouse button")
                        .accessibilityIdentifier("LeftMouseButton")
                    
                    HStack(spacing: 0) {
                        Image(systemName: "rectangle.portrait.fill")
                        Image(systemName: "rectangle.portrait")
                        Image(systemName: "rectangle.portrait")
                    }
                }
                .scaleEffect(tapL ? 0.9 : 1)
                .padding()
                .sensoryFeedback(.selection, trigger: tapL)
                .onLongPressGesture(
                    // perform-action is only executed when minimumDuration in seconds ends
                    minimumDuration: Double.infinity,
                    maximumDistance: 50,
                    perform: {},
                    onPressingChanged: {(press) in
                        withAnimation {
                            tapL.toggle()
                        }
                        if press {
                            // press left mouse button
                            mouseAction?.leftButton(MouseButtonEvent.press)
                            lgg.info("Pressed left mouse button")
                        } else {
                            // release left mouse button
                            mouseAction?.leftButton(MouseButtonEvent.release)
                            lgg.info("Released left mouse button")
                        }
                    }
                )
            }
            
            if showMouseButton[1] {
                let frameheight = if (!showMouseButton[0] && !showMouseButton[2]) { 300.0 } else {
                    if ((showMouseButton[0] && !showMouseButton[2]) || (!showMouseButton[0] && showMouseButton[2])) { 150.0 }
                    else { 60.0 }}
                let scalefactor = if frameheight > 60.0 { 1.0 } else { 0.7 }
                ZStack {
                    RoundedRectangle(cornerRadius: 16.0)
                        .fill(tapM ? Color.blue.opacity(0.4) : Color.blue)
                        .frame(height: frameheight)
                        .accessibilityLabel("Middle mouse button")
                        .accessibilityIdentifier("MiddleMouseButton")
                    HStack(spacing: 0) {
                        Image(systemName: "rectangle.portrait")
                        Image(systemName: "rectangle.portrait.fill")
                        Image(systemName: "rectangle.portrait")
                    }.scaleEffect(scalefactor)
                }
                .scaleEffect(tapM ? 0.9 : 1)
                .padding(.horizontal)
                .sensoryFeedback(.selection, trigger: tapM)
                .onLongPressGesture(
                    // perform-action is only executed when minimumDuration in seconds ends
                    minimumDuration: Double.infinity,
                    maximumDistance: 50,
                    perform: {},
                    onPressingChanged: {(press) in
                        withAnimation {
                            tapM.toggle()
                        }
                        if press {
                            // press middle mouse button
                            mouseAction?.middleButton(MouseButtonEvent.press)
                            lgg.info("Pressed middle mouse button")
                        } else {
                            // release middle mouse button
                            mouseAction?.middleButton(MouseButtonEvent.release)
                            lgg.info("Released middle mouse button")
                        }
                    }
                )
            }
            
            if showMouseButton[2] {
                let frameheight = if (!showMouseButton[0] && !showMouseButton[1]) { 300.0 } else { 150.0 }
                ZStack {
                    RoundedRectangle(cornerRadius: 16.0)
                        .fill(tapR ? Color.blue.opacity(0.4) : Color.blue)
                        .frame(height: frameheight)
                        .accessibilityLabel("Right mouse button")
                        .accessibilityIdentifier("RightMouseButton")
                    HStack(spacing: 0) {
                        Image(systemName: "rectangle.portrait")
                        Image(systemName: "rectangle.portrait")
                        Image(systemName: "rectangle.portrait.fill")
                    }
                    
                }
                .scaleEffect(tapR ? 0.9 : 1)
                .padding()
                .sensoryFeedback(.selection, trigger: tapR)
                .onLongPressGesture(
                    // perform-action is only executed when minimumDuration in seconds ends
                    minimumDuration: Double.infinity,
                    maximumDistance: 50,
                    perform: {},
                    onPressingChanged: {(press) in
                        withAnimation {
                            tapR.toggle()
                        }
                        if press {
                            // press right mouse button
                            mouseAction?.rightButton(MouseButtonEvent.press)
                            lgg.info("Pressed right mouse button")
                        } else {
                            // release right mouse button
                            mouseAction?.rightButton(MouseButtonEvent.release)
                            lgg.info("Released right mouse button")
                        }
                    }
                )
            }
            
        }
        .opacity(disable ? 0.2 : 1.0)
        .disabled(disable)
    }
}

#Preview {
    MouseButtonsView(mouseAction: nil, disable: false, showMouseButton: [true , true , true])
}
