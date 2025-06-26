import Testing
import Network
@testable import MobileTiltMouse


@Suite struct NetworkBrowserTests {
    
    class MockConnection: Connection {
        var startedConnection = false
        
        override func startConnection(_ endpoint: NWEndpoint? = nil) {
            startedConnection = true
       }
    }
    
    
    @Test
    func initConnection() {
        let connection = MockConnection(nil)
        let browser = NetworkBrowser(connection: connection)
       
        #expect(browser.connection === connection)
    }
    
    @Test
    func initBonjourType() {
        let browser1 = NetworkBrowser(connection: nil, bonjourServiceType: "abc")
        #expect(browser1.bonjourServiceType == "abc")
        
        let browser2 = NetworkBrowser(connection: nil, bonjourServiceType: nil)
        #expect(browser2.bonjourServiceType == "_mobilemouse._udp")
        
        let browser3 = NetworkBrowser(connection: nil)
        #expect(browser3.bonjourServiceType == "_mobilemouse._udp")
    }
    
    @Test
    func browserStartsOnce() {
        let browser = NetworkBrowser(connection: nil)
        
        #expect(browser.browser === nil)
        browser.startBrowsing()
        #expect(browser.browser !== nil)
        
        let initialBrowser = browser.browser
        
        browser.startBrowsing()
        
        #expect(browser.browser === initialBrowser)
    }
    
    @Test
    func bonjourType() {
        let browser = NetworkBrowser(connection: nil, bonjourServiceType: "")
        
        browser.startBrowsing()
        
        #expect(browser.browser == nil)
    }
    
    @Test
    func stop() {
        let browser = NetworkBrowser(connection: nil)
        
        browser.startBrowsing()
        #expect(browser.browser != nil)
        
        browser.stopBrowsing()
        #expect(browser.browser == nil)
    }
    
}
