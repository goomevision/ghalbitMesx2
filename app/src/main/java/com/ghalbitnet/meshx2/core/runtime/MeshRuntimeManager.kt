package com.ghalbitnet.meshx2.core.runtime

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.activityfeed.ActivityFeedManager
import com.ghalbitnet.meshx2.activityfeed.ActivityFeedType
import com.ghalbitnet.meshx2.call.CallManager
import com.ghalbitnet.meshx2.chat.LiveContactSync
import com.ghalbitnet.meshx2.chat.LiveContactItem
import com.ghalbitnet.meshx2.core.log.MeshLogger
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.discovery.PeerDiscoveryHandler
import com.ghalbitnet.meshx2.discovery.UdpDiscovery
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.util.LogThrottle
import com.ghalbitnet.meshx2.routing.IntelligentRouteMemory
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.sos.SosAlertManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object MeshRuntimeManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContext: Context
    private var initialized = false
    private var heartbeatRunning = false
    private var discoveryLoopRunning = false
    private var listenerRunning = false
    private var socketRunning = false
    private lateinit var keyStore: KeyStoreManager
    private lateinit var discoveryHandler: PeerDiscoveryHandler
    private var localPeerId: String = ""
    private var lastRuntimeFeedKey: String = ""
    private var lastRuntimeFeedAt: Long = 0L

    private val _runtimeStatus = MutableStateFlow("IDLE")
    val runtimeStatus: StateFlow<String> = _runtimeStatus
    private val _aliveNodes = MutableStateFlow(0)
    val aliveNodes: StateFlow<Int> = _aliveNodes
    private val _contactRoster = MutableStateFlow<List<LiveContactItem>>(emptyList())
    val contactRoster: StateFlow<List<LiveContactItem>> = _contactRoster
    private val _routeUpdates = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val routeUpdates: SharedFlow<String> = _routeUpdates
    private val transportStates = ConcurrentHashMap<String, Boolean>()

    @Volatile
    var startedAt: Long = 0L
        private set

    @Volatile
    var lastRestartReason: String = "Belum ada restart"
        private set

    @Volatile
    var lastWarning: String = ""
        private set

    @Volatile
    var lastErrorSummary: String = ""
        private set

    @Volatile
    var lastPacketSummary: String = ""
        private set

    @Volatile
    var lastRouteUpdate: String = ""
        private set

    @Volatile
    var isRunning: Boolean = false
        private set

    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        ActivityFeedManager.bind(appContext)
        keyStore = KeyStoreManager(appContext)
        discoveryHandler = PeerDiscoveryHandler(appContext, keyStore)
        SosAlertManager.initialize(appContext)
        initialized = true
        publishRuntimeEvent(
            title = "Runtime diinisialisasi",
            message = "MeshRuntimeManager siap mengelola discovery, heartbeat, roster, dan transport.",
            metadata = "{\"state\":\"initialized\"}"
        )
        Log.d("GHALBIT-RUNTIME-LIFECYCLE", "initialized")
    }

    fun configureLocalPeer(peerId: String) {
        localPeerId = peerId
        if (!initialized) return
        UdpDiscovery.init(keyStore)
        UdpDiscovery.setLocalIdentity(peerId, keyStore.publicKeyBase64)
        publishRuntimeEvent(
            type = ActivityFeedType.PEER_VERIFIED,
            title = "Peer lokal siap",
            message = "Node lokal dikonfigurasi sebagai $peerId.",
            peerId = peerId,
            metadata = "{\"localPeerId\":\"$peerId\"}"
        )
        Log.d("GHALBIT-RUNTIME", "configured peerId=$peerId")
    }

    fun start() {
        if (!initialized || isRunning) {
            Log.d("GHALBIT-RUNTIME", "start skipped initialized=$initialized running=$isRunning")
            publishRuntimeEvent(
                title = "Start runtime dilewati",
                message = "initialized=$initialized, running=$isRunning.",
                metadata = "{\"initialized\":$initialized,\"running\":$isRunning}"
            )
            return
        }
        isRunning = true
        startedAt = System.currentTimeMillis()
        if (_runtimeStatus.value == "STOPPED") {
            lastRestartReason = "Runtime dimulai ulang"
        }
        _runtimeStatus.value = "RUNNING"
        MeshRuntimeState.markStarted()
        markTransportState("UDP Listener", true)
        markTransportState("UDP Broadcast", true)
        startHeartbeat()
        startDiscoveryLoop()
        startDiscoveryListener()
        refreshContactRoster()
        publishRuntimeEvent(
            type = ActivityFeedType.SYNC_SUCCESS,
            title = "Runtime berjalan",
            message = "Heartbeat, discovery loop, dan UDP listener mulai aktif.",
            metadata = "{\"state\":\"RUNNING\",\"localPeerId\":\"$localPeerId\"}"
        )
        logRuntimeVerification("runtimeStarted")
        Log.d("GHALBIT-RUNTIME-LIFECYCLE", "started")
    }

    fun stop() {
        isRunning = false
        heartbeatRunning = false
        discoveryLoopRunning = false
        listenerRunning = false
        socketRunning = false
        markTransportState("UDP Listener", false)
        markTransportState("UDP Broadcast", false)
        markTransportState("Socket Server", false)
        MeshRuntimeState.markStopped()
        _runtimeStatus.value = "STOPPED"
        publishRuntimeEvent(
            title = "Runtime berhenti",
            message = "Heartbeat, discovery, UDP listener, dan socket server ditandai tidak aktif.",
            metadata = "{\"state\":\"STOPPED\"}"
        )
        Log.d("GHALBIT-RUNTIME-LIFECYCLE", "stopped")
    }

    fun onPacketProcessed() {
        MeshRuntimeState.heartbeat()
        lastPacketSummary = "Packet diproses pada ${System.currentTimeMillis()}"
    }

    fun recordDiscovery(node: MeshNode) {
        DiscoveryManager.addNode(node)
        MeshRuntimeState.updateNodeCount(NodeStatusManager.onlineCount())
        refreshContactRoster()
        val route = "${node.name}@${node.ipAddress}"
        _routeUpdates.tryEmit(route)
        lastRouteUpdate = route
        publishRuntimeEvent(
            type = if (node.online) ActivityFeedType.NODE_ONLINE else ActivityFeedType.NODE_OFFLINE,
            title = if (node.online) "Node online" else "Node offline",
            message = "${node.name} terdeteksi di ${node.ipAddress}.",
            peerId = node.id,
            metadata = "{\"name\":\"${node.name}\",\"ip\":\"${node.ipAddress}\",\"online\":${node.online}}",
            minIntervalMs = 5_000L
        )
        Log.d("GHALBIT-CONTACT-STATE", "node=${node.name} online=${node.online} ip=${node.ipAddress}")
    }

    fun refreshContactRoster() {
        if (!initialized) return
        val roster = LiveContactSync.build(appContext)
        _contactRoster.value = roster
        _aliveNodes.value = roster.count { it.isLive }
        MeshRuntimeState.updateNodeCount(_aliveNodes.value)
        LogThrottle.d(
            "GHALBIT-CONTACT-OBSERVER",
            "roster:${roster.size}:live:${_aliveNodes.value}",
            "roster=${roster.size} live=${_aliveNodes.value}",
            if (_aliveNodes.value == 0) 10_000L else 3_000L,
            appContext
        )
    }

    private fun startHeartbeat() {
        if (heartbeatRunning) return
        heartbeatRunning = true
        publishRuntimeEvent(
            title = "Heartbeat aktif",
            message = "Runtime heartbeat mulai memantau node hidup.",
            metadata = "{\"transport\":\"heartbeat\",\"active\":true}"
        )
        scope.launch {
            while (isRunning && heartbeatRunning) {
                try {
                    val onlineNodes = NodeStatusManager.getOnlineNodes().count { it.online }
                    _aliveNodes.value = onlineNodes
                    MeshRuntimeState.updateNodeCount(_aliveNodes.value)
                    MeshRuntimeState.heartbeat()
                    refreshContactRoster()
                    if (LogThrottle.shouldLog("heartbeat:$onlineNodes", if (onlineNodes == 0) 10_000L else 3_000L, appContext)) {
                        MeshLogger.i("HEARTBEAT", "alive nodes=$onlineNodes")
                        Log.d("GHALBIT-RUNTIME", "heartbeat alive=$onlineNodes")
                    }
                    delay(10000)
                } catch (cancelled: CancellationException) {
                    Log.d("GHALBIT-RUNTIME", "heartbeat cancelled")
                    break
                } catch (e: Exception) {
                    lastErrorSummary = e.message ?: "Heartbeat error"
                    MeshRuntimeState.setError(lastErrorSummary)
                    publishRuntimeEvent(
                        type = ActivityFeedType.SYNC_FAILED,
                        title = "Heartbeat error",
                        message = lastErrorSummary,
                        metadata = "{\"source\":\"heartbeat\"}"
                    )
                    Log.e("GHALBIT-RUNTIME", "heartbeat failed", e)
                    delay(5000)
                }
            }
        }
    }

    private fun startDiscoveryLoop() {
        if (discoveryLoopRunning) return
        discoveryLoopRunning = true
        publishRuntimeEvent(
            title = "Discovery loop aktif",
            message = "Node lokal mulai broadcast identitas mesh secara berkala.",
            metadata = "{\"transport\":\"udpBroadcast\",\"active\":true}"
        )
        scope.launch {
            while (isRunning && discoveryLoopRunning) {
                try {
                    if (localPeerId.isNotBlank()) {
                        UdpDiscovery.broadcastNode(localPeerId, gateway = false, relay = true)
                    }
                    delay(10000)
                } catch (cancelled: CancellationException) {
                    Log.d("GHALBIT-RUNTIME", "discovery loop cancelled")
                    break
                } catch (e: Exception) {
                    lastWarning = e.message ?: "Discovery loop issue"
                    publishRuntimeEvent(
                        type = ActivityFeedType.SYNC_FAILED,
                        title = "Discovery loop terganggu",
                        message = lastWarning,
                        metadata = "{\"source\":\"discoveryLoop\"}"
                    )
                    Log.e("GHALBIT-RUNTIME", "discovery loop failed", e)
                    delay(5000)
                }
            }
        }
    }

    private fun startDiscoveryListener() {
        if (listenerRunning) return
        listenerRunning = true
        markTransportState("UDP Listener", true)
        publishRuntimeEvent(
            title = "UDP listener aktif",
            message = "Runtime siap menerima paket discovery dari node lain.",
            metadata = "{\"transport\":\"udpListener\",\"active\":true}"
        )
        UdpDiscovery.listen { packet ->
            try {
                val result =
                    discoveryHandler.handleDiscoveredNode(
                        peerId = packet.sourceNodeId,
                        ipAddress = packet.sourceIp,
                        publicKey = packet.publicKey,
                        gateway = packet.gateway,
                        relay = packet.relay,
                        sourceGlobalId = packet.sourceGlobalId,
                        sourcePublicKeyHash = packet.sourcePublicKeyHash
                    )
                recordDiscovery(result.discoveredNode)
                Log.d("GHALBIT-RUNTIME", "discovered peer=${packet.sourceNodeId} ip=${packet.sourceIp}")
            } catch (cancelled: CancellationException) {
                Log.d("GHALBIT-RUNTIME", "listener cancelled")
            } catch (e: Exception) {
                lastErrorSummary = e.message ?: "Listener failed"
                publishRuntimeEvent(
                    type = ActivityFeedType.SYNC_FAILED,
                    title = "UDP listener error",
                    message = lastErrorSummary,
                    metadata = "{\"source\":\"udpListener\"}"
                )
                Log.e("GHALBIT-RUNTIME", "listener failed", e)
            }
        }
    }

    fun markSocketServerActive(active: Boolean) {
        socketRunning = active
        markTransportState("Socket Server", active)
    }

    fun markTransportState(name: String, active: Boolean) {
        val previous = transportStates[name]
        transportStates[name] = active
        if (previous == active) return
        publishRuntimeEvent(
            title = if (active) "$name aktif" else "$name tidak aktif",
            message = if (active) "Transport $name siap digunakan." else "Transport $name berhenti atau belum siap.",
            metadata = "{\"transport\":\"$name\",\"active\":$active}",
            minIntervalMs = 2_000L
        )
    }

    fun recordWarning(message: String) {
        lastWarning = message
        publishRuntimeEvent(
            title = "Runtime warning",
            message = message,
            metadata = "{\"level\":\"warning\"}"
        )
    }

    fun recordError(message: String, throwable: Throwable? = null) {
        lastErrorSummary = listOfNotNull(message, throwable?.javaClass?.simpleName).joinToString(" | ")
        MeshRuntimeState.setError(lastErrorSummary)
        publishRuntimeEvent(
            type = ActivityFeedType.SYNC_FAILED,
            title = "Runtime error",
            message = lastErrorSummary,
            metadata = "{\"level\":\"error\"}"
        )
    }

    fun recordPacketSummary(summary: String) {
        lastPacketSummary = summary
    }

    fun runtimeUptimeMs(now: Long = System.currentTimeMillis()): Long {
        return if (startedAt > 0L && isRunning) {
            (now - startedAt).coerceAtLeast(0L)
        } else {
            0L
        }
    }

    fun activeTransports(): List<String> {
        return transportStates.entries
            .filter { it.value }
            .map { it.key }
            .sorted()
    }

    fun isHeartbeatActive(): Boolean = heartbeatRunning && isRunning

    fun isUdpListenerActive(): Boolean = listenerRunning && isRunning

    fun isSocketServerActive(): Boolean = socketRunning && isRunning

    fun localNodeId(): String = localPeerId

    fun localGlobalId(): String {
        return GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)
    }

    fun localPublicKeyHash(): String {
        val publicKey = keyStore.publicKeyBase64
        if (publicKey.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun logRuntimeVerification(reason: String = "manual") {
        if (!initialized) {
            Log.d("GHALBIT-RUNTIME-VERIFY", "reason=$reason meshRuntimeRunning=false initialized=false")
            return
        }

        val contacts = contactRoster.value.ifEmpty { LiveContactSync.build(appContext) }
        val localNodeId = localNodeId()
        val localGlobalId = localGlobalId()
        val localPublicKeyHash = localPublicKeyHash()
        val nearbyActive = transportStates["Nearby"] == true
        val wifiDirectActive = transportStates["WiFi Direct"] == true

        publishRuntimeEvent(
            title = "Runtime verification",
            message = "reason=$reason, running=$isRunning, alive=${aliveNodes.value}, contacts=${contacts.count { it.isLive }}.",
            metadata = "{\"reason\":\"$reason\",\"running\":$isRunning,\"udpListener\":${isUdpListenerActive()},\"socketServer\":${isSocketServerActive()},\"heartbeat\":${isHeartbeatActive()},\"nearby\":$nearbyActive,\"wifiDirect\":$wifiDirectActive,\"localNodeId\":\"$localNodeId\",\"globalId\":\"$localGlobalId\",\"publicKeyHash\":\"$localPublicKeyHash\",\"aliveNodes\":${aliveNodes.value},\"liveContacts\":${contacts.count { it.isLive }},\"routes\":${IntelligentRouteMemory.getAllHints(appContext).size}}",
            minIntervalMs = 30_000L
        )

        Log.d("GHALBIT-RUNTIME-VERIFY", "reason=$reason appStarted")
        Log.d("GHALBIT-RUNTIME-VERIFY", "meshRuntimeRunning=$isRunning")
        Log.d("GHALBIT-RUNTIME-VERIFY", "udpListener=${isUdpListenerActive()}")
        Log.d("GHALBIT-RUNTIME-VERIFY", "socketServer=${isSocketServerActive()}")
        Log.d("GHALBIT-RUNTIME-VERIFY", "heartbeat=${isHeartbeatActive()}")
        Log.d("GHALBIT-RUNTIME-VERIFY", "nearby=$nearbyActive")
        Log.d("GHALBIT-RUNTIME-VERIFY", "wifiDirect=$wifiDirectActive")
        Log.d("GHALBIT-RUNTIME-VERIFY", "localNodeId=$localNodeId")
        Log.d("GHALBIT-RUNTIME-VERIFY", "globalId=$localGlobalId")
        Log.d("GHALBIT-RUNTIME-VERIFY", "publicKeyHash=$localPublicKeyHash")
        Log.d(
            "GHALBIT-RUNTIME-VERIFY",
            "aliveNodes=${aliveNodes.value} liveContacts=${contacts.count { it.isLive }} routes=${IntelligentRouteMemory.getAllHints(appContext).size} callTx=${CallManager.audioTxCount()} callRx=${CallManager.audioRxCount()}"
        )
    }

    private fun publishRuntimeEvent(
        type: ActivityFeedType = ActivityFeedType.RUNTIME_EVENT,
        title: String,
        message: String,
        peerId: String? = null,
        metadata: String? = null,
        minIntervalMs: Long = 1_000L
    ) {
        if (!initialized) return
        val now = System.currentTimeMillis()
        val key = "$type|$title|$message|$peerId"
        if (key == lastRuntimeFeedKey && now - lastRuntimeFeedAt < minIntervalMs) {
            return
        }
        lastRuntimeFeedKey = key
        lastRuntimeFeedAt = now
        ActivityFeedManager.publish(
            type = type,
            title = title,
            message = message,
            peerId = peerId,
            source = "MeshRuntimeManager",
            metadata = metadata
        )
    }
}
