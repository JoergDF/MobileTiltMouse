import Foundation
import Network

/// A class that monitors network interface availability.
/// 
/// The NetworkMonitor class uses Network framework's NWPathMonitor to:
/// - Monitor WiFi interface availability
/// - Exclude cellular connections
/// - Update the global [`networkStatus`](MobileMouseApp.swift) for UI updates
///
/// Example usage:
/// ```swift
/// let monitor = NetworkMonitor()
/// monitor.startMonitoring()
/// ```
///
/// The monitor updates [`networkStatus.interfaceDisabled`](MobileMouseApp.swift) when:
/// - WiFi becomes available (false)
/// - WiFi becomes unavailable (true)
/// 
class NetworkMonitor {
    private var monitor: NWPathMonitor

    
    init() {
        monitor = NWPathMonitor(prohibitedInterfaceTypes: [.cellular]) 
    }
    
    func startMonitoring() {
        monitor.pathUpdateHandler = { path in
            lgg.info("NwMonitor status: \(String(describing:path.status), privacy: .public) \(path.availableInterfaces, privacy: .public)")
            
            networkStatus.interfaceDisabled = (path.status == .unsatisfied)
        }
        monitor.start(queue: .global(qos: .background))
    }
}
