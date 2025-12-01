import Foundation
import Network

/// Discovers the mouse control server instance on the local network using Bonjour/mDNS.
///
/// It uses Network framework's NWBrowser to:
/// - Discover the service of the mouse control server in the local domain
/// - Monitor service availability changes
/// - Establish connections to discovered server via ``Connection``
///
/// Only one active browser instance is allowed at a time. Starting browsing while a browser exists will be ignored.
///
/// - Parameters:
///     - networkStatus: For updating network status state
///     - connection: An optional ``Connection`` object for network communication.
///     - bonjourServiceType: Optional string for Bonjour service type. Default is nil, then it is fetched from app's property key list.
///
class NetworkBrowser {
    var browser: NWBrowser?
    var networkStatus: NWStatus
    weak var connection: Connection?
    var bonjourServiceType: String?
    
    init(networkStatus: NWStatus, connection: Connection?, bonjourServiceType: String? = nil) {
        self.networkStatus = networkStatus
        self.connection = connection
        
        if bonjourServiceType == nil {
            // get Bonjour service type string from app's property key list
            self.bonjourServiceType = (Bundle.main.object(forInfoDictionaryKey: "NSBonjourServices") as? [String])?.first
        } else {
            self.bonjourServiceType = bonjourServiceType
        }
    }
    
    /// Starts discovering mouse control server instance on the local network.
    ///
    /// This method:
    /// 1. Checks if browsing is already active (if true: returns silently)
    /// 2. Creates NWBrowser instance for UDP service discovery
    /// 3. Sets up state and results handlers
    /// 4. Starts browsing on main queue
    ///
    /// When a server is discovered:
    /// - Updates network interface status
    /// - Initiates connection via [`Connection`](Connection.swift)
    /// - Automatically stops browsing
    ///
    /// - Note: Requires "NSBonjourServices" key in Info.plist with service type string
    func startBrowsing() {
        // find server and connect to it
        guard browser == nil else {
            lgg.info("Network browsing already started")
            return
        }
        
        lgg.info("Network browsing start")

        guard self.bonjourServiceType?.isEmpty == false else {
            lgg.error("Error: BonjourService type is not defined: \(String(describing: self.bonjourServiceType), privacy: .public)")
            return
        }
            
        browser = NWBrowser(for: .bonjour(type: self.bonjourServiceType!, domain: "local."), using: .udp)
        browser?.stateUpdateHandler = { newState in
            lgg.info("Network browser state updated to: \(String(describing: newState), privacy: .public)")
        }
        browser?.browseResultsChangedHandler = { [weak self] (updated, changes) in
            for change in changes {
                switch change {
                case .added(let result):
                    lgg.info("Network browser added: \(result.endpoint.debugDescription, privacy: .public)")
                    self?.networkStatus.interfaceDisabled = false
                    self?.connection?.startConnection(result.endpoint)
                    lgg.info("Network browser stopped intentionally")
                    self?.stopBrowsing()
                case .removed(let result):
                    lgg.info("Network browser removed: \(result.endpoint.debugDescription, privacy: .public)")
                case .changed(old: let old, new: let new, flags: _):
                    lgg.info("Network browser changed: \(old.endpoint.debugDescription, privacy: .public) \(new.endpoint.debugDescription, privacy: .public)")
                case .identical:
                    lgg.info("Network browser unchanged: \(String(describing: change), privacy: .public)")
                @unknown default:
                    lgg.info("Network browser change unknown: \(String(describing: change), privacy: .public)")
                }
            }
        }
        browser?.start(queue: .main)
    }
    
    /// Stops network service discovery and cleans up resources.
    ///
    /// This method:
    /// - Cancels the active browser instance
    /// - Removes the browser reference
    func stopBrowsing() {
        lgg.info("Stopping network browser")
        browser?.cancel()
        browser = nil
    }
}
