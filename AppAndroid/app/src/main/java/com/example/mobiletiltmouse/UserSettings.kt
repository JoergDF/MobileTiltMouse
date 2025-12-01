package com.example.mobiletiltmouse

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val TAG = "UserSettings"


/**
 * UserSettings provides an interface for storing and retrieving user preferences
 * using Android's DataStore.
 *
 * It manages these settings:
 * - STOP_CURSOR: Determines if the mouse cursor should be stopped.
 * - SCROLL_PAGE: Toggles the page scrolling ability.
 * - MOUSE_SPEED: Adjusts the speed of the mouse cursor.
 * - SHOW_MOUSE_BUTTONS: Configures visibility of mouse buttons.
 * - DEVICE_ID: Stores the device ID for identification.
 * - SERVER_IDS: Stores a list of server IDs for identification.
 *
 * @param context The application context used to access the DataStore.
 */
class UserSettings(private val context: Context) {
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "userSettings")
        private val STOP_CURSOR = booleanPreferencesKey("stop_cursor")
        private val SCROLL_PAGE = booleanPreferencesKey("scroll_page")
        private val MOUSE_SPEED = floatPreferencesKey("mouse_speed")
        private val SHOW_MOUSE_BUTTONS = byteArrayPreferencesKey("show_mouse_buttons")
        private val DEVICE_ID = byteArrayPreferencesKey("device_id")
        private val SERVER_IDS = byteArrayPreferencesKey("server_ids")
        private val REMOTE_KEY = byteArrayPreferencesKey("remote_key")
    }

    suspend fun deleteAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    val getStopCursor: Flow<Boolean> = context.dataStore.data
        .catch {
            if (it is IOException) {
                Log.e(TAG, "Error reading preferences.", it)
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map { preferences -> preferences[STOP_CURSOR] ?: false }

    suspend fun setStopCursor(stop: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[STOP_CURSOR] = stop
        }
    }

    val getScrollPage: Flow<Boolean> = context.dataStore.data
        .catch {
            if (it is IOException) {
                Log.e(TAG, "Error reading preferences.", it)
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map { preferences -> preferences[SCROLL_PAGE] ?: false }

    suspend fun setScrollPage(scroll: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SCROLL_PAGE] = scroll
        }
    }

    val getMouseSpeed: Flow<Float> = context.dataStore.data
        .catch {
            if (it is IOException) {
                Log.e(TAG, "Error reading preferences.", it)
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map { preferences -> preferences[MOUSE_SPEED] ?: 5.0f }

    suspend fun setMouseSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[MOUSE_SPEED] = speed
        }
    }

    val getShowMouseButtons: Flow<Array<Boolean>> = context.dataStore.data
        .catch {
            if (it is IOException) {
                Log.e(TAG, "Error reading preferences.", it)
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map { preferences ->
            if (preferences[SHOW_MOUSE_BUTTONS] == null)  {
                arrayOf(true, true, true)
            } else {
                preferences[SHOW_MOUSE_BUTTONS]!!.map { it != 0.toByte() }.toTypedArray()
            }

        }

    suspend fun setShowMouseButtons(showButtons: Array<Boolean>) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_MOUSE_BUTTONS] = showButtons
                .map { if (it) 1.toByte() else 0.toByte() }
                .toByteArray()
        }
    }

    val getDeviceId: Flow<ByteArray?> = context.dataStore.data
        .catch {
            if (it is IOException) {
                Log.e(TAG, "Error reading preferences.", it)
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map { preferences -> preferences[DEVICE_ID] }

    suspend fun setDeviceId(id: ByteArray?) {
        context.dataStore.edit { preferences ->
            if (id == null)
                preferences.remove(DEVICE_ID)
            else
                preferences[DEVICE_ID] = id
        }
    }

    val getServerIds: Flow<ByteArray?> = context.dataStore.data
        .catch {
            if (it is IOException) {
                Log.e(TAG, "Error reading preferences.", it)
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map { preferences -> preferences[SERVER_IDS] }

    suspend fun setServerIds(ids: ByteArray?) {
        context.dataStore.edit { preferences ->
            if (ids == null)
                preferences.remove(SERVER_IDS)
            else
                preferences[SERVER_IDS] = ids
        }
    }

    val getRemoteKey: Flow<ByteArray?> = context.dataStore.data
        .catch {
            if (it is IOException) {
                Log.e(TAG, "Error reading preferences.", it)
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map { preferences -> preferences[REMOTE_KEY] }

    suspend fun setRemoteKey(key: ByteArray?) {
            context.dataStore.edit { preferences ->
                if (key == null)
                    preferences.remove(REMOTE_KEY)
                else
                    preferences[REMOTE_KEY] = key
            }
    }
}