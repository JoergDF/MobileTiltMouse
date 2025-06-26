package com.example.mobiletiltmouse

import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
                onSliderChangeFinished = { }
            )
        }

        composeTestRule.onNodeWithContentDescription("Mouse Cursor Speed").isDisplayed()
        composeTestRule.onNodeWithContentDescription("Show left mouse button").isDisplayed()
        composeTestRule.onNodeWithContentDescription("Show middle mouse button").isDisplayed()
        composeTestRule.onNodeWithContentDescription("Show right mouse button").isDisplayed()
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
                onSliderChangeFinished = { }
            )
        }

        composeTestRule.onNodeWithContentDescription("Show left mouse button").performClick()
        assertArrayEquals(showMouseButtons, arrayOf(false, true, true))

        composeTestRule.onNodeWithContentDescription("Show middle mouse button").performClick()
        assertArrayEquals(showMouseButtons, arrayOf(true, false, true))

        composeTestRule.onNodeWithContentDescription("Show right mouse button").performClick()
        assertArrayEquals(showMouseButtons, arrayOf(true, true, false))
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
                onSliderChangeFinished = { currentSliderPosition = it }
            )
        }

        // move slider to right end
        composeTestRule.onNodeWithContentDescription("Mouse Cursor Speed")
            .performTouchInput {
                swipeRight(startX = centerX)  // startX below 30f fails to move the slider
            }
        assertEquals(currentSliderPosition, speedRange.endInclusive)

        // move slider to left end
        composeTestRule.onNodeWithContentDescription("Mouse Cursor Speed")
            .performTouchInput {
                swipeLeft(startX = centerX)
            }
        assertEquals(currentSliderPosition, speedRange.start)
    }

}