package com.example.mobiletiltmouse


import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*


class MouseActionsTest {

    class MockConnection : Connection(null, null) {
        var sendData = mutableListOf<ByteArray>()
        override fun send(data: ByteArray) {
            sendData.add(data)
        }
    }


    @Test
    fun enableStopCursor() {
        val mouseActions = MouseActions(null, MockConnection())

        mouseActions.enableStopCursor(true)
        assertTrue(mouseActions.stopCursor)
        mouseActions.enableStopCursor(false)
        assertFalse(mouseActions.stopCursor)
    }

    @Test
    fun enableScrollPage() {
        val mouseActions = MouseActions(null, MockConnection())

        mouseActions.enableScrollPage(true)
        assertTrue(mouseActions.scrollPage)
        mouseActions.enableScrollPage(false)
        assertFalse(mouseActions.scrollPage)
    }

    @Test
    fun setSpeed() {
        val mouseActions = MouseActions(null, MockConnection())

        for ((speedin, speedout) in listOf(-1.0, 0.0, 2.2, 10.0, 12.0).zip(listOf(1, 1, 2, 10, 10))) {
            mouseActions.setSpeed(speedin.toFloat())
            assertEquals(mouseActions.speed, speedout)
        }
    }

    @Test
    fun clip511() {
        val mouseActions = MouseActions(null, MockConnection())

        val listIn = listOf(-1000, -512, -511, -5, 0, 5, 510, 511, 512, 1000)
        val listOut = listOf(-511, -511, -511, -5, 0, 5, 510, 511, 511, 511)
        for ((dataIn, dataOut) in listIn.zip(listOut)) {
            assertEquals(mouseActions.clip511(dataIn), dataOut)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun allButtons() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val connection = MockConnection()
        val mouseActions = MouseActions(null, connection, dispatcher)

        mouseActions.leftButton(MouseButtonEvent.PRESS)
        mouseActions.leftButton(MouseButtonEvent.RELEASE)
        mouseActions.middleButton(MouseButtonEvent.PRESS)
        mouseActions.middleButton(MouseButtonEvent.RELEASE)
        mouseActions.rightButton(MouseButtonEvent.PRESS)
        mouseActions.rightButton(MouseButtonEvent.RELEASE)
        advanceUntilIdle()

        assertArrayEquals(connection.sendData[0], byteArrayOf(0x20, 0x00, 0x00))
        assertArrayEquals(connection.sendData[1], byteArrayOf(0x20, 0x00, 0x01))
        assertArrayEquals(connection.sendData[2], byteArrayOf(0x20, 0x00, 0x02))
        assertArrayEquals(connection.sendData[3], byteArrayOf(0x20, 0x00, 0x03))
        assertArrayEquals(connection.sendData[4], byteArrayOf(0x20, 0x00, 0x04))
        assertArrayEquals(connection.sendData[5], byteArrayOf(0x20, 0x00, 0x05))
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun move() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val connection = MockConnection()
        val mouseActions = MouseActions(null, connection, dispatcher)

        mouseActions.setSpeed(1f)

        mouseActions.move(0.01f,-0.01f)
        advanceUntilIdle()
        assertTrue(connection.sendData.isEmpty())

        mouseActions.move(1f,-1f)
        mouseActions.move(2f, -2f)
        advanceUntilIdle()

        assertArrayEquals(connection.sendData[0], byteArrayOf(0x08, 0x61, 0xE8.toByte()))
        assertArrayEquals(connection.sendData[1], byteArrayOf(0x08, 0x05, 0xFF.toByte()))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun scroll() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val connection = MockConnection()
        val mouseActions = MouseActions(null, connection, dispatcher)

        mouseActions.setSpeed(1f)

        mouseActions.scroll(0.01f,-0.01f)
        advanceUntilIdle()
        assertTrue(connection.sendData.isEmpty())

        mouseActions.scroll(-1f,1f)
        advanceUntilIdle()

        assertArrayEquals(connection.sendData[0], byteArrayOf(0x17, 0xA2.toByte(), 0x18.toByte()))
    }
}