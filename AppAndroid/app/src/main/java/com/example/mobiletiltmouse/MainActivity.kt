package com.example.mobiletiltmouse

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import com.example.compose.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

/**
 * A singleton object used for tracking and displaying error alerts in the UI.
 *
 * This object exposes two mutable state properties:
 * - [show]: A Boolean indicating whether the error alert dialog should be visible.
 * - [message]: A String containing the error message to display.
  */
object ErrorAlert {
    var show by mutableStateOf(false)
    var message by mutableStateOf("")
}

/**
 * Singleton object that tracks the current network state.
 *
 * This object exposes two mutable state properties:
 * - [isConnected]: A Boolean indicating whether the device is currently connected to the server.
 * - [isWifiEnabled]: A Boolean indicating whether WiFi is enabled on the device.
 */
object NetworkState {
    var isConnected by mutableStateOf(false)
    var isWifiEnabled by mutableStateOf(false)
}

/**
 * MainActivity is the primary Android activity for the MobileMouse application.
 *
 * This activity implements [SensorEventListener] to receive and process sensor data used
 * for controlling mouse actions.
 *
 * Key responsibilities include:
 * - Initializing [RemoteAccess] and [MouseActions] to handle sensor updates and remote commands.
 * - Managing sensor updates by starting them in [onResume] and stopping them in [onPause].
 * - Forwarding sensor events from [onSensorChanged] to [MouseActions.sensorChanged] for processing.
 * - Keeping the device screen on and managing UI state.
 */
class MainActivity : ComponentActivity(), SensorEventListener {
    private var remoteAccess: RemoteAccess? = null
    private var mouseActions: MouseActions? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        remoteAccess = RemoteAccess(this)
        mouseActions = remoteAccess?.mouseActions

        enableEdgeToEdge()
        setContent {
            // prevent app from sleeping
            LocalView.current.keepScreenOn = true

            //MobileMouseTheme {
            AppTheme {
                MobilMouseApp(remoteAccess)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Ignore
    }

    override fun onSensorChanged(event: SensorEvent) {
        mouseActions?.sensorChanged(event)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        mouseActions?.startSensorUpdate(this)
        remoteAccess?.startRemoteAccess()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        mouseActions?.stopSensorUpdate(this)
        remoteAccess?.stopRemoteAccess()
    }
}

/**
 * The main composable function for the MobileMouse application's user interface.
 *
 * This function composes the primary UI elements which include:
 * - A TopAppBar displaying network status icons and a settings button.
 * - Interactive controls such as mouse toggles ([MouseToggles]) and mouse buttons ([MouseButtons]).
 * - A modal bottom sheet ([SettingsSheet]) to adjust user settings (e.g., mouse button visibility and speed) when enabled.
 * - An error alert dialog that displays messages from [ErrorAlert] when errors occur.
 *
 * It collects state from the [UserSettings] instance to:
 * - Observe user preferences (stopCursor, scrollPage, mouseSpeed, and showMouseButtons).
 * - Update the UI accordingly.
 *
 * In addition, it leverages the [MouseActions] instance from the optional [RemoteAccess] parameter
 * to handle sensor events and remote commands.
 *
 * @param remoteAccess An optional instance of [RemoteAccess] used to manage network discovery,
 *                     connection handling, and mouse actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobilMouseApp(remoteAccess: RemoteAccess?) {
    val context = LocalContext.current
    val mouseActions = remoteAccess?.mouseActions
    val userSettings = UserSettings(context)
    val stopCursor = userSettings.getStopCursor.collectAsState(initial = false)
    val scrollPage = userSettings.getScrollPage.collectAsState(initial = false)
    val mouseSpeed = userSettings.getMouseSpeed.collectAsState(initial = 5f)
    val showMouseButtons = userSettings.getShowMouseButtons.collectAsState(initial = arrayOf(true, true, true))
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }


    LaunchedEffect(null) {
        // set initial values for mouse elements
        mouseActions?.enableStopCursor(userSettings.getStopCursor.first())
        mouseActions?.enableScrollPage(userSettings.getScrollPage.first())
        mouseActions?.setSpeed(userSettings.getMouseSpeed.first())

        NetworkState.isWifiEnabled = remoteAccess?.networkMonitor?.wifiEnabled() ?: false
    }

    fun enableMouseButtons(): Boolean {
        return !scrollPage.value && NetworkState.isWifiEnabled && NetworkState.isConnected
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {  },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    if(!NetworkState.isWifiEnabled) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_wifi_off_24),
                            tint = Color.Red,
                            contentDescription = "Wifi is disabled"
                        )
                    } else if (!NetworkState.isConnected) {
                        // show an icon while searching network and connecting is ongoing
                        NetworkUnconnectedIcon()
                    }

                    // settings button
                    IconButton(onClick = { showBottomSheet = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MouseToggles(
                stopCursor = stopCursor.value,
                onStopCursorChange = {
                    mouseActions?.enableStopCursor(it)
                    scope.launch {
                        userSettings.setStopCursor(it)
                    }
                },
                scrollPage = scrollPage.value,
                onScrollPageChange = {
                    mouseActions?.enableScrollPage(it)
                    scope.launch {
                        userSettings.setScrollPage(it)
                    }
                }
            )
            MouseButtons(enableMouseButtons(), showMouseButtons.value, mouseActions)
            Spacer(Modifier.weight(1.0f))
        }

        if (showBottomSheet) {
            SettingsSheet(
                mouseButtons = showMouseButtons.value,
                onMouseButtonsChange = {
                    scope.launch {
                        userSettings.setShowMouseButtons(it)
                    }
                },
                onShowBottomSheetChange = { showBottomSheet = it },
                sliderPosition = mouseSpeed.value,
                onSliderChangeFinished = {
                    mouseActions?.setSpeed(it)
                    scope.launch {
                        userSettings.setMouseSpeed(it)
                    }
                },
            )
        }
    }

    if (ErrorAlert.show) {
        AlertDialog(
            onDismissRequest = { ErrorAlert.show = false; ErrorAlert.message = "" },
            title = { Text("Error") },
            text = { Text(ErrorAlert.message) },
            confirmButton = {
                Button( onClick = { ErrorAlert.show = false; ErrorAlert.message = "" } ) {
                    Text("OK")
                }
            }
        )
    }
}

/**
 * Displays an animated Wi-Fi icon indicating a transient network connection state.
 *
 * This composable uses an infinite transition to oscillate the icon's alpha value,
 * creating a pulsing effect that visually represents the process of searching or connecting to a network.
 */
@Composable
fun NetworkUnconnectedIcon() {
    val transition = rememberInfiniteTransition(label = "Network Icon Transition")

    val visibility by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Network Icon Animation"
    )

    Icon(
        painter = painterResource(R.drawable.rounded_wifi_24),
        contentDescription = "Network is searched",
        modifier = Modifier.alpha(visibility)
    )
}

@Preview(showBackground = true)
@Composable
fun MobileMouseAppPreview() {
    AppTheme {
       MobilMouseApp(null)
    }
}