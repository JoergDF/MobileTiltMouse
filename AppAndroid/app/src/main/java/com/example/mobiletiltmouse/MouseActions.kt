package com.example.mobiletiltmouse

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI

private const val TAG = "MouseAction"

/**
 * Represents the allowed speed range for mouse movement adjustments.
 *
 * Speed values set in [MouseActions.setSpeed] are clamped to this range,
 * ensuring that the speed remains within a safe and predictable limit.
 */
val speedRange = 1f..10f

/**
 * Represents the possible mouse button events.
 *
 * These events are used to differentiate between the press and release actions of a mouse button.
 */
enum class MouseButtonEvent {
    PRESS,
    RELEASE
}

/**
 * Handles sensor-based mouse actions and remote button events.
 *
 * The [MouseActions] class processes rotation vector sensor data to control mouse movements
 * or scrolling actions and sends remote commands via a [Connection] interface.
 * It supports adjusting the mouse speed and toggling specific modes such as
 * stopping the cursor movement or enabling page scrolling.
 *
 * Key functionality includes:
 * - Sensor data is used to control mouse movement or scrolling.
 * - Starting and stopping sensor updates via [startSensorUpdate] and [stopSensorUpdate].
 * - Enabling/disabling cursor movement with [enableStopCursor] and page scrolling with [enableScrollPage].
 * - Adjusting movement speed through [setSpeed] within a predefined [speedRange].
 * - Sending formatted mouse movement or scrolling data using methods [move] and [scroll].
 * - Sending button press and release events via [leftButton], [middleButton], and [rightButton]
 *   based on values from the [MouseButtonEvent] enum.
 *
 * @param context The optional Android Context used to retrieve the sensor service.
 * @param connection An optional [Connection] instance for sending mouse action commands.
 */
open class MouseActions(context: Context?,
                        private var connection: Connection?,
                        ioDispatcher: CoroutineDispatcher = Dispatchers.IO)
{
    private val sensorManager = context?.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    var scrollPage = false
    var stopCursor = true  // do not send data to server too early
    var speed = 5
    private val scope = CoroutineScope(ioDispatcher)

    /**
     * Processes sensor events and delegates to either scrolling or moving based on mode.
     *
     * Extracts the pitch and roll values from the [SensorEvent] and:
     * - Calls [scroll] if page scrolling mode is enabled.
     * - Calls [move] if cursor movement is enabled.
     *
     * @param event The sensor event containing rotation vector data.
     */
    fun sensorChanged(event: SensorEvent) {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        val orientationAngles = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val pitch = orientationAngles[1]
        var roll  = orientationAngles[2]

        if (roll < -PI / 4.0) {
            roll += (PI / 2.0).toFloat()
        } else if (roll > PI / 4.0) {
            roll -= (PI / 2.0).toFloat()
        }

        if (scrollPage) {
            scroll(roll, pitch)
        } else {
            if (!stopCursor) {
                move(roll, pitch)
            }
        }
    }

    /**
     * Start sensor updates.
     *
     * Registers the provided [SensorEventListener] to receive updates from the
     * rotation vector sensor.
     *
     * @param listener The listener that will handle sensor events.
     */
    fun startSensorUpdate(listener: SensorEventListener) {
        sensor?.also { sens ->
            sensorManager?.registerListener(listener, sens, 100000)
        }
        Log.d(TAG, "Sensor update started")
    }

    /**
     * Stops sensor data updates.
     *
     * Unregisters the sensor update listener.
     *
     * @param listener The listener to unregister.
     */
    fun stopSensorUpdate(listener: SensorEventListener) {
        sensorManager?.unregisterListener(listener)
        Log.d(TAG, "Sensor update stopped")
    }

    /**
     * Enables or disables stopping cursor movement.
     *
     * When [stopCursor] is enabled, cursor movements are suppressed.
     *
     * @param stopCursor A Boolean flag indicating whether to stop the cursor.
     */
    fun enableStopCursor(stopCursor: Boolean) {
        this.stopCursor = stopCursor
        Log.d(TAG, "Stop cursor: $stopCursor")
    }

    /**
     * Enables or disables page scrolling mode.
     *
     * When enabled, sensor data is used to scroll pages instead of moving the cursor.
     *
     * @param scrollPage A Boolean flag indicating whether page scrolling is active.
     */
    fun enableScrollPage(scrollPage: Boolean) {
        this.scrollPage = scrollPage
        Log.d(TAG, "Scroll page: $scrollPage")
    }

    /**
     * Sets the speed for mouse movement and scrolling.
     *
     * The provided [speed] is clamped to the predefined [speedRange]
     * and then stored as an integer value used in movement computations.
     *
     * @param speed The desired speed value as a Float.
     */
    fun setSpeed(speed: Float) {
        this.speed = when {
            speed < speedRange.start -> speedRange.start
            speed > speedRange.endInclusive -> speedRange.endInclusive
            else -> speed
        }.toInt()
        Log.d(TAG, "Speed: ${this.speed}")
    }

    /**
     * Clips an integer value to the range -511 to 511.
     *
     * @param d The integer value to clip.
     * @return The clipped value.
     */
    fun clip511(d: Int): Int {
        return if (d > 511) {
            511
        } else if (d < -511) {
            -511
        } else {
            d
        }
    }

    /**
     * Processes and sends the mouse movement or scrolling data.
     *
     * Scales the input [remoteX] and [remoteY] based on the current [speed],
     * clips the resulting values, combines them with a header, and sends the data via the [connection].
     *
     * @param remoteX The horizontal component of the movement.
     * @param remoteY The vertical component of the movement.
     * @param header An integer header used to differentiate between move (0x0) and scroll (0x1) events.
     */
    private fun streamData(remoteX: Float, remoteY: Float, header: Int) {
        scope.launch {
            var x = (remoteX * 100.0).toInt()
            var y = (remoteY * 100.0).toInt()

            x = clip511(speed * x * x * x / 2048)
            y = clip511(speed * y * y * y / 2048)

            var xy = ((y and 0x3FF) shl 10) or (x and 0x3FF)

            if (xy != 0) {
                xy = xy or (header shl 20)
                connection?.send(
                    byteArrayOf(
                        ((xy and 0xFF0000) shr 16).toByte(),
                        ((xy and 0x00FF00) shr 8).toByte(),
                        (xy and 0x0000FF).toByte()
                    )
                )
            }
        }
    }

    /**
     * Sends a mouse movement command.
     *
     * Delegates to [streamData] with a header value of 0x0 to indicate a move event.
     *
     * @param remoteX The horizontal component of the movement.
     * @param remoteY The vertical component of the movement.
     */
    fun move(remoteX: Float, remoteY: Float) {
        streamData(remoteX, remoteY, header = 0x0)
    }

    /**
     * Sends a scroll command.
     *
     * Delegates to [streamData] with a header value of 0x1 to indicate a scroll event.
     *
     * @param remoteX The horizontal component of the movement.
     * @param remoteY The vertical component of the movement.
     */
    fun scroll(remoteX: Float, remoteY: Float) {
        streamData(remoteX, remoteY, header = 0x1)
    }

    /**
     * Sends a left mouse button event.
     *
     * Based on the [event] (either [MouseButtonEvent.PRESS] or [MouseButtonEvent.RELEASE]),
     * it sends the corresponding command via the [connection].
     *
     * @param event The [MouseButtonEvent] indicating press or release.
     */
    open fun leftButton(event: MouseButtonEvent) {
        scope.launch {
            when (event) {
                MouseButtonEvent.PRESS -> connection?.send(byteArrayOf(0x20, 0x00, 0x00))
                MouseButtonEvent.RELEASE -> connection?.send(byteArrayOf(0x20, 0x00, 0x01))
            }
            Log.d(TAG, "Left button: $event")
        }
    }

    /**
     * Sends a middle mouse button event.
     *
     * Based on the [event] (either [MouseButtonEvent.PRESS] or [MouseButtonEvent.RELEASE]),
     * it sends the corresponding command via the [connection].
     *
     * @param event The [MouseButtonEvent] indicating press or release.
     */
    open fun middleButton(event: MouseButtonEvent) {
        scope.launch {
            when (event) {
                MouseButtonEvent.PRESS -> connection?.send(byteArrayOf(0x20, 0x00, 0x02))
                MouseButtonEvent.RELEASE -> connection?.send(byteArrayOf(0x20, 0x00, 0x03))
            }
            Log.d(TAG, "Middle button: $event")
        }
    }

    /**
     * Sends a right mouse button event.
     *
     * Based on the [event] (either [MouseButtonEvent.PRESS] or [MouseButtonEvent.RELEASE]),
     * it sends the corresponding command via the [connection].
     *
     * @param event The [MouseButtonEvent] indicating press or release.
     */
    open fun rightButton(event: MouseButtonEvent) {
        scope.launch {
            when (event) {
                MouseButtonEvent.PRESS -> connection?.send(byteArrayOf(0x20, 0x00, 0x04))
                MouseButtonEvent.RELEASE -> connection?.send(byteArrayOf(0x20, 0x00, 0x05))
            }
            Log.d(TAG, "Right button: $event")
        }
    }

}