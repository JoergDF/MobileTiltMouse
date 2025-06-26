package com.example.mobiletiltmouse

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

private const val TAG = "NetworkMonitor"

/**
 * NetworkMonitor class for monitoring the network connectivity status, specifically WiFi.
 *
 * This class utilizes the ConnectivityManager to track changes in network availability
 * and provides methods to start and stop monitoring, as well as check if WiFi is currently enabled.
 *
 * @param context The application context. Required to access system services.
 */
class NetworkMonitor(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkRequest: NetworkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            NetworkState.isWifiEnabled = true
            Log.d(TAG, "Network available")
        }

        override fun onLost(network: Network) {
            NetworkState.isWifiEnabled = false
            Log.d(TAG, "Network lost")
        }
    }

    fun startNetworkMonitor() {
        Log.d(TAG, "Network monitor start")
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    fun stopNetworkMonitor() {
        Log.d(TAG, "Network monitor stop")
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    fun wifiEnabled(): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val isWifiEnabled = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        Log.d(TAG, "Wifi enabled: $isWifiEnabled")
        return isWifiEnabled
    }
}