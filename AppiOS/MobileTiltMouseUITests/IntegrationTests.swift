
import XCTest

final class IntegrationTests: XCTestCase {
    let app = XCUIApplication()
    
    override func setUpWithError() throws {
        continueAfterFailure = false
        app.launch()
    }

    func testSendButtonClicks() throws {
        // Run this test only if the following environment variable is set: TEST_RUNNER_Integration_Testing="YES"
        try XCTSkipUnless(ProcessInfo.processInfo.environment["Integration_Testing"] == "YES", "Skipping integration test testSendButtonClicks")
        
        XCTAssertTrue(app.switches["scrollPage"].value as? String == "0", "Scroll page must be disabled")
        XCTAssertFalse(app.images["WifiDisabled"].exists, "Wifi must be enabled")
        XCTAssertTrue(app.images["WifiEnabledConnecting"].waitForNonExistence(timeout: 10),
                      "Could not connect to MobileTiltMouse server within timeout")
            
        app.otherElements["LeftMouseButton"].tap()
        app.otherElements["MiddleMouseButton"].tap()
        app.otherElements["RightMouseButton"].tap()
    }
   
}
