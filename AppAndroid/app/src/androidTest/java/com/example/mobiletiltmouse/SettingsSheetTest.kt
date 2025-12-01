package com.example.mobiletiltmouse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*


class SettingsSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displayElements() {
        composeTestRule.setContent {
            SettingsSheet(
                mouseButtons = arrayOf(true, true, true),
                onMouseButtonsChange = { },
                onShowBottomSheetChange = { },
                sliderPosition = 5f,
                onSliderChangeFinished = { },
                onResetPairing = { }
            )
        }

        composeTestRule.onNodeWithContentDescription("Mouse Cursor Speed Icon").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Mouse Cursor Speed").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Mouse Icon").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Show left mouse button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Show middle mouse button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Show right mouse button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Reset Pairing Icon").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete all paired devices").assertIsDisplayed()
    }

    @Test
    fun showMouseButtonToggles() {
        var showMouseButtons = arrayOf(true, true, true)

        composeTestRule.setContent {
            SettingsSheet(
                mouseButtons = showMouseButtons,
                onMouseButtonsChange = { showMouseButtons = it },
                onShowBottomSheetChange = { },
                sliderPosition = 5f,
                onSliderChangeFinished = { },
                onResetPairing = { }
            )
        }

        composeTestRule.onNodeWithContentDescription("Show left mouse button").performClick()
        assertArrayEquals(arrayOf(false, true, true), showMouseButtons)

        composeTestRule.onNodeWithContentDescription("Show middle mouse button").performClick()
        assertArrayEquals(arrayOf(true, false, true), showMouseButtons)

        composeTestRule.onNodeWithContentDescription("Show right mouse button").performClick()
        assertArrayEquals(arrayOf(true, true, false), showMouseButtons)
    }

    @Test
    fun speedSlider() {
        var currentSliderPosition = 0f
        composeTestRule.setContent {
            SettingsSheet(
                mouseButtons = arrayOf(true, true, true),
                onMouseButtonsChange = { },
                onShowBottomSheetChange = { },
                sliderPosition = currentSliderPosition,
                onSliderChangeFinished = { currentSliderPosition = it },
                onResetPairing = { }
            )
        }

        // move slider to right end
        composeTestRule.onNodeWithContentDescription("Mouse Cursor Speed")
            .performTouchInput {
                swipeRight(startX = centerX)  // startX below 30f fails to move the slider
            }
        assertEquals(speedRange.endInclusive, currentSliderPosition)

        // move slider to intermediate position
        composeTestRule.onNodeWithContentDescription("Mouse Cursor Speed")
            .performTouchInput {
                swipeLeft(startX = centerX + 100, endX = centerX)
            }
        assertTrue(currentSliderPosition > speedRange.start && currentSliderPosition < speedRange.endInclusive)

        // move slider to left end
        composeTestRule.onNodeWithContentDescription("Mouse Cursor Speed")
            .performTouchInput {
                swipeLeft(startX = centerX)
            }
        assertEquals(speedRange.start, currentSliderPosition)
    }

    @Test
    fun resetPairing() {
        var resetPairingCalled = false
        composeTestRule.setContent {
            SettingsSheet(
                mouseButtons = arrayOf(true, true, true),
                onMouseButtonsChange = { },
                onShowBottomSheetChange = { },
                sliderPosition = 5f,
                onSliderChangeFinished = { },
                onResetPairing = { resetPairingCalled = true }
            )
        }

        // open Dialog "Reset Pairing Confirmation"
        composeTestRule.onNodeWithText("Delete all paired devices").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete all paired devices").performClick()
        composeTestRule.onNodeWithContentDescription("Reset pairing confirmation").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reset").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()

        // click "Cancel"
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onNodeWithContentDescription("Reset pairing confirmation").assertIsNotDisplayed()
        assertFalse(resetPairingCalled)

        // click "Reset"
        composeTestRule.onNodeWithText("Delete all paired devices").performClick()
        composeTestRule.onNodeWithContentDescription("Reset pairing confirmation").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reset").performClick()
        composeTestRule.onNodeWithContentDescription("Reset pairing confirmation").assertIsNotDisplayed()
        assertTrue(resetPairingCalled)
    }
}