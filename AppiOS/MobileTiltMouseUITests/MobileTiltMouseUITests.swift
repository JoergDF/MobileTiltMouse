import XCTest


final class MobileTiltMouseUITests: XCTestCase {
    let app = XCUIApplication()

    override func setUpWithError() throws {
        continueAfterFailure = false
        
        app.launchArguments += ["-UITesting", "YES"]
        app.launch()
    }
   
    
    func testToggleStopCursor() throws {
        let stopCursorToggle = app.switches["stopCursor"]
        
        XCTAssertTrue(stopCursorToggle.exists, "Stop cursor toggle does not exist")
        XCTAssertTrue(stopCursorToggle.isHittable, "Stop cursor toggle is not hittable")
        
        let preToggleValue = stopCursorToggle.value as! String
        // .tab() only changes the toggle, if .switches.firstMatch is used
        stopCursorToggle.switches.firstMatch.tap()
        let postToggleValue = stopCursorToggle.value as! String
        XCTAssertTrue(preToggleValue != postToggleValue, "Stop cursor toggle was not toggled")
       
    }
    
    func testToggleScroll() throws {
        let scrollPageToggle = app.switches["scrollPage"]
        
        XCTAssertTrue(scrollPageToggle.exists, "Scroll page toggle does not exist")
        XCTAssertTrue(scrollPageToggle.isHittable, "Scroll page toggle is not hittable")
        
        let preToggleValue = scrollPageToggle.value as! String
        // .tab() only changes the toggle, if .switches.firstMatch is used
        scrollPageToggle.switches.firstMatch.tap()
        let postToggleValue = scrollPageToggle.value as! String
        XCTAssertTrue(preToggleValue != postToggleValue, "Scroll page toggle was not toggled")
        
        // if scrolling is on, buttons should be disabled
        XCTAssertTrue(app.switches["stopCursor"].isEnabled, "Stop cursor toggle is not enabled")
        XCTAssertFalse(app.otherElements["LeftMouseButton"].isEnabled, "Left mouse button is not disabled")
        XCTAssertFalse(app.otherElements["MiddleMouseButton"].isEnabled, "Middle mouse button is not disabled")
        XCTAssertFalse(app.otherElements["RightMouseButton"].isEnabled, "Right mouse button is not disabled")
    }
    
    func testMouseButtons() throws {
        let leftMouseButton = app.otherElements["LeftMouseButton"]
        let middleMouseButton = app.otherElements["MiddleMouseButton"]
        let rightMouseButton = app.otherElements["RightMouseButton"]
        
        XCTAssertTrue(leftMouseButton.exists, "Left mouse button does not exist")
        XCTAssertTrue(leftMouseButton.isHittable, "Left mouse button is not hittable")
        
        XCTAssertTrue(middleMouseButton.exists, "Middle mouse button does not exist")
        XCTAssertTrue(middleMouseButton.isHittable, "Middle mouse button is not hittable")

        XCTAssertTrue(rightMouseButton.exists, "Right mouse button does not exist")
        XCTAssertTrue(rightMouseButton.isHittable, "Right mouse button is not hittable")
    }
    
    func testHideShowMouseButtons() throws {
        let settingsButton = app.buttons["Settings"]
        XCTAssertTrue(settingsButton.exists, "Settings button does not exist")
        
        // open sheet "Settings"
        settingsButton.tap()
        
        let showLeftMouseButton = app.switches["ShowLeftMouseButton"]
        let showMiddleMouseButton = app.switches["ShowMiddleMouseButton"]
        let showRightMouseButton = app.switches["ShowRightMouseButton"]
        
        XCTAssertTrue(showLeftMouseButton.exists, "Toggle left mouse button does not exist")
        XCTAssertTrue(showMiddleMouseButton.exists, "Toggle middle mouse button does not exist")
        XCTAssertTrue(showRightMouseButton.exists, "Toggle right mouse button does not exist")
        
        // hide mouse buttons
        let leftMouseButton = app.otherElements["LeftMouseButton"]
        let middleMouseButton = app.otherElements["MiddleMouseButton"]
        let rightMouseButton = app.otherElements["RightMouseButton"]
        
        XCTAssertTrue(leftMouseButton.exists, "Left mouse button does not exist")
        XCTAssertTrue(middleMouseButton.exists, "Middle mouse button does not exist")
        XCTAssertTrue(rightMouseButton.exists, "Right mouse button does not exist")

        showLeftMouseButton.switches.firstMatch.tap()
        XCTAssertFalse(leftMouseButton.exists, "Left mouse button should not exist")
        XCTAssertTrue(middleMouseButton.exists, "Middle mouse button does not exist")
        XCTAssertTrue(rightMouseButton.exists, "Right mouse button does not exist")
        
        showMiddleMouseButton.switches.firstMatch.tap()
        XCTAssertFalse(leftMouseButton.exists, "Left mouse button should not exist")
        XCTAssertFalse(middleMouseButton.exists, "Middle mouse button should not exist")
        XCTAssertTrue(rightMouseButton.exists, "Right mouse button does not exist")
       
        showRightMouseButton.switches.firstMatch.tap()
        XCTAssertFalse(leftMouseButton.exists, "Left mouse button should not exist")
        XCTAssertFalse(middleMouseButton.exists, "Middle mouse button should not exist")
        XCTAssertFalse(rightMouseButton.exists, "Right mouse button should not exist")
        
    }
    
    func testSpeedSlider() throws {
        // open sheet "Settings"
        app.buttons["Settings"].tap()
        
        XCTAssertTrue(app.sliders["SpeedSlider"].exists, "Speed slider does not exist")
        XCTAssertTrue(app.sliders["SpeedSlider"].value as? String == "5", "Speed slider value is not 5")
    }
    
    func testResetPairings() throws {
        // open sheet "Settings"
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.buttons["Delete all paired devices"].exists, "Delete All Paired Devices button does not exist")
        
        // open Confirmation, tap somewhere to Cancel
        app.buttons["Delete all paired devices"].tap()
        XCTAssertTrue(app.buttons["Reset Pairings"].exists, "Confirm Reset Pairings button does not exist")
        app.switches["ShowLeftMouseButton"].switches.firstMatch.tap()
        XCTAssertFalse(app.buttons["Reset Pairings"].exists, "Confirm Reset Pairings button should disappear")
        
        // open Confirmation, hit Reset
        app.buttons["Delete all paired devices"].tap()
        XCTAssertTrue(app.buttons["Reset Pairings"].exists, "Confirm Reset Pairings button does not exist")
        app.buttons["Reset Pairings"].tap()
        XCTAssertFalse(app.buttons["Reset Pairings"].exists, "Confirm Reset Pairings button should disappear")
        XCTAssertTrue(app.staticTexts["Reset done"].exists, "Reset confirmation must exist")
        Thread.sleep(forTimeInterval: 2.0)
        XCTAssertFalse(app.staticTexts["Reset done"].exists, "Reset confirmation must disappear")
    }
    
    func testNetworkSymbol() throws {
        XCTAssertTrue(app.images["WifiEnabledConnecting"].exists, "Wifi enabled image must exists")
        XCTAssertFalse(app.images["WifiDisabled"].exists, "Wifi disabled image must not exists")
        app.buttons["UITestWifi"].tap()
        XCTAssertTrue(app.images["WifiDisabled"].exists, "Wifi disabled image must exists")
        app.buttons["UITestWifi"].tap()
        XCTAssertTrue(app.images["WifiEnabledConnecting"].exists, "Wifi enabled image must exists")
    }
    
    func testAlertError() throws {
        let alert = app.alerts["Error"]
        
        XCTAssertFalse(alert.exists, "Error Alert must not exist")
        app.buttons["UITestError"].tap()
        XCTAssertTrue(alert.exists, "Error Alert must exist")
                
        XCTAssertTrue(alert.staticTexts["UITestError"].exists, "Alert message must exist")
        alert.buttons["OK"].tap()
        XCTAssertFalse(alert.exists, "Error Alert must not exist")
    }
    
    
    func testPairingEntry() throws {
        app.buttons["UITestPairing"].tap()
        
        XCTAssertTrue(app.staticTexts["Please enter pairing code"].exists)
        XCTAssertTrue(app.staticTexts["shown on computer:"].exists)
        
        for idx in 1...5 {
            let digitField = app.textFields["Digit \(idx)"]
            XCTAssertTrue(digitField.exists)
            digitField.typeText("\(idx)")
            XCTAssertEqual(digitField.value as? String, " \(idx)")
        }
        XCTAssertTrue(app.staticTexts["Final code: 1 2 3 4 5"].exists)
        
        // reject code
        app.buttons["UITestRejectCode"].tap()
        XCTAssertTrue(app.staticTexts["Invalid pairing code!"].exists)
        XCTAssertTrue(app.staticTexts["Please try again."].exists)
        let codeReset = app.staticTexts["Please enter pairing code"]
        let expectation = expectation(for: NSPredicate(format: "exists == true"), evaluatedWith: codeReset, handler: nil)
        wait(for: [expectation], timeout: 3)
        for idx in 1...5 {
            XCTAssertEqual(app.textFields["Digit \(idx)"].value as? String, " ")
        }
        // check focus is in field 1 again
        app.textFields["Digit 1"].typeText("0")
        XCTAssertEqual(app.textFields["Digit 1"].value as? String, " 0")
        
        // invalid input
        app.textFields["Digit 2"].typeText("a")
        XCTAssertEqual(app.textFields["Digit 2"].value as? String, " ")
        app.textFields["Digit 2"].typeText("+")
        XCTAssertEqual(app.textFields["Digit 2"].value as? String, " ")
        
        // backspace/delete
        app.textFields["Digit 2"].typeText(XCUIKeyboardKey.delete.rawValue)
        XCTAssertEqual(app.textFields["Digit 1"].value as? String, " ")
        
    }
    
}
