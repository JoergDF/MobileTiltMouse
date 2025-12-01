import Foundation

/// Coordinates remote control functionalities by managing network discovery,
/// device pairing, connection handling, mouse action execution, and network monitoring.
///
/// Start sequence:
/// 1. Search server in network - NetworkBrowser()
/// 2. Connect to server - Connection()
/// 3. Check Pairing with server - Pairing()
/// 4. Start mouse control - MouseActions()
/// Each class calls the start of the next step (and might pass parameters).
///
/// Network monitoring starts automatically on initialization to inform the user about network interface availability.
///
/// - Parameters:
///     - errAlert: For showing error alert
///     - networkStatus: Status of network connectivity
///     - pairingStatus: Status of pairing process
///
class RemoteAccess {
    var connection: Connection?
    var nwBrowser: NetworkBrowser?
    var mouseAction: MouseActions?
    var pairing: Pairing?
    
    init(errAlert: ErrorAlert, networkStatus: NWStatus, pairingStatus: PairingStatus) {
        mouseAction = MouseActions()
        pairing = Pairing(mouseAction: mouseAction, pairingStatus: pairingStatus, networkStatus: networkStatus)
        connection = Connection(errAlert: errAlert, networkStatus: networkStatus, pairing: pairing)
        nwBrowser = NetworkBrowser(networkStatus: networkStatus, connection: connection)
        
        NetworkMonitor(networkStatus: networkStatus).startMonitoring()
    }
    
    func startRemoteAccess() {
        nwBrowser?.startBrowsing()
    }
    
    func stopRemoteAccess() {
        mouseAction?.stopMotionUpdate()
        connection?.stopConnection()
    }
}
