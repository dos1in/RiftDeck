package com.riftdeck.core.sharing

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** DNS-SD is advisory; all transfers independently authenticate the advertised device identity. */
@Suppress("DEPRECATION")
class NsdPeerDiscovery(context: Context, private val deviceId: String, private val deviceName: String) {
    private val manager = context.applicationContext.getSystemService(NsdManager::class.java)
    private val retries = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var generation = 0L
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val found = linkedMapOf<String, NsdServiceInfo>()
    private val resolved = linkedMapOf<String, LanPeer>()
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var activeResolveName: String? = null
    private var activeResolveListener: NsdManager.ResolveListener? = null
    private val mutablePeers = MutableStateFlow<List<LanPeer>>(emptyList())
    val peers: StateFlow<List<LanPeer>> = mutablePeers.asStateFlow()
    private val mutableUnavailable = MutableStateFlow(false)
    val unavailable: StateFlow<Boolean> = mutableUnavailable.asStateFlow()

    fun start(port: Int) {
        require(port in 1..65535)
        stop()
        synchronized(lock) {
            val token = generation
            mutableUnavailable.value = false
            val registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    synchronized(lock) {
                        if (generation != token) runCatching { manager.unregisterService(this) }
                    }
                }
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = failed(token)
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            }
            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = failed(token)
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    synchronized(lock) {
                        if (generation != token || serviceInfo.serviceType.trimEnd('.') != SERVICE_TYPE.trimEnd('.')) return
                        if (found.size >= MAX_PEERS || found.containsKey(serviceInfo.serviceName)) return
                        found[serviceInfo.serviceName] = serviceInfo
                        pending.addLast(serviceInfo)
                        resolveNext(token)
                    }
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    synchronized(lock) {
                        if (generation != token) return
                        found.remove(serviceInfo.serviceName)
                        resolved.remove(serviceInfo.serviceName)
                        pending.removeAll { it.serviceName == serviceInfo.serviceName }
                        publish()
                    }
                }
            }
            registration = registrationListener
            discovery = discoveryListener
            val service = NsdServiceInfo().apply {
                serviceName = "RiftDeck-${deviceId.take(12)}"
                serviceType = SERVICE_TYPE
                this.port = port
                setAttribute("id", deviceId)
                // TXT values are limited to 255 bytes; the authenticated handshake supplies the full name.
                setAttribute("name", deviceName.take(48))
                setAttribute("v", "1")
            }
            runCatching { manager.registerService(service, NsdManager.PROTOCOL_DNS_SD, registrationListener) }
                .onFailure { mutableUnavailable.value = true }
            runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener) }
                .onFailure { mutableUnavailable.value = true }
            scheduleRefresh(token)
        }
    }

    /** Older Android releases allow one outstanding resolution, so resolve discovered services in order. */
    private fun resolveNext(token: Long) {
        if (generation != token || resolving || pending.isEmpty()) return
        val service = pending.removeFirst()
        resolving = true
        activeResolveName = service.serviceName
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
                    // A resolution from the previous session cannot be cancelled before API 34.
                    // Keep the new generation's service available until that old callback completes.
                    retries.postDelayed({
                        synchronized(lock) {
                            if (generation == token && found.containsKey(service.serviceName)) {
                                pending.addLast(service)
                                resolveNext(token)
                            }
                        }
                    }, 1000)
                }
                finish(null)
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) = finish(serviceInfo)
            private fun finish(serviceInfo: NsdServiceInfo?) {
                synchronized(lock) {
                    if (generation != token) return
                    resolving = false
                    activeResolveName = null
                    activeResolveListener = null
                    if (serviceInfo != null && found.containsKey(service.serviceName)) {
                        val id = serviceInfo.attributes["id"]?.toString(Charsets.UTF_8).orEmpty()
                        val name = serviceInfo.attributes["name"]?.toString(Charsets.UTF_8).orEmpty()
                        val host = serviceInfo.host
                        if (id != deviceId && id.matches(Regex("[A-Za-z0-9_-]{1,80}")) &&
                            name.isNotBlank() && name.none { it.code < 32 || it.code == 127 } &&
                            serviceInfo.attributes["v"]?.toString(Charsets.UTF_8) == "1" &&
                            host != null && isLocalAddress(host) && serviceInfo.port in 1..65535
                        ) {
                            resolved[service.serviceName] = LanPeer(id, name, host.hostAddress.orEmpty(), serviceInfo.port)
                            publish()
                        }
                    }
                    resolveNext(token)
                }
            }
        }
        activeResolveListener = listener
        runCatching { manager.resolveService(service, listener) }.onFailure {
            resolving = false
            activeResolveName = null
            activeResolveListener = null
            resolveNext(token)
        }
    }

    /** Legacy one-shot resolutions become stale when DHCP changes a peer's address. */
    private fun scheduleRefresh(token: Long) {
        retries.postDelayed({
            synchronized(lock) {
                if (generation != token || discovery == null) return@synchronized
                val queuedNames = pending.map { it.serviceName }.toSet()
                found.values.filter { it.serviceName != activeResolveName && it.serviceName !in queuedNames }
                    .forEach(pending::addLast)
                resolveNext(token)
                scheduleRefresh(token)
            }
        }, 30_000)
    }

    private fun publish() {
        mutablePeers.value = resolved.values.distinctBy { it.id }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    private fun failed(token: Long) {
        synchronized(lock) { if (generation == token) mutableUnavailable.value = true }
    }

    fun stop() {
        synchronized(lock) {
            generation++
            retries.removeCallbacksAndMessages(null)
            if (Build.VERSION.SDK_INT >= 34) activeResolveListener?.let { runCatching { manager.stopServiceResolution(it) } }
            registration?.let { runCatching { manager.unregisterService(it) } }
            discovery?.let { runCatching { manager.stopServiceDiscovery(it) } }
            registration = null
            discovery = null
            found.clear()
            resolved.clear()
            pending.clear()
            resolving = false
            activeResolveName = null
            activeResolveListener = null
            mutablePeers.value = emptyList()
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_riftdeck._tcp."
        const val MAX_PEERS = 64
    }
}
