import Foundation
import Network

/// Monitors network interface availability.
///
/// It uses Network framework's NWPathMonitor to:
/// - Monitor WiFi interface availability
/// - Exclude cellular connections
/// - Update the global `networkStatus` for UI updates
///
/// The monitor updates `networkStatus.interfaceDisabled` when:
/// - WiFi becomes available (false)
/// - WiFi becomes unavailable (true)
///
/// - Parameters:
///     - networkStatus: For updating network status state.
///     
class NetworkMonitor {
    private var monitor: NWPathMonitor
    private var networkStatus: NWStatus
    
    init(networkStatus: NWStatus) {
        monitor = NWPathMonitor(prohibitedInterfaceTypes: [.cellular])
        self.networkStatus = networkStatus
    }
    
    func startMonitoring() {
        monitor.pathUpdateHandler = { path in
            lgg.info("NwMonitor status: \(String(describing:path.status), privacy: .public) \(path.availableInterfaces, privacy: .public)")
            
            self.networkStatus.interfaceDisabled = (path.status == .unsatisfied)
        }
        monitor.start(queue: .global(qos: .background))
    }
}
