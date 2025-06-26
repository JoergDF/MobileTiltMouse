package com.example.mobiletiltmouse

import android.content.Context

/**
 * RemoteAccess orchestrates remote control functionalities by managing network discovery, connection handling,
 * mouse action execution, and network monitoring.
 *
 * This class initializes and connects the following components:
 * - Connection: Handles the underlying network connection.
 * - NetworkBrowser: Uses Android's NSD to discover services on the local network.
 * - MouseActions: Processes and forwards mouse actions.
 * - NetworkMonitor: Observes network changes and connectivity.
 *
 * Usage:
 * - Call startRemoteAccess() to initiate network browsing and begin monitoring network connectivity.
 * - Call stopRemoteAccess() to stop network monitoring and disconnect the active connection.
 * - Call restartNetwork() to reset and re-establish the network browsing process.
 *
 * @param context The application context used to initialize necessary system services.
 */
class RemoteAccess(val context: Context) {
    var mouseActions: MouseActions? = null
    var networkMonitor: NetworkMonitor? = null
    private var connection: Connection? = null
    private var nwBrowser: NetworkBrowser? = null


    init {
        connection = Connection(this)
        nwBrowser = NetworkBrowser(context, connection, this)
        mouseActions = MouseActions(context, connection)
        networkMonitor = NetworkMonitor(context)
    }

    fun startRemoteAccess() {
        nwBrowser?.startBrowsing()
        networkMonitor?.startNetworkMonitor()
    }

    fun stopRemoteAccess() {
        connection?.stopConnection()
        networkMonitor?.stopNetworkMonitor()
    }

    fun restartNetwork() {
        connection?.stopConnection()
        nwBrowser?.stopBrowsing()
        Thread.sleep(1000)
        // NsdManger's resolve listener might not be released, therefore a new instance is created
        nwBrowser = NetworkBrowser(context, connection, this)
        nwBrowser?.startBrowsing()
    }
}