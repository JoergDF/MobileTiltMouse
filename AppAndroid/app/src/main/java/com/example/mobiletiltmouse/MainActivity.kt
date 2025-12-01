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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
 * Singleton object used for tracking and displaying error alerts in the UI.
 *
 * This object exposes two mutable state properties:
 * @property show: A Boolean indicating whether the error alert dialog should be visible.
 * @property message: A String containing the error message to display.
  */
object ErrorAlert {
    var show by mutableStateOf(false)
    var message by mutableStateOf("")
}

/**
 * Singleton object that tracks the current network state.
 *
 * This object exposes two mutable state properties:
 * @property isConnected: A Boolean indicating whether the device is currently connected to the server.
 * @property isWifiEnabled: A Boolean indicating whether WiFi is enabled on the device.
 */
object NetworkState {
    var isConnected by mutableStateOf(false)
    var isWifiEnabled by mutableStateOf(false)
}

/**
 * Singleton object that tracks the state of the device pairing process.
 *
 * This object exposes two mutable state properties:
 * @property showCodeEntry A boolean state that determines whether the pairing code entry
 *                         UI should be displayed.
 * @property codeRejected A boolean state that indicates if the pairing code entered by
 *                        the user was rejected by the server.
 */
object PairingStatus {
    var showCodeEntry by mutableStateOf(false)
    var codeRejected by mutableStateOf(false)
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
    private lateinit var userSettings: UserSettings
    private var remoteAccess: RemoteAccess? = null
    private var mouseActions: MouseActions? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userSettings = UserSettings(this)
        remoteAccess = RemoteAccess(this, userSettings)
        mouseActions = remoteAccess?.mouseActions

        enableEdgeToEdge()
        setContent {
            // prevent app from sleeping
            LocalView.current.keepScreenOn = true

            //MobileMouseTheme {
            AppTheme {
                MobilTiltMouseApp(remoteAccess, userSettings)
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
 * The main composable function for the Mobile Tilt Mouse application's user interface.
 *
 * This function composes the primary UI elements which include:
 * - A `TopAppBar` displaying network status icons (WiFi and connection state) and a settings button.
 * - [MouseToggles] for enabling/disabling cursor movement and scrolling.
 * - [MouseButtons] for performing left, right and middle clicks.
 * - A [SettingsSheet] (modal bottom sheet) to adjust user settings (like mouse button visibility and mouse speed) when enabled.
 * - A [PairingEntry] dialog for the user to input the pairing code from the server.
 * - An `AlertDialog` to display critical error messages from [ErrorAlert].
 *
 *  State Management and Initialization:
 *  - It observes user preferences (e.g., `mouseSpeed`, `showMouseButtons`) from a
 *   `UserSettings` instance and updates the UI accordingly.
 *  - A `LaunchedEffect` is used for one-time initialization tasks when the composable first enters
 *   the composition. This includes:
 *       - Initializing the device ID for the pairing process.
 *       - Setting the initial state of the mouse controls (speed, stop/scroll modes)
 *         based on saved user settings.
 *       - Checking and setting the initial WiFi status.
 *
 * @param remoteAccess An optional instance of [RemoteAccess], which provides access to
 *                     lower-level components like [MouseActions], [Pairing] and [NetworkMonitor].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobilTiltMouseApp(remoteAccess: RemoteAccess?, userSettings: UserSettings) {
    val stopCursor = userSettings.getStopCursor.collectAsState(initial = false)
    val scrollPage = userSettings.getScrollPage.collectAsState(initial = false)
    val mouseSpeed = userSettings.getMouseSpeed.collectAsState(initial = 5f)
    val showMouseButtons = userSettings.getShowMouseButtons.collectAsState(initial = arrayOf(true, true, true))
    val mouseActions = remoteAccess?.mouseActions
    val scope = rememberCoroutineScope()
    var showSettingsSheet by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(painter = painterResource(R.drawable.settings_24px),
                            contentDescription = "Settings")
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

        if (showSettingsSheet) {
            SettingsSheet(
                mouseButtons = showMouseButtons.value,
                onMouseButtonsChange = {
                    scope.launch {
                        userSettings.setShowMouseButtons(it)
                    }
                },
                onShowBottomSheetChange = { showSettingsSheet = it },
                sliderPosition = mouseSpeed.value,
                onSliderChangeFinished = {
                    scope.launch {
                        mouseActions?.setSpeed(it)
                        userSettings.setMouseSpeed(it)
                    }
                },
                onResetPairing = {
                    scope.launch {
                        remoteAccess?.pairing?.resetPairing()
                    }
                }
            )
        }

        if (PairingStatus.showCodeEntry) {
            PairingEntry(
                onCodeComplete = {
                    scope.launch {
                        remoteAccess?.pairing?.sendPairingCode(it)
                    }
                }
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
       MobilTiltMouseApp(null, UserSettings(LocalContext.current))
    }
}