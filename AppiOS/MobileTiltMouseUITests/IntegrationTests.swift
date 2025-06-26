
import XCTest

final class IntegrationTests: XCTestCase {
    let app = XCUIApplication()
    
    override func setUpWithError() throws {
        continueAfterFailure = false
        app.launch()
    }

    func testSendButtonClicks() throws {
        XCTAssertTrue(app.switches["scrollPage"].value as? String == "0", "Scroll page must be disabled")
        XCTAssertFalse(app.images["WifiDisabled"].exists, "Wifi must be enabled")
        XCTAssertTrue(app.images["WifiEnabledConnecting"].waitForNonExistence(timeout: 10),
                      "Could not connect to server within timeout")
            
        app.otherElements["LeftMouseButton"].tap()
        app.otherElements["MiddleMouseButton"].tap()
        app.otherElements["RightMouseButton"].tap()
    }
   
}
