package com.example.mobiletiltmouse

import android.content.Context

/**
 * Coordinates remote control functionalities by managing network discovery,
 * device pairing, connection handling, mouse action execution, and network monitoring.
 *
 * @param context The application context used to initialize necessary system services.
 */
class RemoteAccess(val context: Context, userSettings: UserSettings) {
    var networkMonitor = NetworkMonitor(context)
    var mouseActions   = MouseActions(context)
    var pairing        = Pairing(mouseActions, userSettings)
    private var connection: Connection? = null
    private var nwBrowser: NetworkBrowser? = null

    init {
        connection = Connection(context, this, pairing)
        nwBrowser = NetworkBrowser(context, connection, this)
    }

    fun startRemoteAccess() {
        nwBrowser?.startBrowsing()
        networkMonitor.startNetworkMonitor()
    }

    fun stopRemoteAccess() {
        connection?.stopConnection()
        networkMonitor.stopNetworkMonitor()
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