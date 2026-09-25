package com.androplay.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import com.androplay.util.Log
import java.io.IOException
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.Locale

/**
 * TXT key/value pairs for `_airplay._tcp` and `_raop._tcp`.
 * They must be identical to what the protocol core returns from /info and uses
 * in its handshake, so they normally come from [NativeBridge.discoveryRecords].
 */
data class DiscoveryRecords(
    val airplay: Map<String, String>,
    val raop: Map<String, String>
) {
    companion object {
        /** Mirror of RPiPlay's lib/dnssdint.h, used only when the native library is missing. */
        val FALLBACK = DiscoveryRecords(
            airplay = linkedMapOf(
                "features" to "0x5A7FFEE6",
                "flags" to "0x4",
                "model" to "AppleTV2,1",
                "pk" to "b07727d6f6cd6e08b58ede525ec3cdeaa252ad9f683feb212ef8a205246554e7",
                "pi" to "2e388006-13ba-4041-9a67-25dd4a43d536",
                "srcvers" to "220.68",
                "vv" to "2"
            ),
            raop = linkedMapOf(
                "ch" to "2",
                "cn" to "0,1,2,3",
                "da" to "true",
                "et" to "0,3,5",
                "vv" to "2",
                "ft" to "0x5A7FFEE6",
                "am" to "AppleTV2,1",
                "md" to "0,1,2",
                "rhd" to "5.6.0.0",
                "pw" to "false",
                "sr" to "44100",
                "ss" to "16",
                "sv" to "false",
                "tp" to "UDP",
                "txtvers" to "1",
                "sf" to "0x4",
                "vs" to "220.68",
                "vn" to "65537",
                "pk" to "b07727d6f6cd6e08b58ede525ec3cdeaa252ad9f683feb212ef8a205246554e7"
            )
        )
    }
}

/** Publishes AirPlay and RAOP DNS-SD records for the active protocol listener. */
class AirPlayDiscoveryAdvertiser(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferences = appContext.getSharedPreferences("airplay_identity", Context.MODE_PRIVATE)

    private var socket: ServerSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val registrations = mutableListOf<NsdManager.RegistrationListener>()
    private val registeredTypes = mutableSetOf<String>()
    private var running = false
    private var onReady: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    /** Uses [protocolPort] when native AirPlay is active, otherwise opens a discovery-only probe. */
    fun start(
        name: String,
        protocolPort: Int? = null,
        records: DiscoveryRecords = DiscoveryRecords.FALLBACK,
        onReady: () -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (running) return true
        this.onReady = onReady
        this.onError = onError
        val displayName = name.trim().ifBlank { "AndroPlay" }.take(60)
        return try {
            val listenerSocket = protocolPort?.let { null } ?: ServerSocket(0)
            socket = listenerSocket
            running = true
            listenerSocket?.let {
                Thread({ acceptAndCloseConnections(it) }, "AirPlay-discovery-probe").apply {
                    isDaemon = true
                    start()
                }
            }
            val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("AndroPlay-mDNS").apply {
                setReferenceCounted(false)
                acquire()
            }

            val deviceId = deviceId()
            val advertisedPort = protocolPort ?: requireNotNull(listenerSocket).localPort
            // Same layout as RPiPlay's dnssd_register_airplay / dnssd_register_raop.
            register("_airplay._tcp", displayName, advertisedPort,
                linkedMapOf("deviceid" to deviceId) + records.airplay)
            register("_raop._tcp", "${deviceId.replace(":", "")}@$displayName", advertisedPort,
                records.raop)
            true
        } catch (error: Exception) {
            Log.e(TAG, "Could not advertise AirPlay discovery", error)
            stop()
            false
        }
    }

    fun stop() {
        running = false
        registrations.forEach { listener ->
            try {
                nsdManager.unregisterService(listener)
            } catch (error: IllegalArgumentException) {
                Log.d(TAG, "mDNS registration was not active", error)
            }
        }
        registrations.clear()
        registeredTypes.clear()
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
        socket?.close()
        socket = null
        onReady = null
        onError = null
    }

    private fun register(type: String, name: String, port: Int, attributes: Map<String, String>) {
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = type
            this.port = port
            attributes.forEach { (key, value) -> setAttribute(key, value) }
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                mainHandler.post {
                    if (!running) return@post
                    Log.i(TAG, "Registered $type as ${serviceInfo.serviceName} on port $port")
                    registeredTypes.add(type)
                    if (registeredTypes.size == 2) onReady?.invoke()
                }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                mainHandler.post {
                    if (!running) return@post
                    val callback = onError
                    stop()
                    callback?.invoke("Local network discovery failed ($type, code $errorCode)")
                }
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Could not unregister $type: $errorCode")
            }
        }
        registrations.add(listener)
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun acceptAndCloseConnections(listenerSocket: ServerSocket) {
        while (!listenerSocket.isClosed) {
            try {
                listenerSocket.accept().close()
            } catch (_: IOException) {
                break
            }
        }
    }

    private fun deviceId(): String {
        preferences.getString("device_id", null)?.let { return it }
        val bytes = ByteArray(6).also(SecureRandom()::nextBytes)
        bytes[0] = ((bytes[0].toInt() and 0xFE) or 0x02).toByte()
        val id = bytes.joinToString(":") { String.format(Locale.US, "%02X", it.toInt() and 0xFF) }
        preferences.edit().putString("device_id", id).apply()
        return id
    }

    fun hardwareAddress(): ByteArray = deviceId().split(":")
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private companion object {
        const val TAG = "AirPlayDiscovery"
    }
}
