import Foundation
import Network

/// A class that discovers the mouse control server instance on the local network using Bonjour/mDNS.
///
/// The NetworkBrowser class uses Network framework's NWBrowser to:
/// - Discover the service of the mouse control server in the local domain
/// - Monitor service availability changes
/// - Establish connections to discovered server via [`Connection`](Connection.swift) 
///
/// Only one active browser instance is allowed at a time. Starting browsing while a browser exists will be ignored.
///
/// Example usage:
/// ```swift
/// let connection = Connection()
/// let browser = NetworkBrowser(connection: connection)
/// 
/// // Start discovering servers
/// browser.startBrowsing()
/// 
/// // Stop discovery
/// browser.stopBrowsing()
/// ```
///
/// The browser integrates with:
/// - [`Connection`](Connection.swift) to establish connections to discovered servers
/// - [`networkStatus`](MobileMouseApp.swift) to update interface availability state
///
class NetworkBrowser {
    var browser: NWBrowser?
    weak var connection: Connection?
    var bonjourServiceType: String?
    
    init(connection: Connection?, bonjourServiceType: String? = nil) {
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
                    networkStatus.interfaceDisabled = false
                    self?.connection?.startConnection(result.endpoint)
                    lgg.info("Network browser stop intentionally")
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
    /// 1. Cancels the active browser instance
    /// 2. Removes the browser reference
    func stopBrowsing() {
        lgg.info("Stopping network browser")
        browser?.cancel()
        browser = nil
    }
}
