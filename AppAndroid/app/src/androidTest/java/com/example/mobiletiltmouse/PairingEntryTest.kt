package com.example.mobiletiltmouse

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey




class PairingEntryTest
{
    @get:Rule
    val composeTestRule = createComposeRule()


    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testPairingEntry() {
        var code = listOf<String>()
        composeTestRule.setContent {
            PairingEntry(onCodeComplete = { code = it } )
        }

        composeTestRule.onNodeWithText("Please enter pairing code").assertIsDisplayed()
        composeTestRule.onNodeWithText("shown on computer:").assertIsDisplayed()

        for (i in 1..codeSize) {
            composeTestRule.onNodeWithContentDescription("Digit $i").assertIsDisplayed()
            if (i == 1) {
                // API 29 needs some time until first entry box is focused
                composeTestRule.mainClock.advanceTimeBy(100)
            }
            composeTestRule.onNodeWithContentDescription("Digit $i").assertIsFocused()
            composeTestRule.onNodeWithContentDescription("Digit $i").performTextInput("$i")
            composeTestRule.onNodeWithContentDescription("Digit $i").assertTextEquals("$i")
            if (i < codeSize) {
                composeTestRule.onNodeWithContentDescription("Digit ${i + 1}").assertIsFocused()
            }
        }
        assertEquals(listOf("1", "2", "3", "4", "5"), code)

        // code rejected
        PairingStatus.codeRejected = true
        composeTestRule.onNodeWithText("Invalid pairing code!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Please try again.").assertIsDisplayed()
        composeTestRule.mainClock.advanceTimeBy(3000)
        assertEquals(false, PairingStatus.codeRejected)

        // backspace
        composeTestRule.onNodeWithContentDescription("Digit 1").assertIsFocused()
        composeTestRule.onNodeWithContentDescription("Digit 1").performTextInput("3")
        composeTestRule.onNodeWithContentDescription("Digit 2").assertIsFocused()
        composeTestRule.onNodeWithContentDescription("Digit 2").performKeyInput { pressKey(Key.Backspace) }
        composeTestRule.onNodeWithContentDescription("Digit 1").assertIsFocused()
        composeTestRule.onNodeWithContentDescription("Digit 1").assertTextEquals("")
        composeTestRule.onNodeWithContentDescription("Digit 1").performKeyInput { pressKey(Key.Backspace) }
        composeTestRule.onNodeWithContentDescription("Digit 1").assertIsFocused()
        composeTestRule.onNodeWithContentDescription("Digit 1").assertTextEquals("")

        // invalid text input
        composeTestRule.onNodeWithContentDescription("Digit 1").performTextInput("a")
        composeTestRule.onNodeWithContentDescription("Digit 1").assertIsFocused()
        composeTestRule.onNodeWithContentDescription("Digit 1").assertTextEquals("")
        composeTestRule.onNodeWithContentDescription("Digit 1").performTextInput("12")
        composeTestRule.onNodeWithContentDescription("Digit 1").assertIsFocused()
        composeTestRule.onNodeWithContentDescription("Digit 1").assertTextEquals("")
    }
}