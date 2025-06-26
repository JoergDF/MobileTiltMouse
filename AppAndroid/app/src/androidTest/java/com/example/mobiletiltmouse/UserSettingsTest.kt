package com.example.mobiletiltmouse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
    }

    @Test
    fun stopCursor() = runTest {
        userSettings.setStopCursor(true)
        val stopCursor = userSettings.getStopCursor.first()
        assertEquals(true, stopCursor)
    }

    @Test
    fun scrollPage() = runTest {
        userSettings.setScrollPage(true)
        val scrollPage = userSettings.getScrollPage.first()
        assertEquals(true, scrollPage)
    }

    @Test
    fun mouseSpeed() = runTest {
        userSettings.setMouseSpeed(1.0f)
        val mouseSpeed = userSettings.getMouseSpeed.first()
        assertEquals(1.0f, mouseSpeed)
    }

    @Test
    fun showMouseButtons() = runTest {
        userSettings.setShowMouseButtons(arrayOf(true, false, true))
        val showMouseButtons = userSettings.getShowMouseButtons.first()
        assertArrayEquals(arrayOf(true, false, true), showMouseButtons)
    }

}