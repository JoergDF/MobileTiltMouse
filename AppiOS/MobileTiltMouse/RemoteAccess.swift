import Foundation

/// A class that coordinates network connectivity, device discovery, and mouse control functionality.
///
/// The RemoteAccess class manages three main components:
/// - Network connection handling via [`Connection`](Connection.swift)
/// - Service discovery using [`NetworkBrowser`](NetworkBrowser.swift)
/// - Mouse pointer control through [`MouseActions`](MouseActions.swift)
/// - Network monitoring via [`NetworkMonitor`](NetworkMonitor.swift)
///
/// Example usage:
/// ```swift
/// // Create and initialize remote access
/// let remoteAccess = RemoteAccess()
/// 
/// // Start network discovery and motion updates
/// remoteAccess.startRemoteAccess()
/// 
/// // Stop all remote access functionality
/// remoteAccess.stopRemoteAccess()
/// ```
///
/// - Important: Network monitoring starts automatically on initialization to detect
///   network interface availability.
class RemoteAccess {
    var connection: Connection?
    var nwBrowser: NetworkBrowser?
    var mouseAction: MouseActions?
    
    init() {
        connection = Connection(self)
        if let connection {
            nwBrowser = NetworkBrowser(connection: connection)
            mouseAction = MouseActions(connection: connection)
            NetworkMonitor().startMonitoring()
        }
    }
    
    func startRemoteAccess() {
        nwBrowser?.startBrowsing()
        mouseAction?.startMotionUpdate()
    }
    
    func stopRemoteAccess() {
        mouseAction?.stopMotionUpdate()
        connection?.stopConnection()
    }
    
    func restartNetwork() {
        connection?.stopConnection()
        nwBrowser?.stopBrowsing()
        nwBrowser?.startBrowsing()
    }
}
