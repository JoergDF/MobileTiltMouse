package com.example.mobiletiltmouse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test


class MainActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun allElementsAreDisplayed() {
        // Wifi icon is checked in a separate test
        composeTestRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Freeze cursor").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Scroll mode").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Left mouse button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Middle mouse button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Right mouse button").assertIsDisplayed()
    }

    @Test
    fun wifiIcon() {
        NetworkState.isWifiEnabled = false
        composeTestRule.onNodeWithContentDescription("Wifi is disabled").assertIsDisplayed()

        NetworkState.isWifiEnabled = true
        NetworkState.isConnected = false
        composeTestRule.onNodeWithContentDescription("Network is searched").assertIsDisplayed()

        NetworkState.isWifiEnabled = true
        NetworkState.isConnected = true
        composeTestRule.onNodeWithContentDescription("Wifi is disabled").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Network is searched").assertDoesNotExist()
    }

    @Test
    fun settingsSheet() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithContentDescription("Settings sheet").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Mouse Cursor Speed").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Settings sheet")
            .performTouchInput { swipeDown() }
        composeTestRule.onNodeWithContentDescription("Settings sheet").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription("Mouse Cursor Speed").assertIsNotDisplayed()
    }

    @Test
    fun errorAlert() {
        ErrorAlert.show = true
        ErrorAlert.message = "Test error"

        composeTestRule.onNodeWithText("Error").assertExists()
        composeTestRule.onNodeWithText("Test error").assertExists()

        // Dismiss the error alert by tapping the "OK" button.
        composeTestRule.onNodeWithText("OK").performClick()

        assertFalse(ErrorAlert.show)
        composeTestRule.onNodeWithText("Error").assertDoesNotExist()
    }

    @Test
    fun pairingEntry() {
        PairingStatus.showCodeEntry = true
        composeTestRule.onNodeWithContentDescription("Pairing code entry").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Digit 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Please enter pairing code").assertExists()

        PairingStatus.showCodeEntry = false
        composeTestRule.onNodeWithContentDescription("Pairing code entry").assertDoesNotExist()

    }
}