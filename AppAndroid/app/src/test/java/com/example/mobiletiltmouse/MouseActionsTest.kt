package com.example.mobiletiltmouse


import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*


class MouseActionsTest {

    class MockConnection : Connection(null, null, null) {
        var sendData = mutableListOf<ByteArray>()
        override fun send(data: ByteArray) {
            sendData.add(data)
        }
    }


    @Test
    fun enableStopCursor() {
        val mouseActions = MouseActions(null)

        mouseActions.enableStopCursor(true)
        assertTrue(mouseActions.stopCursor)
        mouseActions.enableStopCursor(false)
        assertFalse(mouseActions.stopCursor)
    }

    @Test
    fun enableScrollPage() {
        val mouseActions = MouseActions(null)

        mouseActions.enableScrollPage(true)
        assertTrue(mouseActions.scrollPage)
        mouseActions.enableScrollPage(false)
        assertFalse(mouseActions.scrollPage)
    }

    @Test
    fun setSpeed() {
        val mouseActions = MouseActions(null)

        for ((speedin, speedout) in listOf(-1.0, 0.0, 2.2, 10.0, 12.0).zip(listOf(1, 1, 2, 10, 10))) {
            mouseActions.setSpeed(speedin.toFloat())
            assertEquals(speedout, mouseActions.speed)
        }
    }

    @Test
    fun clip511() {
        val mouseActions = MouseActions(null)

        val listIn = listOf(-1000, -512, -511, -5, 0, 5, 510, 511, 512, 1000)
        val listOut = listOf(-511, -511, -511, -5, 0, 5, 510, 511, 511, 511)
        for ((dataIn, dataOut) in listIn.zip(listOut)) {
            assertEquals(dataOut, mouseActions.clip511(dataIn))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun allButtons() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val connection = MockConnection()
        val mouseActions = MouseActions(null, dispatcher)
        mouseActions.startMouseActionsConnection(connection)

        mouseActions.leftButton(MouseButtonEvent.PRESS)
        mouseActions.leftButton(MouseButtonEvent.RELEASE)
        mouseActions.middleButton(MouseButtonEvent.PRESS)
        mouseActions.middleButton(MouseButtonEvent.RELEASE)
        mouseActions.rightButton(MouseButtonEvent.PRESS)
        mouseActions.rightButton(MouseButtonEvent.RELEASE)
        advanceUntilIdle()

        assertArrayEquals(byteArrayOf(0x20, 0x00, 0x00), connection.sendData[0])
        assertArrayEquals(byteArrayOf(0x20, 0x00, 0x01), connection.sendData[1])
        assertArrayEquals(byteArrayOf(0x20, 0x00, 0x02), connection.sendData[2])
        assertArrayEquals(byteArrayOf(0x20, 0x00, 0x03), connection.sendData[3])
        assertArrayEquals(byteArrayOf(0x20, 0x00, 0x04), connection.sendData[4])
        assertArrayEquals(byteArrayOf(0x20, 0x00, 0x05), connection.sendData[5])
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun move() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val connection = MockConnection()
        val mouseActions = MouseActions(null,  dispatcher)
        mouseActions.startMouseActionsConnection(connection)

        mouseActions.setSpeed(1f)

        mouseActions.streamMotion(0.01f,-0.01f, false)
        assertTrue(connection.sendData.isEmpty())

        mouseActions.streamMotion(1f,-1f, false)
        mouseActions.streamMotion(2f, -2f, false)

        assertArrayEquals(byteArrayOf(0x08, 0x61, 0xE8.toByte()), connection.sendData[0])
        assertArrayEquals(byteArrayOf(0x08, 0x05, 0xFF.toByte()), connection.sendData[1])
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun scroll() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val connection = MockConnection()
        val mouseActions = MouseActions(null, dispatcher)
        mouseActions.startMouseActionsConnection(connection)

        mouseActions.setSpeed(1f)

        mouseActions.streamMotion(0.01f,-0.01f, true)
        assertTrue(connection.sendData.isEmpty())

        mouseActions.streamMotion(-1f,1f, true)
        assertArrayEquals(byteArrayOf(0x13, 0xD3.toByte(), 0x0C.toByte()), connection.sendData[0])
    }
}