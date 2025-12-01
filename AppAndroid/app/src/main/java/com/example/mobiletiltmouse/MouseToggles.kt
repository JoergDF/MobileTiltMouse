package com.example.mobiletiltmouse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.compose.AppTheme



/**
 * A composable function that displays two toggles for controlling mouse features:
 * one for stopping the cursor and one for enabling/disabling page scrolling.
 *
 * The UI consists of two rows, each containing an icon and a [Switch] for updating the respective setting.
 *
 * @param stopCursor A Boolean representing whether the "freeze cursor" feature is enabled.
 * @param onStopCursorChange A callback function invoked when the freeze cursor switch is toggled.
 * @param scrollPage A Boolean indicating whether the page scroll feature is active.
 * @param onScrollPageChange A callback function invoked when the scroll page switch is toggled.
 */
@Composable
fun MouseToggles(
    stopCursor: Boolean,
    onStopCursorChange: (Boolean) -> Unit,
    scrollPage: Boolean,
    onScrollPageChange: (Boolean) -> Unit
) {
    Column (modifier = Modifier.padding(20.dp)) {
        Row (verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.rounded_keep_24),
                contentDescription = null
            )
            Switch(
                checked = stopCursor,
                onCheckedChange = { onStopCursorChange(it) },
                modifier = Modifier
                    .padding(horizontal = 5.dp)
                    .semantics { contentDescription = "Freeze cursor" },
            )
        }

        Row (verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.rounded_drag_pan_24),
                contentDescription = null
            )
            Switch(
                checked = scrollPage,
                onCheckedChange = { onScrollPageChange(it) },
                modifier = Modifier
                    .padding(horizontal = 5.dp)
                    .semantics { contentDescription = "Scroll mode" },
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun MouseTogglesPreview() {
    AppTheme {
        MouseToggles(false, {}, false, {})
    }
}