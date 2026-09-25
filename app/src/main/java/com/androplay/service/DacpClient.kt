package com.androplay.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Sends remote-control commands (play/pause, next, ...) to the sender that is streaming
 * audio, over DACP: the sender advertises "iTunes_Ctrl_<DACP-ID>" as _dacp._tcp and accepts
 * `GET /ctrl-int/1/<command>` carrying the Active-Remote token it gave us.
 */
class DacpClient(context: Context) {
    enum class Command(val path: String) {
        PLAY("play"),
        PAUSE("pause"),
        PLAY_PAUSE("playpause"),
        NEXT("nextitem"),
        PREVIOUS("previtem")
    }

    private val nsd = context.applicationContext.getSystemService(NsdManager::class.java)
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "AndroPlay-dacp") }
    private val lock = Any()

    private var dacpId: String? = null
    private var activeRemote: String? = null
    private var host: InetAddress? = null
    private var port = 0
    private var discovery: NsdManager.DiscoveryListener? = null
    private var resolving = false

    /** Called when a sender identifies itself; starts looking for its DACP server. */
    fun setSender(dacpId: String, activeRemote: String) = synchronized(lock) {
        if (dacpId == this.dacpId && activeRemote == this.activeRemote) return
        stopDiscoveryLocked()
        this.dacpId = dacpId
        this.activeRemote = activeRemote
        host = null
        port = 0
        startDiscoveryLocked()
    }

    fun clear() = synchronized(lock) {
        stopDiscoveryLocked()
        dacpId = null
        activeRemote = null
        host = null
        port = 0
    }

    fun send(command: Command) {
        val (target, targetPort, remote) = synchronized(lock) {
            val target = host
            val remote = activeRemote
            if (target == null || remote == null || port == 0) {
                Log.w(TAG, "No sender remote control available for ${command.path}")
                return
            }
            Triple(target, port, remote)
        }
        executor.execute {
            try {
                // A raw socket rather than HttpURLConnection: the app only permits cleartext
                // HTTP to localhost, and this request goes to the phone's LAN address.
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(target, targetPort), TIMEOUT_MS)
                    socket.soTimeout = TIMEOUT_MS
                    val request = buildRequest(command, remote, hostHeader(target, targetPort))
                    socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
                    val status = socket.getInputStream().bufferedReader().readLine()
                    Log.i(TAG, "${command.path} -> $status")
                }
            } catch (error: Exception) {
                Log.w(TAG, "Remote control ${command.path} failed", error)
            }
        }
    }

    private fun startDiscoveryLocked() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                val wanted = synchronized(lock) { dacpId }?.let(::serviceNameFor) ?: return
                if (info.serviceName.equals(wanted, ignoreCase = true)) resolve(info)
            }

            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "DACP discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        discovery = listener
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun resolve(info: NsdServiceInfo) {
        synchronized(lock) {
            if (resolving || host != null) return
            resolving = true
        }
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolved: NsdServiceInfo) = synchronized(lock) {
                resolving = false
                if (!resolved.serviceName.equals(dacpId?.let(::serviceNameFor), ignoreCase = true)) return
                host = resolved.host
                port = resolved.port
                Log.i(TAG, "Sender remote control at ${resolved.host}:${resolved.port}")
                stopDiscoveryLocked()
            }

            override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {
                synchronized(lock) { resolving = false }
                Log.w(TAG, "Could not resolve ${failed.serviceName}: $errorCode")
            }
        })
    }

    private fun stopDiscoveryLocked() {
        discovery?.let {
            try {
                nsd.stopServiceDiscovery(it)
            } catch (error: IllegalArgumentException) {
                // Already stopped.
            }
        }
        discovery = null
    }

    companion object {
        private const val TAG = "AndroPlayDacp"
        private const val SERVICE_TYPE = "_dacp._tcp"
        private const val TIMEOUT_MS = 3000

        fun serviceNameFor(dacpId: String) = "iTunes_Ctrl_$dacpId"

        /** HTTP Host value; IPv6 literals are bracketed and lose their zone. */
        fun hostHeader(address: InetAddress, port: Int): String {
            val literal = address.hostAddress.orEmpty().substringBefore('%')
            return if (':' in literal) "[$literal]:$port" else "$literal:$port"
        }

        fun buildRequest(command: Command, activeRemote: String, host: String) =
            "GET /ctrl-int/1/${command.path} HTTP/1.1\r\n" +
                "Host: $host\r\n" +
                "Active-Remote: $activeRemote\r\n" +
                "Connection: close\r\n\r\n"
    }
}
