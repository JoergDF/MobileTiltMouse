import Testing
import Foundation
@testable import MobileTiltMouse

@Suite struct MouseActionsTests {
    class ConnectionMock: Connection {
        var sendData: Data?
        override func send(_ payload: Data) {
            sendData = payload
        }
    }
    
    let connection: ConnectionMock
    let mouseAction: MouseActions
    
    init() {
        connection = ConnectionMock(nil)
        mouseAction = MouseActions(connection: connection)
    }
    
    @Test(arguments: zip(MouseButtonEvent.allCases, [0, 1]))
    func leftButton(event: MouseButtonEvent, lowerByte: UInt8) {
        mouseAction.leftButton(event)
        #expect(connection.sendData == Data([0x20, 0x00, lowerByte]))
    }
    
    @Test(arguments: zip(MouseButtonEvent.allCases, [2, 3]))
    func middleButton(event: MouseButtonEvent, lowerByte: UInt8) {
        mouseAction.middleButton(event)
        #expect(connection.sendData == Data([0x20, 0x00, lowerByte]))
    }

    @Test(arguments: zip(MouseButtonEvent.allCases, [4, 5]))
    func rightButton(event: MouseButtonEvent, lowerByte: UInt8) {
        mouseAction.rightButton(event)
        #expect(connection.sendData == Data([0x20, 0x00, lowerByte]))
    }
    
    @Test(arguments: zip([-1.0, 0.0, 1.0, 2.2, 10.0], [1, 1, 1, 2, 10]))
    func setSpeed(speedIn: Double, speedOut: Int) {
        mouseAction.setSpeed(speedIn)
        #expect(mouseAction.speed == speedOut)
    }
    
    @Test(arguments: zip(
        [-1000, -512, -511, -5, 0, 5, 510, 511, 512, 1000],
        [ -511, -511, -511, -5, 0, 5, 510, 511, 511,  511]))
    func clip511(dataIn: Int, dataOut: Int) {
        #expect(mouseAction.clip511(dataIn) == dataOut)
    }

    @Test(arguments: [true, false])
    func enableScrollPage(enable: Bool) {
        mouseAction.enableScrollPage(enable)
        #expect(mouseAction.scrollPage == enable)
    }
    
    @Test(arguments: [true, false])
    func enableStopCursor(enable: Bool) {
        mouseAction.enableStopCursor(enable)
        #expect(mouseAction.stopCursor == enable)
    }
    
    @Test
    func move() {
        mouseAction.setSpeed(1.0)
        
        mouseAction.move(0.01, -0.01)
        #expect(connection.sendData == nil)
        
        mouseAction.move(1.0, -1.0) // x=y=488 xy=0x07A1E8
        #expect(connection.sendData == Data([0x07, 0xA1, 0xE8]))

        mouseAction.move(2.0, -2.0) // x=y=511 xy=0x7FDFF
        #expect(connection.sendData == Data([0x07, 0xFD, 0xFF]))
    }
    
    @Test
    func scroll() {
        mouseAction.setSpeed(1.0)
        
        mouseAction.scroll(-1.0, 1.0) // x=y=-488 xy=0x186218
        #expect(connection.sendData == Data([0x18, 0x62, 0x18]))
    }
}


