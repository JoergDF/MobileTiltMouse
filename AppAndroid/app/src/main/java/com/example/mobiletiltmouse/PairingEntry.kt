package com.example.mobiletiltmouse


import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.compose.AppTheme
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

private const val TAG = "PairingEntry"

// number of digits in pairing code
const val codeSize = 5


/**
 * Displays a dialog for the user to input a numeric pairing code.
 *
 * The dialog presents a series of individual input fields, one for each digit of the code.
 * Focus automatically moves to the next field as the user types. Once all digits are
 * entered, the `onCodeComplete` lambda is invoked.
 *
 * The composable also handles visual feedback for incorrect code submissions by observing
 * an external `PairingStatus.codeRejected` state. If the code is rejected, an error
 * message is shown, the fields are marked with an error state, and after a short delay,
 * the fields are cleared and reset for a new attempt.
 *
 * Backspace key is supported, allowing the user to move to and clear the  previous input field.
 * The dialog cannot be dismissed by tapping outside or pressing the back button until the code
 * entry process is successfully completed.
 *
 * @param onCodeComplete A lambda function that is invoked when the user has
 *                       entered all digits of the pairing code. It receives the
 *                       entered code as a `List<String>`.
 */
@Composable
fun PairingEntry(onCodeComplete: (List<String>) -> Unit) {
    val code = remember { List(codeSize) { "" }.toMutableStateList() }
    val focusRequesters = remember { List(codeSize) { FocusRequester() } }
    var codeCompleted by remember { mutableStateOf(false) }

    // set initial focus to first digit
    LaunchedEffect(null) {
        delay(50) // Small delay to allow UI to settle
        focusRequesters[0].requestFocus()
    }

    // if pairing code was wrong, wait 2 seconds and reset
    LaunchedEffect(PairingStatus.codeRejected) {
        if (PairingStatus.codeRejected) {
            delay(2000)
            PairingStatus.codeRejected = false
            code.fill("")
            focusRequesters[0].requestFocus()
            codeCompleted = false
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface (
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.semantics { contentDescription = "Pairing code entry" }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (PairingStatus.codeRejected) {
                    Text("Invalid pairing code!",
                        fontWeight = FontWeight.Bold,
                        color = Color.Red)
                    Text("Please try again.")
                } else {
                    Text(text = "Please enter pairing code")
                    Text("shown on computer:")
                }
                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (digitIndex in 0..<codeSize) {
                        OutlinedTextField(
                            value = code[digitIndex],
                            onValueChange = {
                                if ("0123456789".contains(it) && it.length == 1) {
                                    code[digitIndex] = it
                                    if (digitIndex < (codeSize - 1)) {
                                        focusRequesters[digitIndex + 1].requestFocus()
                                    } else {
                                        codeCompleted = true // disable entering of more digits
                                        onCodeComplete(code.toList())
                                    }
                                }
                            },
                            readOnly = codeCompleted,
                            isError = PairingStatus.codeRejected,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequesters[digitIndex])
                                .onKeyEvent { event ->
                                    // Backspace key handling
                                    if (event.key == Key.Backspace && digitIndex > 0) {
                                        focusRequesters[digitIndex - 1].requestFocus()
                                        code[digitIndex - 1] = ""
                                        true // Consume the event
                                    } else {
                                        false // Do not consume the event
                                    }
                                }
                                .semantics { contentDescription = "Digit ${digitIndex + 1}" },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                            shape = MaterialTheme.shapes.small, // for rounded corners
                            colors = OutlinedTextFieldDefaults.colors(
                                cursorColor = Color.Transparent,
                                errorCursorColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                autoCorrectEnabled = false
                            )
                        )
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun PairingEntryPreview() {
    AppTheme {
        PairingEntry({})
    }
}