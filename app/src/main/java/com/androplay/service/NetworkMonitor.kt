package com.androplay.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.InetAddress

/** The TV's current network, as shown on the home screen. */
data class NetworkStatus(
    val type: Type = Type.NONE,
    /** Wi-Fi network name; null when not on Wi-Fi or when Android withholds it (see [NetworkMonitor]). */
    val ssid: String? = null,
    val ipv4: List<String> = emptyList()
) {
    enum class Type { WIFI, ETHERNET, OTHER, NONE }
}

/**
 * Follows the default network. Android only reveals the Wi-Fi name (SSID) to apps holding a
 * location permission, with location services on; without it [NetworkStatus.ssid] is null.
 */
class NetworkMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val wifi = appContext.getSystemService(WifiManager::class.java)

    private val _status = MutableStateFlow(NetworkStatus())
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private var capabilities: NetworkCapabilities? = null
    private var links: LinkProperties? = null

    private val callback: Callback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+ redacts the SSID in callbacks unless location info is requested.
            Callback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO)
        } else {
            Callback()
        }

    /**
     * NetworkCallback(int flags) only exists from API 31; calling it on older Android throws
     * NoSuchMethodError even with flags = 0, so the older constructor is picked separately.
     */
    private inner class Callback : ConnectivityManager.NetworkCallback {
        constructor() : super()

        @RequiresApi(Build.VERSION_CODES.S)
        constructor(flags: Int) : super(flags)

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            capabilities = caps
            publish()
        }

        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
            links = properties
            publish()
        }

        override fun onLost(network: Network) {
            capabilities = null
            links = null
            publish()
        }
    }

    private var registered = false

    fun start() {
        if (registered) return
        registered = true
        val active = connectivity.activeNetwork
        capabilities = active?.let(connectivity::getNetworkCapabilities)
        links = active?.let(connectivity::getLinkProperties)
        publish()
        connectivity.registerDefaultNetworkCallback(callback)
    }

    fun stop() {
        if (!registered) return
        registered = false
        connectivity.unregisterNetworkCallback(callback)
    }

    /** Re-reads the network, e.g. after the location permission was granted. */
    fun refresh() {
        val active = connectivity.activeNetwork
        capabilities = active?.let(connectivity::getNetworkCapabilities) ?: capabilities
        links = active?.let(connectivity::getLinkProperties) ?: links
        publish()
    }

    fun canReadSsid(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun publish() {
        val caps = capabilities
        val type = when {
            caps == null -> NetworkStatus.Type.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkStatus.Type.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus.Type.WIFI
            else -> NetworkStatus.Type.OTHER
        }
        _status.value = NetworkStatus(
            type = type,
            ssid = if (type == NetworkStatus.Type.WIFI) readSsid(caps) else null,
            ipv4 = ipv4Addresses(links?.linkAddresses.orEmpty().map { it.address })
        )
    }

    @Suppress("DEPRECATION")
    private fun readSsid(caps: NetworkCapabilities?): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cleanSsid((caps?.transportInfo as? WifiInfo)?.ssid)?.let { return it }
        }
        // Deprecated in API 31 but still the only way before it.
        return cleanSsid(wifi?.connectionInfo?.ssid)
    }

    companion object {
        /** Android quotes SSIDs and reports "<unknown ssid>" when it withholds them. */
        fun cleanSsid(raw: String?): String? {
            val value = raw?.trim() ?: return null
            if (value.isEmpty() || value == WifiManager.UNKNOWN_SSID || value == "0x") return null
            return value.removeSurrounding("\"").takeIf { it.isNotBlank() }
        }

        /** Usable IPv4 addresses: senders on the LAN reach the TV through these. */
        fun ipv4Addresses(addresses: List<InetAddress>): List<String> =
            addresses.filterIsInstance<Inet4Address>()
                .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress || it.isAnyLocalAddress }
                .mapNotNull { it.hostAddress }
                .distinct()
    }
}
