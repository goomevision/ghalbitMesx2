package com.ghalbitnet.meshx2.ui

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.call.CallState
import com.ghalbitnet.meshx2.chat.ConversationKeepAliveManager
import com.ghalbitnet.meshx2.chat.RouteHealthStatus
import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager
import com.ghalbitnet.meshx2.routing.RouteStateReconciler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class RuntimeUiSnapshot(
    val state: RuntimeUiState,
    val title: String,
    val detail: String,
    val actionsLocked: Boolean,
    val source: String,
    val updatedAt: Long = System.currentTimeMillis()
)

object RuntimeUiStateManager {
    private const val MIN_EMIT_INTERVAL_MS = 280L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _stateFlow =
        MutableStateFlow(
            RuntimeUiSnapshot(
                state = RuntimeUiState.IDLE,
                title = "Runtime siaga",
                detail = "Jaringan belum diaktifkan.",
                actionsLocked = false,
                source = "initial"
            )
        )
    val stateFlow: StateFlow<RuntimeUiSnapshot> = _stateFlow

    @Volatile
    private var bound = false
    private var appContext: Context? = null
    private val transientStates = linkedMapOf<String, RuntimeUiSnapshot>()
    private var lastPublishedAt = 0L

    fun bind(context: Context) {
        appContext = context.applicationContext
        if (bound) {
            syncNow()
            return
        }
        bound = true
        scope.launch {
            MeshRuntimeManager.aliveNodes.collectLatest {
                publishDerivedState()
            }
        }
        scope.launch {
            MeshRuntimeManager.runtimeStatus.collectLatest {
                publishDerivedState()
            }
        }
        scope.launch {
            ConversationKeepAliveManager.stateFlow.collectLatest {
                publishDerivedState()
            }
        }
        syncNow()
    }

    fun current(): RuntimeUiSnapshot = _stateFlow.value

    fun syncNow() {
        publishDerivedState()
    }

    fun setTransientState(
        source: String,
        state: RuntimeUiState,
        title: String,
        detail: String,
        actionsLocked: Boolean = defaultLock(state)
    ) {
        val reconciled = reconcilePendingState(source, state, title, detail, actionsLocked)
        transientStates[source] =
            RuntimeUiSnapshot(
                state = reconciled.state,
                title = reconciled.title,
                detail = reconciled.detail,
                actionsLocked = reconciled.actionsLocked,
                source = source
            )
        Log.d("GHALBIT-UI-STATE", "source=$source state=${reconciled.state} detail=${reconciled.detail}")
        publishDerivedState()
    }

    fun clearTransientState(source: String) {
        transientStates.remove(source)
        publishDerivedState()
    }

    fun onCallState(state: CallState, detail: String) {
        when (state) {
            CallState.IDLE -> clearTransientState("call")
            CallState.OUTGOING -> setTransientState("call", RuntimeUiState.PREPARING, "Mempersiapkan komunikasi...", detail)
            CallState.RINGING,
            CallState.INCOMING -> setTransientState("call", RuntimeUiState.VERIFYING, "Memverifikasi identitas peer...", detail)
            CallState.ACCEPT_CLICKED,
            CallState.SIGNALING_ACCEPT,
            CallState.ROUTE_READY,
            CallState.WAITING_FOR_AUDIO_PATH,
            CallState.VOICE_HANDSHAKING,
            CallState.VOICE_TRANSPORT_READY,
            CallState.CALL_CONNECTED_SIGNAL_ONLY -> setTransientState("call", RuntimeUiState.CONNECTING, "Menyiapkan jalur suara...", detail)
            CallState.WAITING_FOR_ROUTE,
            CallState.RELAY_NOT_CONFIGURED,
            CallState.RELAY_UNREACHABLE,
            CallState.MESH_STALE -> setTransientState("call", RuntimeUiState.OFFLINE_PENDING, "Menunggu jalur suara...", detail, actionsLocked = false)
            CallState.PTT_FALLBACK -> setTransientState("call", RuntimeUiState.WEAK_SIGNAL, "PTT fallback aktif", detail, actionsLocked = false)
            CallState.VOICE_STREAM_ACTIVE,
            CallState.CONNECTED -> setTransientState("call", RuntimeUiState.READY, "Jalur suara aktif", "Sambungan suara tetap dijaga.", actionsLocked = false)
            CallState.ENDED,
            CallState.CALL_ENDED,
            CallState.REJECTED,
            CallState.MISSED -> clearTransientState("call")
            CallState.FAILED,
            CallState.CALL_FAILED -> setTransientState("call", RuntimeUiState.RECONNECTING, "Menjaga sambungan tetap aktif...", detail)
        }
    }

    fun onRouteHealth(routeHealthStatus: RouteHealthStatus, detail: String) {
        when (routeHealthStatus) {
            RouteHealthStatus.STABLE -> setTransientState("route", RuntimeUiState.READY, "Terhubung melalui jaringan lokal", detail, actionsLocked = false)
            RouteHealthStatus.NEARBY_DETECTED -> setTransientState("route", RuntimeUiState.READY, "Perangkat dekat terdeteksi", detail, actionsLocked = false)
            RouteHealthStatus.PROBING_ROUTE -> setTransientState("route", RuntimeUiState.CONNECTING, "Menguji jalur suara", detail, actionsLocked = false)
            RouteHealthStatus.VOICE_PROBE_READY,
            RouteHealthStatus.LOCAL_VOICE_READY -> setTransientState("route", RuntimeUiState.READY, "Suara lokal siap", detail, actionsLocked = false)
            RouteHealthStatus.LOCAL_VOICE_UNSTABLE,
            RouteHealthStatus.ROUTE_DEMOTED_AFTER_CONFIRMATION -> setTransientState("route", RuntimeUiState.WEAK_SIGNAL, "Jalur suara tidak stabil", detail, actionsLocked = false)
            RouteHealthStatus.WEAK -> setTransientState("route", RuntimeUiState.WEAK_SIGNAL, "Sinyal jaringan sedang lemah", detail, actionsLocked = false)
            RouteHealthStatus.RECONNECTING -> setTransientState("route", RuntimeUiState.RECONNECTING, "Menyambungkan ulang...", detail)
            RouteHealthStatus.INTERNET_FALLBACK -> setTransientState("route", RuntimeUiState.INTERNET_FALLBACK, "Menggunakan jalur internet", detail, actionsLocked = false)
            RouteHealthStatus.OFFLINE_PENDING -> setTransientState("route", RuntimeUiState.OFFLINE_PENDING, "Menunggu koneksi tersedia", detail, actionsLocked = false)
        }
    }

    fun onSosSending(active: Boolean) {
        if (active) {
            setTransientState(
                source = "sos",
                state = RuntimeUiState.SYNCING,
                title = "Menyebarkan sinyal darurat...",
                detail = "Sistem sedang meneruskan SOS ke jalur yang tersedia."
            )
        } else {
            clearTransientState("sos")
        }
    }

    private fun publishDerivedState() {
        val transient = transientStates.values.maxByOrNull { it.updatedAt }
        val snapshot = transient ?: deriveBaseState()
        val current = _stateFlow.value
        val now = System.currentTimeMillis()
        val visuallySame =
            current.state == snapshot.state &&
                current.title == snapshot.title &&
                current.detail == snapshot.detail &&
                current.actionsLocked == snapshot.actionsLocked &&
                current.source == snapshot.source
        if (visuallySame && now - lastPublishedAt < MIN_EMIT_INTERVAL_MS) {
            Log.d("GHALBIT-UX-PERF", "runtime ui throttled state=${snapshot.state}")
            return
        }
        if (!visuallySame) {
            val emitted = snapshot.copy(updatedAt = now)
            _stateFlow.value = emitted
            lastPublishedAt = now
            Log.d("GHALBIT-UI-STATE", "state=${emitted.state} source=${emitted.source} detail=${emitted.detail}")
        }
    }

    private fun deriveBaseState(): RuntimeUiSnapshot {
        val runtimeRunning = MeshRuntimeManager.isRunning
        val aliveNodes = MeshRuntimeManager.aliveNodes.value
        val keepAliveStates = ConversationKeepAliveManager.stateFlow.value.values.toList()
        val activeKeepAlive = keepAliveStates.firstOrNull()
        val activeRouteSearch = RouteStateReconciler.activeGlobalState()

        val state =
            when {
                !runtimeRunning -> RuntimeUiState.IDLE
                MeshRuntimeManager.isUdpListenerActive().not() || MeshRuntimeManager.isSocketServerActive().not() -> RuntimeUiState.PREPARING
                activeRouteSearch != null -> RouteStateReconciler.runtimeState(activeRouteSearch)
                activeKeepAlive?.routeHealth == RouteHealthStatus.RECONNECTING -> RuntimeUiState.RECONNECTING
                activeKeepAlive?.routeHealth == RouteHealthStatus.PROBING_ROUTE -> RuntimeUiState.CONNECTING
                activeKeepAlive?.routeHealth == RouteHealthStatus.NEARBY_DETECTED -> RuntimeUiState.READY
                activeKeepAlive?.routeHealth == RouteHealthStatus.VOICE_PROBE_READY -> RuntimeUiState.READY
                activeKeepAlive?.routeHealth == RouteHealthStatus.LOCAL_VOICE_READY -> RuntimeUiState.READY
                activeKeepAlive?.routeHealth == RouteHealthStatus.LOCAL_VOICE_UNSTABLE -> RuntimeUiState.WEAK_SIGNAL
                activeKeepAlive?.routeHealth == RouteHealthStatus.ROUTE_DEMOTED_AFTER_CONFIRMATION -> RuntimeUiState.WEAK_SIGNAL
                activeKeepAlive?.routeHealth == RouteHealthStatus.WEAK -> RuntimeUiState.WEAK_SIGNAL
                activeKeepAlive?.routeHealth == RouteHealthStatus.INTERNET_FALLBACK -> RuntimeUiState.INTERNET_FALLBACK
                activeKeepAlive?.routeHealth == RouteHealthStatus.OFFLINE_PENDING -> RuntimeUiState.OFFLINE_PENDING
                aliveNodes > 0 -> RuntimeUiState.READY
                else -> RuntimeUiState.DISCOVERING
            }

        val detail =
            when (state) {
                RuntimeUiState.IDLE -> "Runtime menunggu diaktifkan."
                RuntimeUiState.PREPARING -> "Menyiapkan listener, socket, dan heartbeat."
                RuntimeUiState.DISCOVERING -> activeRouteSearch?.label ?: "Mencari jalur terbaik..."
                RuntimeUiState.CONNECTING -> activeRouteSearch?.label ?: "Membangun jalur komunikasi..."
                RuntimeUiState.VERIFYING -> "Memverifikasi identitas peer..."
                RuntimeUiState.SYNCING -> "Sinkronisasi jaringan sedang berjalan."
                RuntimeUiState.RECONNECTING -> "Menjaga sambungan tetap aktif..."
                RuntimeUiState.READY -> if (aliveNodes > 0) "Terhubung melalui jaringan lokal." else "Runtime siap."
                RuntimeUiState.WEAK_SIGNAL -> "Jalur ada, tetapi kualitasnya sedang turun."
                RuntimeUiState.INTERNET_FALLBACK -> "Menggunakan jalur internet."
                RuntimeUiState.OFFLINE_PENDING -> "Menunggu koneksi tersedia."
                RuntimeUiState.FAILED -> MeshRuntimeManager.lastErrorSummary.ifBlank { "Runtime gagal menjaga jalur." }
            }

        return RuntimeUiSnapshot(
            state = state,
            title = stateTitle(state),
            detail = detail,
            actionsLocked = defaultLock(state),
            source = "runtime"
        )
    }

    private fun stateTitle(state: RuntimeUiState): String {
        return when (state) {
            RuntimeUiState.IDLE -> "Runtime siaga"
            RuntimeUiState.PREPARING -> "Menyiapkan jaringan"
            RuntimeUiState.DISCOVERING -> "Mencari node"
            RuntimeUiState.CONNECTING -> "Membangun jalur"
            RuntimeUiState.VERIFYING -> "Memverifikasi peer"
            RuntimeUiState.SYNCING -> "Sinkronisasi aktif"
            RuntimeUiState.RECONNECTING -> "Menyambungkan ulang"
            RuntimeUiState.READY -> "Jaringan siap"
            RuntimeUiState.WEAK_SIGNAL -> "Sinyal lemah"
            RuntimeUiState.INTERNET_FALLBACK -> "Fallback internet"
            RuntimeUiState.OFFLINE_PENDING -> "Menunggu koneksi"
            RuntimeUiState.FAILED -> "Jalur terganggu"
        }
    }

    private fun defaultLock(state: RuntimeUiState): Boolean {
        return state in setOf(
            RuntimeUiState.PREPARING,
            RuntimeUiState.CONNECTING,
            RuntimeUiState.VERIFYING,
            RuntimeUiState.SYNCING
        )
    }

    private fun reconcilePendingState(
        source: String,
        state: RuntimeUiState,
        title: String,
        detail: String,
        actionsLocked: Boolean
    ): RuntimeUiSnapshot {
        if (state != RuntimeUiState.OFFLINE_PENDING) {
            return RuntimeUiSnapshot(state, title, detail, actionsLocked, source)
        }
        val key = source.substringAfterLast(':', source)
        val active = RouteStateReconciler.current(chatId = key, globalId = key) ?: RouteStateReconciler.activeGlobalState()
        if (active == null || !RouteStateReconciler.shouldSuppressPending(key, key)) {
            return RuntimeUiSnapshot(state, title, detail, actionsLocked, source)
        }
        val protectedState = RouteStateReconciler.runtimeState(active)
        return RuntimeUiSnapshot(
            state = protectedState,
            title = stateTitle(protectedState),
            detail = active.label,
            actionsLocked = defaultLock(protectedState),
            source = source
        )
    }
}
