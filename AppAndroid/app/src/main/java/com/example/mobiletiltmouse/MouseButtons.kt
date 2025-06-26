package com.example.mobiletiltmouse

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.compose.AppTheme



/**
 * A composable function that displays interactive mouse buttons based on a set of provided flags.
 *
 * The UI renders up to three buttons (left, middle and right) based on the Boolean flags in the
 * [showMouseButtons] array. Each button is wrapped in a gesture detector that uses the [buttonModifier]
 * to provide visual feedback and haptic response when pressed.
 *
 * When a button is pressed and released, the corresponding method in the nullable [mouseAction]
 * instance is invoked.
 *
 * The function takes an [enabled] parameter to enable or disable the buttons visually via an opacity modifier.
 *
 * @param enabled A Boolean flag that determines if the buttons should be interactable.
 *                When false, the buttons are rendered with reduced opacity.
 * @param showMouseButtons An array of three Booleans representing the visibility state of the left,
 *                         middle, and right mouse buttons respectively.
 * @param mouseAction An optional [MouseActions] instance that handles the button press events.
 *
 */
@Composable
fun MouseButtons(enabled: Boolean, showMouseButtons: Array<Boolean>, mouseAction: MouseActions?) {
    var pressButtonLeft by remember { mutableStateOf(false) }
    var pressButtonMiddle by remember { mutableStateOf(false) }
    var pressButtonRight by remember { mutableStateOf(false) }

    Column (
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.alpha(alpha = if (enabled) 1f else 0.4f) // buttons enabled/disabled
    ) {
        if (showMouseButtons[0]) {
            val frameHeight = if (!showMouseButtons[1] && !showMouseButtons[2]) { 300.dp } else { 150.dp }
            Box(
                buttonModifier(
                    enabled,
                    pressButtonLeft,
                    onButtonChange = {
                        pressButtonLeft = !pressButtonLeft
                        mouseAction?.leftButton(it)
                    }
                )
                    .size(frameHeight)
                    .semantics { contentDescription = "Left mouse button" }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledRectangle()
                    OutlinedRectangle()
                    OutlinedRectangle()
                }
            }
        }

        if (showMouseButtons[1]) {
            val frameHeight = if (!showMouseButtons[0] && !showMouseButtons[2]) { 300.dp } else {
                if ((showMouseButtons[0] && !showMouseButtons[2])
                    || (!showMouseButtons[0] && showMouseButtons[2])) { 150.dp }
                else { 60.dp } }
            val scaleFactor = if (frameHeight > 60.dp) { 1.0f } else { 0.7f }
            Box(
                buttonModifier(
                    enabled,
                    pressButtonMiddle,
                    onButtonChange = {
                        pressButtonMiddle = !pressButtonMiddle
                        mouseAction?.middleButton(it)
                    }
                )
                    .size(frameHeight)
                    .scale(scaleFactor)
                    .semantics { contentDescription = "Middle mouse button" }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedRectangle()
                    FilledRectangle()
                    OutlinedRectangle()
                }
            }
        }

        if (showMouseButtons[2]) {
            val frameHeight = if (!showMouseButtons[0] && !showMouseButtons[1]) { 300.dp } else { 150.dp }
            Box(
                buttonModifier(
                    enabled,
                    pressButtonRight,
                    onButtonChange = {
                        pressButtonRight = !pressButtonRight
                        mouseAction?.rightButton(it)
                    }
                )
                    .size(frameHeight)
                    .semantics { contentDescription = "Right mouse button" }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedRectangle()
                    OutlinedRectangle()
                    FilledRectangle()
                }
            }
        }

    }
}

/**
 * Returns a [Modifier] that adds interactive button behavior including scaling,
 * visual feedback, and gesture detection for haptic responses.
 *
 * The modifier applies the following effects:
 * - Fills the maximum width.
 * - Applies a scaling animation based on the [pressButton] state.
 * - Draws a rounded rectangle with a blue color and variable opacity to indicate press state.
 * - Sets up pointer input to detect gestures:
 *   - When enabled, on detecting a press (first down), it triggers the [MouseButtonEvent.PRESS]
 *     event via [onButtonChange] and performs haptic feedback.
 *   - On release (or cancellation), it triggers [MouseButtonEvent.RELEASE] and performs haptic feedback.
 *
 * @param enabled A Boolean flag that toggles whether the gesture detection is active.
 *                When false, no gesture events are processed.
 * @param pressButton A Boolean representing the current pressed state of the button.
 * @param onButtonChange A callback function invoked with a [MouseButtonEvent] indicating
 *                       whether the button was pressed or released.
 *
 * @return A [Modifier] enriched with scaling, drawing, and pointer input behaviors for interactive buttons.
 */
@Composable
fun buttonModifier(enabled: Boolean, pressButton: Boolean, onButtonChange: (MouseButtonEvent) -> Unit): Modifier {
    val scale: Float by animateFloatAsState(if (pressButton) 0.9f else 1.0f, label = "scaleButton")
    val haptic = LocalHapticFeedback.current
    val buttonColor = MaterialTheme.colorScheme.primary

    return Modifier
        .fillMaxWidth()
        .scale(scale)
        .drawBehind {
            drawRoundRect(
                buttonColor,
                cornerRadius = CornerRadius (10.dp.toPx()),
                alpha = if (pressButton) 0.4f else 1.0f
            )
        }
        .pointerInput(Unit) {
            if (enabled) {
                awaitEachGesture {
                    awaitFirstDown().also {
                        onButtonChange(MouseButtonEvent.PRESS)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        it.consume()
                    }
                    waitForUpOrCancellation().also {
                        onButtonChange(MouseButtonEvent.RELEASE)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        it?.consume()
                    }
                }
            }
        }
}

/**
 * Displays an outlined rectangle icon.
 */
@Composable
fun OutlinedRectangle() = Icon(
    painter = painterResource(R.drawable.rounded_rectangle_24),
    contentDescription = null,
    modifier = Modifier.rotate(90f)
)

/**
 * Displays an filled rectangle icon.
 */
@Composable
fun FilledRectangle() = Icon(
    painter = painterResource(R.drawable.filled_round_rectangle_24),
    contentDescription = null,
    modifier = Modifier.rotate(90f)
)


@Preview(showBackground = true)
@Composable
fun MouseButtonsPreview() {
    AppTheme {
        MouseButtons(true, arrayOf(true, true, true), null)
    }
}