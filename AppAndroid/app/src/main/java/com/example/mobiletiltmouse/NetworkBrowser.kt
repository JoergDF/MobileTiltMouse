package com.example.mobiletiltmouse


import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresExtension


private const val TAG = "NetworkBrowser"
const val SERVICE_TYPE = "_mobiletiltmouse._udp."


/**
 * NetworkBrowser handles the discovery and resolution of network services on the local network using Network Service Discovery (NSD).
 * It leverages the device's WifiManager to acquire a multicast lock that's required on API level 29 and above to detect
 * network services correctly.
 *
 * Key Features:
 * - Initiates a search for services that match the specified SERVICE_TYPE.
 * - Automatically resolves discovered services to retrieve their host address and port information.
 * - If a valid connection interface is provided, attempts to establish a connection using the resolved service details.
 * - Cleans up resources by releasing the multicast lock and stopping service discovery after a connection is established
 *   or if an error occurs.
 *
 * Usage:
 * 1. Instantiate the NetworkBrowser with an application Context and an optional Connection object.
 * 2. Invoke startBrowsing() to begin service discovery.
 * 3. The class will automatically resolve found services and, if applicable, use the Connection to establish a connection.
 * 4. Call stopBrowsing() when discovery is no longer needed to release system resources.
 *
 * Important:
 * - Requires the ACCESS_WIFI_STATE and CHANGE_WIFI_MULTICAST_STATE permissions to function properly.
 * - Ensure that stopBrowsing() is called to prevent battery drain and resource leaks.
 *
 * @param context The application context for accessing system services.
 * @param connection An optional [Connection] object that is used to initiate a connection to a discovered service.
 * @param remoteAccess An optional [RemoteAccess] instance to manage restart of this network browsing.
 */
class NetworkBrowser(context: Context, val connection: Connection?, val remoteAccess: RemoteAccess?) {

    // API 29 requires multicast for success in detecting services
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val multicastLock: WifiManager.MulticastLock = wifiManager.createMulticastLock("NetworkBrowserMulticastLock")

    val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Service discovery listener started")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d(TAG, "Service discovery success: $service")

            if (service.serviceType == SERVICE_TYPE) {
                try {
                    nsdManager.resolveService(service, resolveListener)
                } catch (e: Exception) {
                    Log.e(TAG, "Error resolving service: ${e.message}")
                    remoteAccess?.restartNetwork()
                }
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.e(TAG, "Service lost: $service")
            remoteAccess?.restartNetwork()
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "Discovery stopped: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed: Error code: $errorCode")
            remoteAccess?.restartNetwork()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed: Error code: $errorCode")
            remoteAccess?.restartNetwork()
        }
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e(TAG, "Resolve failed: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Resolve Succeeded: $serviceInfo")

            if (serviceInfo.host?.hostAddress != null) {
                try {
                    // connected to found server
                    connection?.startConnection(
                        "${serviceInfo.host.hostAddress}:${serviceInfo.port}"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error start connection: $e")
                   return
                }

                // save battery and stop browsing
                Log.d(TAG, "Network browser stop (intentionally)")
                stopBrowsing()
            } else {
                Log.e(TAG, "Resolve failed to start connection: Host address is null")
                remoteAccess?.restartNetwork()
            }
        }
    }

    fun startBrowsing() {
        if (multicastLock.isHeld) {
            Log.d(TAG, "Service discovery already started")
            return
        }
        Log.d(TAG, "Service discovery start")
        multicastLock.acquire()
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }


    fun stopBrowsing() {
        Log.d(TAG, "Service discovery stop")
        try {
            if (multicastLock.isHeld) {
                nsdManager.stopServiceDiscovery(discoveryListener)
                multicastLock.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stop service discovery: ${e.message}")
        }
    }

}
