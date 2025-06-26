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
 * It manages various settings including:
 * - STOP_CURSOR: Determines if the cursor should be stopped.
 * - SCROLL_PAGE: Toggles the page scrolling ability.
 * - MOUSE_SPEED: Adjusts the mouse speed.
 * - SHOW_MOUSE_BUTTONS: Configures visibility of mouse buttons.
 *
 * Preferences are exposed as Kotlin Flow objects, allowing reactive observation of changes.
 * Suspend functions are provided to update these settings asynchronously.
 *
 * Usage:
 * 1. Instantiate UserSettings with an application Context.
 * 2. Collect from getStopCursor, getScrollPage, getMouseSpeed, or getShowMouseButtons to observe the preferences.
 * 3. Use setStopCursor, setScrollPage, setMouseSpeed, and setShowMouseButtons to persist changes.
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
}