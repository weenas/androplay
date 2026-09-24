package com.androplay.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

/** A discovery-only mDNS advertisement until the AirPlay protocol backend is packaged. */
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

    /** Opens a real TCP port before publishing it in DNS-SD. No AirPlay requests are processed. */
    fun start(name: String, onReady: () -> Unit, onError: (String) -> Unit): Boolean {
        if (running) return true
        this.onReady = onReady
        this.onError = onError
        val displayName = name.trim().ifBlank { "AndroPlay" }.take(60)
        return try {
            val listenerSocket = ServerSocket(0)
            socket = listenerSocket
            running = true
            Thread({ acceptAndCloseConnections(listenerSocket) }, "AirPlay-discovery-probe").apply {
                isDaemon = true
                start()
            }
            val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("AndroPlay-mDNS").apply {
                setReferenceCounted(false)
                acquire()
            }

            val deviceId = deviceId()
            val pairingId = preferences.getString("pairing_id", null) ?: UUID.randomUUID().toString().also {
                preferences.edit().putString("pairing_id", it).apply()
            }
            register("_airplay._tcp", displayName, listenerSocket.localPort, mapOf(
                "deviceid" to deviceId,
                "features" to FEATURES,
                "flags" to "0x4",
                "model" to MODEL,
                "pi" to pairingId,
                "pw" to "false",
                "srcvers" to SOURCE_VERSION,
                "vv" to "2"
            ))
            register("_raop._tcp", "${deviceId.replace(":", "")}@$displayName", listenerSocket.localPort, mapOf(
                "txtvers" to "1",
                "ch" to "2",
                "cn" to "0,1,2,3",
                "da" to "true",
                "et" to "0,3,5",
                "ft" to FEATURES,
                "am" to MODEL,
                "md" to "0,1,2",
                "pw" to "false",
                "sf" to "0x4",
                "sr" to "44100",
                "ss" to "16",
                "sv" to "false",
                "tp" to "UDP",
                "vn" to "65537",
                "vs" to SOURCE_VERSION
            ))
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

    private companion object {
        const val TAG = "AirPlayDiscovery"
        // Match UxPlay's legacy-mirroring discovery identity for sender compatibility.
        const val FEATURES = "0x5A7FFEE6,0x0"
        const val MODEL = "AppleTV3,2"
        const val SOURCE_VERSION = "220.68"
    }
}
