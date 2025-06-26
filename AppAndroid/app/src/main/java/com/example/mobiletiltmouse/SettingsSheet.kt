package com.example.mobiletiltmouse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.compose.AppTheme



/**
 * A composable function that displays a modal bottom sheet for adjusting user settings related
 * to mouse actions, such as the speed slider and the visibility of mouse buttons.
 *
 * The function uses different bottom sheet states depending on the preview flag:
 * - In non-preview mode, it leverages [rememberModalBottomSheetState].
 * - In preview mode, it uses [rememberStandardBottomSheetState] with an initial expanded value.
 *
 * Inside the bottom sheet, the UI is composed of:
 * - An icon and a slider for adjusting the mouse cursor speed.
 * - Three rows, each containing icons representing mouse buttons and a [Switch] to toggle their visibility.
 *
 * @param mouseButtons An array of three Booleans representing the state of the left, middle,
 * and right mouse button switches and hence the visibility of the mouse buttons. The index
 * corresponds to the respective mouse button.
 * @param onMouseButtonsChange A callback function that is invoked when any of the mouse button
 * switches are toggled. It provides an updated array of Boolean values.
 * @param onShowBottomSheetChange A callback function that is triggered when the modal bottom sheet
 * is dismissed, allowing the parent composable to control its visibility.
 * @param sliderPosition A Float representing the initial position of the mouse cursor speed slider.
 * @param onSliderChangeFinished A callback function that is invoked once the user has finalized
 * their adjustment on the slider.
 * @param preview A Boolean flag that determines whether the function is being rendered in preview mode.
 * If true, a standard bottom sheet state is used and the sheet is initially expanded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(mouseButtons: Array<Boolean>,
                  onMouseButtonsChange: (Array<Boolean>) -> Unit,
                  onShowBottomSheetChange: (Boolean) -> Unit,
                  sliderPosition: Float,
                  onSliderChangeFinished: (Float) -> Unit,
                  preview: Boolean = false) {

    val sheetState = if (!preview) {
        rememberModalBottomSheetState(skipPartiallyExpanded = false)
    } else {
        rememberStandardBottomSheetState(initialValue = SheetValue.Expanded)
    }
    var localSliderPosition by remember { mutableFloatStateOf(sliderPosition) }

    ModalBottomSheet(
        modifier = Modifier.fillMaxHeight().semantics { contentDescription = "Settings sheet" },
        onDismissRequest = { onShowBottomSheetChange(false) },
        sheetState = sheetState,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                modifier = Modifier.scale(1.5f).padding(10.dp),
                painter = painterResource(R.drawable.rounded_speed_24),
                contentDescription = "Mouse Cursor Speed Icon",
            )
            Slider(
                value = localSliderPosition,
                onValueChange = { localSliderPosition = it },
                onValueChangeFinished = { onSliderChangeFinished(localSliderPosition) },
                valueRange = speedRange,
                steps = 8,
                modifier = Modifier.semantics { contentDescription = "Mouse Cursor Speed" },
            )


            Icon(
                modifier = Modifier.scale(1.5f).padding(10.dp).padding(top = 20.dp),
                painter = painterResource(R.drawable.baseline_mouse_24),
                contentDescription = "Mouse Icon",
            )
            Row (verticalAlignment = Alignment.CenterVertically) {
                FilledRectangle()
                OutlinedRectangle()
                OutlinedRectangle()
                Spacer(Modifier.weight(1.0f))
                Switch(
                    checked = mouseButtons[0],
                    onCheckedChange = {
                        onMouseButtonsChange(arrayOf(
                            !mouseButtons[0],
                            mouseButtons[1],
                            mouseButtons[2]))
                    },
                    modifier = Modifier.semantics { contentDescription = "Show left mouse button" },
                )
            }
            Row (verticalAlignment = Alignment.CenterVertically) {
                OutlinedRectangle()
                FilledRectangle()
                OutlinedRectangle()
                Spacer(Modifier.weight(1.0f))
                Switch(
                    checked = mouseButtons[1],
                    onCheckedChange = {
                        onMouseButtonsChange(arrayOf(
                            mouseButtons[0],
                            !mouseButtons[1],
                            mouseButtons[2]))
                    },
                    modifier = Modifier.semantics { contentDescription = "Show middle mouse button" },
                )
            }
            Row (verticalAlignment = Alignment.CenterVertically) {
                OutlinedRectangle()
                OutlinedRectangle()
                FilledRectangle()
                Spacer(Modifier.weight(1.0f))
                Switch(
                    checked = mouseButtons[2],
                    onCheckedChange = {
                        onMouseButtonsChange(arrayOf(
                            mouseButtons[0],
                            mouseButtons[1],
                            !mouseButtons[2]))
                    },
                    modifier = Modifier.semantics { contentDescription = "Show right mouse button" },
                )
            }

        }

    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    AppTheme {
        SettingsSheet(arrayOf(false, false, false), {}, {}, 5f, {}, preview = true)
    }
}