package com.example.mobiletiltmouse

import androidx.compose.ui.test.isNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume
import org.junit.Rule
import org.junit.Test



class IntegrationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun sendButtonClicks() {
        // execute this test only if the argument "remoteArg" is set to "integrationTest"
        // that is done when the server runs the integration test
        val remoteArg = InstrumentationRegistry.getArguments().getString("remoteArg")
        Assume.assumeTrue(remoteArg == "integrationTest")

        // Wifi must be enabled
        composeTestRule.onNodeWithContentDescription("Wifi is disabled").assertDoesNotExist()

        // When network is found and connection to server is established, continue
        composeTestRule.waitUntil (timeoutMillis = 30000) {
            composeTestRule.onNodeWithContentDescription("Network is searched").isNotDisplayed()
        }

        composeTestRule.onNodeWithContentDescription("Left mouse button")
            .performTouchInput {
                down(center)
                Thread.sleep(100)
                up()
            }
        Thread.sleep(100)
        composeTestRule.onNodeWithContentDescription("Middle mouse button")
            .performTouchInput {
                down(center)
                Thread.sleep(100)
                up()
            }
        Thread.sleep(100)
        composeTestRule.onNodeWithContentDescription("Right mouse button")
            .performTouchInput {
                down(center)
                Thread.sleep(100)
                up()
            }
    }
}