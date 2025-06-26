import Foundation
import SwiftUICore
import CoreMotion


/// Represents mouse button press and release events.
enum MouseButtonEvent: CaseIterable {
    case press
    case release
}

/// A class that handles mouse pointer control using device motion and button events.
///
/// The MouseActions class manages:
/// - Device motion tracking for cursor movement and scrolling
/// - Mouse button press/release events
/// - Motion sensitivity settings
/// - Cursor freeze and scroll mode toggles
///
/// Example usage:
/// ```swift
/// let mouseActions = MouseActions(connection: connection)
///
/// // Start motion tracking
/// mouseActions.startMotionUpdate()
/// 
/// // Handle button events
/// mouseActions.leftButton(.press)
/// mouseActions.leftButton(.release)
///
/// // Configure settings
/// mouseActions.setSpeed(5.0)
/// mouseActions.enableStopCursor(true)
/// mouseActions.enableScrollPage(true)
/// ```
///
/// The class uses CoreMotion for device attitude tracking and sends movement/button
/// commands through the provided [`Connection`](Connection.swift) instance.
///
/// Motion data format:
/// ```
/// +------------+----------+----------+
/// | 4-bit type | 10-bit y | 10-bit x |
/// +------------+----------+----------+
/// ```
/// Where type is:
/// - 0x0: Mouse movement
/// - 0x1: Page scrolling
///
/// Button event data format:
/// ```
/// +------------+--------+--------+
/// | 0x20 (type)|  0x00  | action |
/// +------------+--------+--------+
/// ```
/// Where action is:
/// - 0x00/0x01: Left button press/release
/// - 0x02/0x03: Middle button press/release
/// - 0x04/0x05: Right button press/release
///

class MouseActions {
    let connection: Connection?
    let motionManager: CMMotionManager
    var scrollPage: Bool
    var stopCursor: Bool
    var speed: Int
    
    init(connection: Connection?) {
        self.connection = connection
        motionManager = CMMotionManager()
        scrollPage = false
        stopCursor = false
        speed = 5       
    }
    
    /// Starts device motion tracking for cursor movement and scrolling.
    ///
    /// This method:
    /// 1. Checks if device motion tracking is available
    /// 2. Sets update interval to 0.1 seconds (10 Hz)
    /// 3. Starts motion updates on the main queue
    /// 4. Processes pitch and roll values to control cursor/scrolling
    ///
    /// Motion processing:
    /// - In scroll mode: Converts motion to scroll events
    /// - In normal mode: Converts motion to cursor movement (unless cursor is frozen)
    ///
    func startMotionUpdate() {
        if motionManager.isDeviceMotionAvailable {
            motionManager.deviceMotionUpdateInterval = 0.1
            
            motionManager.startDeviceMotionUpdates(to: OperationQueue.main) { [weak self] (data, error) in
                var x = data?.attitude.roll  ?? 0.0
                let y = data?.attitude.pitch ?? 0.0

                // use phone when it is rolled by -90° (in right hand) or +90° (in left hand)
                if x < -.pi / 4 {
                    x += .pi / 2
                } else if x > .pi / 4 {
                    x -= .pi / 2
                }
                
                if let scrollPage = self?.scrollPage, scrollPage {
                    self?.scroll(x, y)
                } else {
                    if let stopCursor = self?.stopCursor, !stopCursor {
                        self?.move(x, y)
                    }
                }
            }
        }
    }
    
    /// Stops device motion tracking and cleanup motion resources.
    func stopMotionUpdate() {
        motionManager.stopDeviceMotionUpdates()
    }
    
    /// Enables or disables scroll page mode for motion tracking.
    ///
    /// When scroll mode is enabled:
    /// - Device motion controls page scrolling instead of cursor movement
    /// - Motion data is sent with header type 0x1
    /// - Cursor movement is suspended
    ///
    /// - Parameter scrollPage: Boolean flag to enable (true) or disable (false) scroll mode
    func enableScrollPage(_ scrollPage: Bool) {
        self.scrollPage = scrollPage
    }
    
    /// Enables or disables cursor movement freeze.
    ///
    /// When cursor freeze is enabled:
    /// - Device motion does not control cursor movement
    /// - Motion tracking remains active but cursor updates are suspended
    /// - Scroll mode is unaffected by this setting
    ///
    /// - Parameter stopCursor: Boolean flag to enable (true) or disable (false) cursor freeze
    func enableStopCursor(_ stopCursor: Bool) {
        self.stopCursor = stopCursor
    }
    
    /// Sets the motion sensitivity for cursor movement and scrolling.
    ///
    /// The speed value is used as a multiplier in the motion calculations:
    /// - Higher values make cursor/scrolling more sensitive to device motion
    /// - Lower values require more device motion for the same cursor/scroll distance
    ///
    /// - Parameter speed: Sensitivity multiplier (minimum value is 1.0)
    func setSpeed(_ speed: Double) {
        if speed >= 1.0 {
            self.speed = Int(speed)
        } else {
            self.speed = 1
        }
    }
       
    /// Processes and streams motion data to the server.
    ///
    /// Applies speed multiplier and cubic transformation to motion values,
    /// clips them to valid range (-511 to 511), and sends them in the format:
    /// ```
    /// +------------+----------+----------+
    /// | 4-bit type | 10-bit y | 10-bit x |
    /// +------------+----------+----------+
    /// ```
    ///
    /// - Parameters:
    ///   - remoteX: Roll value from device motion
    ///   - remoteY: Pitch value from device motion
    ///   - header: Packet type (0x0 for movement, 0x1 for scrolling)
    private func streamData(_ remoteX: Double, _ remoteY: Double, header: Int) {
        var x = Int(remoteX * 100.0)
        var y = -Int(remoteY * 100.0)
        
        x = clip511(speed * x * x * x / 2048)
        y = clip511(speed * y * y * y / 2048)
        
        var xy = ((y & 0x3FF) << 10) | (x & 0x3FF)
        
        if xy != 0 {
            xy |= (header << 20)
            connection?.send(Data([UInt8((xy & 0xFF0000) >> 16),
                                   UInt8((xy & 0x00FF00) >> 8),
                                   UInt8( xy & 0x0000FF)]))
        }
    }
    
    /// Clips an integer value to the range -511 to 511.
    ///
    /// - Parameter d: The value to clip
    /// - Returns: The clipped value in range [-511, 511]
    func clip511(_ d: Int) -> Int {
        if d > 511 {
            return 511
        } else if d < -511 {
            return -511
        } else {
            return d
        }
    }

    /// Processes motion data and sends cursor movement commands.
    ///
    /// Converts device motion (pitch and roll) to cursor movement steps and
    /// sends them to the server with header type 0x0.
    ///
    /// - Parameters:
    ///   - remoteX: Roll value from device motion
    ///   - remoteY: Pitch value from device motion
    func move(_ remoteX: Double, _ remoteY: Double) {
        streamData(remoteX, remoteY, header: 0x0)
    }
    
    /// Processes motion data and sends scroll commands.
    ///
    /// Converts device motion (pitch and roll) to scroll steps and
    /// sends them to the server with header type 0x1.
    ///
    /// - Parameters:
    ///   - remoteX: Roll value for horizontal scroll
    ///   - remoteY: Pitch value for vertical scroll
    func scroll(_ remoteX: Double, _ remoteY: Double) {
        streamData(remoteX, remoteY, header: 0x1)
    }

    /// Sends left mouse button events to the server.
    ///
    /// - Parameter event: The button event (press or release)
    func leftButton(_ event: MouseButtonEvent) {
        if event == .press {
            connection?.send(Data([0x20, 0x00, 0x00]))
        } else if event == .release {
            connection?.send(Data([0x20, 0x00, 0x01]))
        }
    }
    
    /// Sends middle mouse button events to the server.
    ///
    /// - Parameter event: The button event (press or release)
    func middleButton(_ event: MouseButtonEvent) {
        if event == .press {
            connection?.send(Data([0x20, 0x00, 0x02]))
        } else if event == .release {
            connection?.send(Data([0x20, 0x00, 0x03]))
        }
    }
    
    /// Sends right mouse button events to the server.
    ///
    /// - Parameter event: The button event (press or release)
    func rightButton(_ event: MouseButtonEvent) {
        if event == .press {
            connection?.send(Data([0x20, 0x00, 0x04]))
        } else if event == .release {
            connection?.send(Data([0x20, 0x00, 0x05]))
        }
    }
}

