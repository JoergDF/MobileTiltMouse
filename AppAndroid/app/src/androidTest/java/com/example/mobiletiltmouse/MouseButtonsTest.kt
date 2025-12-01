package com.example.mobiletiltmouse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*


class MouseButtonsTest {
    class MockMouseActions() : MouseActions(null) {
        val leftEvents = mutableListOf<MouseButtonEvent>()
        val middleEvents = mutableListOf<MouseButtonEvent>()
        val rightEvents = mutableListOf<MouseButtonEvent>()

        override fun leftButton(event: MouseButtonEvent) {
            leftEvents.add(event)
        }

        override fun middleButton(event: MouseButtonEvent) {
            middleEvents.add(event)
        }

        override fun rightButton(event: MouseButtonEvent) {
            rightEvents.add(event)
        }
    }

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showButtons_all() {
        composeTestRule.setContent {
            MouseButtons(
                enabled = true,
                showMouseButtons = arrayOf(true, true, true),
                mouseAction = null
            )
        }
        composeTestRule.onNodeWithContentDescription("Left mouse button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Middle mouse button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Right mouse button").assertIsDisplayed()
    }

    @Test
    fun showButtons_notRight() {
        composeTestRule.setContent {
            MouseButtons(
                enabled = true,
                showMouseButtons = arrayOf(true, true, false),
                mouseAction = null
            )
        }
        composeTestRule.onNodeWithContentDescription("Left mouse button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Middle mouse button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Right mouse button").assertIsNotDisplayed()
    }

    @Test
    fun showButtons_onlyRight() {
        composeTestRule.setContent {
            MouseButtons(
                enabled = true,
                showMouseButtons = arrayOf(false, false, true),
                mouseAction = null
            )
        }
        composeTestRule.onNodeWithContentDescription("Left mouse button").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription("Middle mouse button").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription("Right mouse button").assertIsDisplayed()
    }

    @Test
    fun showButtons_notMiddle() {
        composeTestRule.setContent {
            MouseButtons(
                enabled = true,
                showMouseButtons = arrayOf(true, false, true),
                mouseAction = null
            )
        }
        composeTestRule.onNodeWithContentDescription("Left mouse button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Middle mouse button").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription("Right mouse button").assertIsDisplayed()
    }

    @Test
    fun showButtons_onlyMiddle() {
        composeTestRule.setContent {
            MouseButtons(
                enabled = true,
                showMouseButtons = arrayOf(false, true, false),
                mouseAction = null
            )
        }
        composeTestRule.onNodeWithContentDescription("Left mouse button").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription("Middle mouse button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Right mouse button").assertIsNotDisplayed()
    }

    @Test
    fun showButtons_notLeft() {
        composeTestRule.setContent {
            MouseButtons(
                enabled = true,
                showMouseButtons = arrayOf(false, true, true),
                mouseAction = null
            )
        }
        composeTestRule.onNodeWithContentDescription("Left mouse button").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription("Middle mouse button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Right mouse button").assertIsDisplayed()
    }

    @Test
    fun showButtons_onlyLeft() {
        composeTestRule.setContent {
            MouseButtons(
                enabled = true,
                showMouseButtons = arrayOf(true, false, false),
                mouseAction = null
            )
        }
        composeTestRule.onNodeWithContentDescription("Left mouse button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Middle mouse button").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription("Right mouse button").assertIsNotDisplayed()
    }

    @Test
    fun leftButton_click() {
        val mockMouseActions = MockMouseActions()
        composeTestRule.setContent {
            MouseButtons(
                enabled = true,
                showMouseButtons = arrayOf(true, true, true),
                mouseAction = mockMouseActions
            )
        }

        composeTestRule.onNodeWithContentDescription("Left mouse button")
            .performTouchInput {
                down(center)
                up()
            }

        assertEquals(MouseButtonEvent.PRESS, mockMouseActions.leftEvents[0])
        assertEquals(MouseButtonEvent.RELEASE, mockMouseActions.leftEvents[1])
        assertTrue(mockMouseActions.middleEvents.isEmpty())
        assertTrue(mockMouseActions.rightEvents.isEmpty())
    }

    @Test
    fun middleButton_click() {
        val mockMouseActions = MockMouseActions()
        composeTestRule.setContent {
            MouseButtons(
                enabled = true,
                showMouseButtons = arrayOf(true, true, true),
                mouseAction = mockMouseActions
            )
        }

        composeTestRule.onNodeWithContentDescription("Middle mouse button")
            .performTouchInput {
                down(center)
                up()
            }

        assertTrue(mockMouseActions.leftEvents.isEmpty())
        assertEquals(MouseButtonEvent.PRESS, mockMouseActions.middleEvents[0])
        assertEquals(MouseButtonEvent.RELEASE, mockMouseActions.middleEvents[1])
        assertTrue(mockMouseActions.rightEvents.isEmpty())
    }

    @Test
    fun rightButton_click() {
        val mockMouseActions = MockMouseActions()
        composeTestRule.setContent {
            MouseButtons(
                enabled = true,
                showMouseButtons = arrayOf(true, true, true),
                mouseAction = mockMouseActions
            )
        }

        composeTestRule.onNodeWithContentDescription("Right mouse button")
            .performTouchInput {
                down(center)
                up()
            }

        assertTrue(mockMouseActions.leftEvents.isEmpty())
        assertTrue(mockMouseActions.middleEvents.isEmpty())
        assertEquals(MouseButtonEvent.PRESS, mockMouseActions.rightEvents[0])
        assertEquals(MouseButtonEvent.RELEASE, mockMouseActions.rightEvents[1])
    }

    @Test
    fun buttons_disabled() {
        val mockMouseActions = MockMouseActions()
        composeTestRule.setContent {
            MouseButtons(
                enabled = false,
                showMouseButtons = arrayOf(true, true, true),
                mouseAction = mockMouseActions
            )
        }

        composeTestRule.onNodeWithContentDescription("Left mouse button")
            .performTouchInput {
                down(center)
                up()
            }
        composeTestRule.onNodeWithContentDescription("Middle mouse button")
            .performTouchInput {
                down(center)
                up()
            }
        composeTestRule.onNodeWithContentDescription("Right mouse button")
            .performTouchInput {
                down(center)
                up()
            }

        assertTrue(mockMouseActions.leftEvents.isEmpty())
        assertTrue(mockMouseActions.middleEvents.isEmpty())
        assertTrue(mockMouseActions.rightEvents.isEmpty())
    }
}