package com.example.mobiletiltmouse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*


class UserSettingsTest {
    private lateinit var userSettings: UserSettings
    private lateinit var context: Context

    @Before
    fun setup()  {
        context = ApplicationProvider.getApplicationContext()
        userSettings = UserSettings(context)
        runBlocking { userSettings.deleteAll() }
    }

    @After
    fun tearDown() = runTest {
        userSettings.deleteAll()
    }


    @Test
    fun stopCursor() = runTest {
        assertFalse(userSettings.getStopCursor.first())
        userSettings.setStopCursor(true)
        assertEquals(true, userSettings.getStopCursor.first())
    }

    @Test
    fun scrollPage() = runTest {
        assertFalse(userSettings.getScrollPage.first())
        userSettings.setScrollPage(true)
        assertEquals(true, userSettings.getScrollPage.first())
    }

    @Test
    fun mouseSpeed() = runTest {
        assertEquals(5.0f, userSettings.getMouseSpeed.first())
        userSettings.setMouseSpeed(1.0f)
        assertEquals(1.0f, userSettings.getMouseSpeed.first())
    }

    @Test
    fun showMouseButtons() = runTest {
        assertArrayEquals(arrayOf(true, true, true), userSettings.getShowMouseButtons.first())
        userSettings.setShowMouseButtons(arrayOf(true, false, true))
        assertArrayEquals(arrayOf(true, false, true), userSettings.getShowMouseButtons.first())
    }

    @Test
    fun deviceId() = runTest {
        assertNull(userSettings.getDeviceId.first())
        userSettings.setDeviceId(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07))
        assertArrayEquals(
            byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07),
            userSettings.getDeviceId.first())
        userSettings.setDeviceId(null)
        assertNull(userSettings.getDeviceId.first())
    }

    @Test
    fun serverIds() = runTest {
        assertNull(userSettings.getServerIds.first())
        userSettings.setServerIds(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07))
        assertArrayEquals(
            byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07),
            userSettings.getServerIds.first())
        userSettings.setServerIds(null)
        assertNull(userSettings.getServerIds.first())
    }

    @Test
    fun remoteKey() = runTest {
        assertNull(userSettings.getRemoteKey.first())
        userSettings.setRemoteKey(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07))
        assertArrayEquals(
            byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07),
            userSettings.getRemoteKey.first())
        userSettings.setRemoteKey(null)
        assertNull(userSettings.getRemoteKey.first())
    }
}