// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** Android DNS-SD discovery and lifecycle; the encrypted channel is independently testable on a JVM. */
internal class NearbyLanClient(context: Context) : Closeable {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val cpuLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WizeFiles:NearbyTransfer").apply { setReferenceCounted(false) }
    @Suppress("DEPRECATION")
    private val wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "wizefiles-nearby-transfer")
        .apply { setReferenceCounted(false) }
    private val main = Handler(Looper.getMainLooper())
    private val dispatch: (() -> Unit) -> Unit = { main.post(it) }
    private val io = Executors.newFixedThreadPool(2)
    private val generation = AtomicInteger()
    private val connecting = AtomicBoolean()
    @Volatile private var closed = false
    @Volatile private var advertisingGeneration = 0
    @Volatile private var server: SSLServerSocket? = null
    @Volatile private var pendingSocket: SSLSocket? = null
    @Volatile private var active: NearbyLanConnection? = null
    @Volatile private var activeId: String? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicast: WifiManager.MulticastLock? = null
    private data class Endpoint(val address: InetSocketAddress, val network: Network?)
    private val endpoints = mutableMapOf<String, Endpoint>()
    private val pendingResolutions = ArrayDeque<NsdServiceInfo>()
    private val visible = mutableSetOf<String>()
    private var resolving = false
    private var discoveryEvents: NearbyDiscoveryCallback? = null

    fun startAdvertising(name: String, serviceId: String, callback: NearbyConnectionCallback): NearbyAction {
        val action = action()
        val epoch = ++advertisingGeneration
        val session = generation.get()
        io.execute {
            try {
                check(serviceId == NEARBY_SERVICE_ID && !closed)
                val identity = NearbyTlsIdentity.create()
                val listener = identity.context.serverSocketFactory.createServerSocket(0, 1) as SSLServerSocket
                if (epoch != advertisingGeneration || closed) { listener.close(); return@execute }
                server = listener
                main.post {
                    if (epoch != advertisingGeneration || closed) return@post
                    val info = NsdServiceInfo().apply {
                        serviceName = "WizeFiles-${UUID.randomUUID().toString().take(8)}"
                        serviceType = SERVICE_TYPE
                        port = listener.localPort
                        setAttribute("name", name.take(64))
                    }
                    val registrationCallback = object : NsdManager.RegistrationListener {
                        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                            if (epoch == advertisingGeneration && !closed) action.succeed()
                            else runCatching { nsd.unregisterService(this) }
                        }
                        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            main.post {
                                if (epoch == advertisingGeneration) {
                                    stopAdvertising()
                                    action.fail(IOException("Local network visibility failed ($errorCode)"))
                                }
                            }
                        }
                        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
                        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    }
                    registration = registrationCallback
                    runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationCallback) }
                        .onFailure { stopAdvertising(); action.fail(asException(it)) }
                }
                while (epoch == advertisingGeneration && !closed) {
                    val socket = listener.accept() as SSLSocket
                    if (active != null || !connecting.compareAndSet(false, true)) { socket.close(); continue }
                    pendingSocket = socket
                    io.execute {
                        try { pair(socket, true, name, "peer-${UUID.randomUUID()}", identity.certificate.encoded, session, callback) }
                        catch (_: Exception) { /* Unverified network probes do not end visibility. */ }
                        finally { pendingSocket = null; connecting.set(false) }
                    }
                }
            } catch (error: Exception) {
                if (epoch == advertisingGeneration && !closed) action.fail(error)
            }
        }
        return action
    }

    fun startDiscovery(serviceId: String, callback: NearbyDiscoveryCallback): NearbyAction {
        val action = action()
        if (closed || serviceId != NEARBY_SERVICE_ID) { action.fail(IOException("Transfer is unavailable")); return action }
        stopDiscovery()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {
                main.post { if (discovery === this) action.succeed() }
            }
            override fun onStartDiscoveryFailed(type: String, code: Int) {
                main.post {
                    if (discovery === this) { stopDiscovery(); action.fail(IOException("Local network discovery failed ($code)")) }
                }
            }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                main.post {
                    if (discovery !== this || info.serviceType.trimEnd('.') != SERVICE_TYPE.trimEnd('.') || visible.size >= 64) return@post
                    if (visible.add(info.serviceName)) { pendingResolutions.add(info); resolveNext(this, callback) }
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                main.post {
                    if (discovery !== this) return@post
                    visible.remove(info.serviceName)
                    endpoints.remove(info.serviceName)
                    callback.onEndpointLost(info.serviceName)
                }
            }
        }
        discovery = listener
        discoveryEvents = callback
        try {
            multicast = wifi.createMulticastLock("wizefiles-nearby-discovery").apply { setReferenceCounted(false); acquire() }
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (error: Exception) { stopDiscovery(); action.fail(error) }
        return action
    }

    @Suppress("DEPRECATION") // Serial resolution supports Android 11–13 as well as newer releases.
    private fun resolveNext(owner: NsdManager.DiscoveryListener, callback: NearbyDiscoveryCallback) {
        if (resolving || discovery !== owner || pendingResolutions.isEmpty()) return
        resolving = true
        val info = pendingResolutions.removeFirst()
        val listener = object : NsdManager.ResolveListener {
            private fun finished(resolved: NsdServiceInfo?) {
                main.post {
                    resolving = false
                    if (discovery !== owner) {
                        discovery?.let { next -> discoveryEvents?.let { resolveNext(next, it) } }
                        return@post
                    }
                    val addresses = resolved?.let {
                        if (Build.VERSION.SDK_INT >= 34) it.hostAddresses else listOfNotNull(it.host)
                    }.orEmpty().filter(::isLocalAddress)
                    val host = addresses.firstOrNull { it.address.size == 4 } ?: addresses.firstOrNull()
                    if (resolved != null && host != null && visible.contains(info.serviceName) && resolved.port in 1..65535) {
                        val network = if (Build.VERSION.SDK_INT >= 33) resolved.network else legacyNetwork(host)
                        endpoints[info.serviceName] = Endpoint(InetSocketAddress(host, resolved.port), network)
                        val name = resolved.attributes["name"]?.takeIf { it.size <= 256 }?.toString(Charsets.UTF_8)
                            ?.take(64)?.takeIf(String::isNotBlank) ?: info.serviceName
                        callback.onEndpointFound(info.serviceName, NearbyDiscoveryInfo(NEARBY_SERVICE_ID, name))
                    }
                    resolveNext(owner, callback)
                }
            }
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = finished(null)
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) = finished(serviceInfo)
        }
        try { nsd.resolveService(info, listener) } catch (_: Exception) {
            resolving = false
            resolveNext(owner, callback)
        }
    }

    fun requestConnection(name: String, id: String, callback: NearbyConnectionCallback): NearbyAction {
        val action = action()
        val address = endpoints[id]
        if (address == null || active != null || !connecting.compareAndSet(false, true)) {
            action.fail(IOException("Device is unavailable or a connection is already pending"))
            return action
        }
        val session = generation.get()
        io.execute {
            try {
                val socket = NearbyTlsIdentity.pairingContext().socketFactory.createSocket() as SSLSocket
                pendingSocket = socket
                // A local-only Wi-Fi network may not be Android's default internet network.
                address.network?.bindSocket(socket)
                socket.connect(address.address, 10_000)
                pair(socket, false, name, id, null, session, callback)
                action.succeed()
            } catch (error: Exception) {
                runCatching { pendingSocket?.close() }
                action.fail(error)
            } finally { pendingSocket = null; connecting.set(false) }
        }
        return action
    }

    private fun pair(
        socket: SSLSocket, serverSide: Boolean, name: String, id: String, certificate: ByteArray?,
        session: Int, callback: NearbyConnectionCallback
    ) {
        val connectionDispatch: (() -> Unit) -> Unit = { task ->
            main.post { if (session == generation.get() && activeId == id && !closed) task() }
        }
        val connection = NearbyLanConnection.pair(socket, serverSide, name, certificate, connectionDispatch,
            ready = { if (session == generation.get() && activeId == id) callback.onConnectionResult(id, NearbyConnectionResult(true)) },
            disconnected = {
                if (session == generation.get() && activeId == id) {
                    active = null
                    activeId = null
                    releaseTransferLocks()
                    callback.onDisconnected(id)
                }
            })
        main.post {
            if (session != generation.get() || closed || active != null) { connection.close(); return@post }
            activeId = id
            active = connection
            try {
                setTransferPaused(false)
            } catch (_: Exception) {
                disconnectFromEndpoint(id)
                callback.onConnectionResult(id, NearbyConnectionResult(false))
                return@post
            }
            callback.onConnectionInitiated(id, NearbyConnectionInfo(connection.peerName, connection.authenticationToken.copyOf()))
            connection.start(id)
        }
    }

    fun acceptConnection(id: String, callback: NearbyPayloadCallback): NearbyAction =
        active?.takeIf { activeId == id }?.accept(callback) ?: unavailable()

    fun sendPayload(id: String, payload: NearbyPayload): NearbyAction =
        active?.takeIf { activeId == id }?.send(payload) ?: unavailable()

    fun cancelPayload(id: Long) { active?.cancelPayload(id) }
    fun rejectConnection(id: String) = disconnectFromEndpoint(id)
    fun disconnectFromEndpoint(id: String) {
        if (activeId == id) { activeId = null; active?.close(); active = null; releaseTransferLocks() }
    }

    fun stopAdvertising() {
        ++advertisingGeneration
        runCatching { server?.close() }
        server = null
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
    }

    fun stopDiscovery() {
        val previous = discovery
        discovery = null
        discoveryEvents = null
        previous?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        multicast?.let { if (it.isHeld) it.release() }
        multicast = null
        endpoints.clear()
        visible.clear()
        pendingResolutions.clear()
        // An outstanding legacy resolve cannot be canceled; its callback releases this flag.
    }

    fun stopAllEndpoints() {
        generation.incrementAndGet()
        activeId = null
        active?.close()
        active = null
        releaseTransferLocks()
        runCatching { pendingSocket?.close() }
    }

    override fun close() {
        closed = true
        stopAdvertising()
        stopDiscovery()
        stopAllEndpoints()
        io.shutdownNow()
    }

    @Suppress("DEPRECATION")
    private fun legacyNetwork(peer: InetAddress): Network? = connectivity.allNetworks.firstOrNull { network ->
        val capabilities = connectivity.getNetworkCapabilities(network)
        (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) &&
            connectivity.getLinkProperties(network)?.routes?.any {
                it.destination.prefixLength > 0 && it.destination.contains(peer)
            } == true
    }

    fun setTransferPaused(paused: Boolean) {
        if (paused) releaseTransferLocks()
        else if (active != null) {
            if (!cpuLock.isHeld) cpuLock.acquire()
            if (wifi.isWifiEnabled && !wifiLock.isHeld) wifiLock.acquire()
        }
    }

    private fun releaseTransferLocks() {
        if (wifiLock.isHeld) wifiLock.release()
        if (cpuLock.isHeld) cpuLock.release()
    }

    private fun action(): NearbyAction {
        val session = generation.get()
        return NearbyAction { task -> main.post { if (session == generation.get() && !closed) task() } }
    }

    private fun unavailable() = action().apply { fail(IOException("Peer disconnected")) }

    companion object {
        private const val SERVICE_TYPE = "_wizefiles2._tcp."
        private fun asException(error: Throwable) = error as? Exception ?: IOException(error)
        private fun isLocalAddress(address: InetAddress): Boolean = !address.isLoopbackAddress &&
            (address.isSiteLocalAddress || address.isLinkLocalAddress ||
                (address.address.size == 16 && (address.address[0].toInt() and 0xfe) == 0xfc))
    }
}
