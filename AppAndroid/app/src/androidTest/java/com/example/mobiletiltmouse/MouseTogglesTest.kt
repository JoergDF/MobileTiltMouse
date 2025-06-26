package com.example.mobiletiltmouse

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test


class MouseTogglesTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mouseToggles() {
        var stopCursor = false
        var scrollPage = true
        composeTestRule.setContent {
            MouseToggles(
                stopCursor = stopCursor,
                onStopCursorChange = { stopCursor = it },
                scrollPage = scrollPage,
                onScrollPageChange = { scrollPage = it }
            )
        }

        composeTestRule.onNodeWithContentDescription("Freeze cursor").assertIsOff()
        composeTestRule.onNodeWithContentDescription("Scroll mode").assertIsOn()

        composeTestRule.onNodeWithContentDescription("Freeze cursor").performClick()
        composeTestRule.onNodeWithContentDescription("Scroll mode").performClick()

        assertTrue(stopCursor)
        assertFalse(scrollPage)
    }

}