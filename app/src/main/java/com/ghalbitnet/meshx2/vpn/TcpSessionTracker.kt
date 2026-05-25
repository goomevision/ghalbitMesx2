package com.ghalbitnet.meshx2.vpn

import java.util.concurrent.ConcurrentHashMap

object TcpSessionTracker {

    private const val SESSION_TTL_MS = 120_000L
    private const val MAX_SESSIONS = 128
    private val sessions = ConcurrentHashMap<String, TcpSessionState>()

    fun createOrUpdate(state: TcpSessionState) {
        cleanup()
        if (!sessions.containsKey(state.sessionId) && sessions.size >= MAX_SESSIONS) {
            cleanup(forceTrim = true)
        }
        val previous = sessions[state.sessionId]
        sessions[state.sessionId] = state.copy(lastSeen = System.currentTimeMillis())
        if (previous == null) {
            VpnLogManager.info(
                "TCP_STATE_CREATED",
                "session=${state.sessionId} ${state.clientIp}:${state.clientPort} -> ${state.remoteIp}:${state.remotePort} state=${state.connectionState}"
            )
        } else {
            VpnLogManager.info(
                "TCP_SEQ_ACK_UPDATED",
                "session=${state.sessionId} clientSeq=${state.clientSeq} clientAck=${state.clientAck} remoteSeq=${state.remoteSeq} remoteAck=${state.remoteAck} state=${state.connectionState}"
            )
        }
    }

    fun get(sessionId: String): TcpSessionState? {
        cleanup()
        return sessions[sessionId]
    }

    fun touch(sessionId: String) {
        sessions.computeIfPresent(sessionId) { _, state ->
            state.copy(lastSeen = System.currentTimeMillis())
        }
    }

    fun updateAfterReturn(
        sessionId: String,
        payloadLength: Int
    ) {
        sessions.computeIfPresent(sessionId) { _, state ->
            val transition =
                TcpStateMachine.onGatewayPayload(
                    current = state.connectionState,
                    payloadLength = payloadLength
                )
            if (!transition.valid) {
                VpnLogManager.warn(
                    "TCP_INVALID_STATE_TRANSITION",
                    "return payload session=$sessionId state=${state.connectionState} reason=${transition.closeReason}"
                )
                state
            } else {
                if (transition.nextState != state.connectionState) {
                    logState(transition.nextState, sessionId, transition.closeReason)
                }
            state.copy(
                remoteSeq = state.remoteSeq + payloadLength,
                remoteAck = state.clientSeq,
                lastRemoteSeq = state.remoteSeq + payloadLength,
                lastRemoteAck = state.clientSeq,
                connectionState = transition.nextState,
                lastSeen = System.currentTimeMillis(),
                closeReason = transition.closeReason ?: state.closeReason
            )
            }
        }
    }

    fun markGatewayClose(
        sessionId: String,
        hardReset: Boolean
    ) {
        sessions.computeIfPresent(sessionId) { _, state ->
            val transition = TcpStateMachine.onGatewayClose(state.connectionState, hardReset)
            if (transition.valid) {
                logState(transition.nextState, sessionId, transition.closeReason)
                state.copy(
                    connectionState = transition.nextState,
                    lastSeen = System.currentTimeMillis(),
                    closeReason = transition.closeReason ?: state.closeReason
                )
            } else {
                VpnLogManager.warn(
                    "TCP_INVALID_STATE_TRANSITION",
                    "gateway close session=$sessionId state=${state.connectionState}"
                )
                state
            }
        }
    }

    fun cleanup(forceTrim: Boolean = false) {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf {
            val state = it.value
            val expired = now - state.lastSeen > SESSION_TTL_MS
            val finished =
                state.connectionState == TcpConnectionState.RESET ||
                    state.connectionState == TcpConnectionState.CLOSED ||
                    state.connectionState == TcpConnectionState.TIME_WAIT
            val trim = forceTrim && (now - state.lastSeen > 5_000L)
            if (expired || finished || trim) {
                VpnLogManager.info(
                    "TCP_SESSION_CLEANUP",
                    "session=${state.sessionId} state=${state.connectionState} reason=${state.closeReason ?: "TTL"}"
                )
                true
            } else {
                false
            }
        }
    }

    fun snapshot(): List<TcpSessionState> {
        cleanup()
        return sessions.values.sortedByDescending { it.lastSeen }
    }

    private fun logState(
        state: TcpConnectionState,
        sessionId: String,
        reason: String?
    ) {
        val event =
            when (state) {
                TcpConnectionState.SYN_SENT -> "TCP_STATE_SYN_SENT"
                TcpConnectionState.ESTABLISHED -> "TCP_STATE_ESTABLISHED"
                TcpConnectionState.FIN_WAIT -> "TCP_STATE_FIN_WAIT"
                TcpConnectionState.CLOSE_WAIT -> "TCP_STATE_CLOSE_WAIT"
                TcpConnectionState.RESET -> "TCP_STATE_RESET"
                TcpConnectionState.CLOSED,
                TcpConnectionState.TIME_WAIT -> "TCP_STATE_CLOSED"
                TcpConnectionState.SYN_RECEIVED,
                TcpConnectionState.LAST_ACK -> "TCP_STATE_UPDATED"
            }
        VpnLogManager.info(event, "session=$sessionId state=$state${reason?.let { " reason=$it" } ?: ""}")
    }
}
