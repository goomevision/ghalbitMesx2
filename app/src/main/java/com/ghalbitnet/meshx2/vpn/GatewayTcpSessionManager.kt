package com.ghalbitnet.meshx2.vpn

import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

object GatewayTcpSessionManager {

    private const val MAX_SESSIONS = 64
    private const val IDLE_TTL_MS = 120_000L

    private val sessions = ConcurrentHashMap<String, GatewayTcpSession>()

    fun sessionKey(
        clientNodeId: String,
        sessionId: String,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): String {
        return "$clientNodeId|$sessionId|$sourceAddress|$sourcePort|$destinationAddress|$destinationPort"
    }

    fun get(
        key: String
    ): GatewayTcpSession? {
        cleanupIdleSessions()
        return sessions[key]
    }

    fun create(
        clientNodeId: String,
        sessionId: String,
        gatewayNodeId: String,
        packetId: String,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
        remoteHost: String,
        socket: Socket
    ): GatewayTcpSession? {
        cleanupIdleSessions()
        if (sessions.size >= MAX_SESSIONS) {
            return null
        }
        val now = System.currentTimeMillis()
        val session =
            GatewayTcpSession(
                clientNodeId = clientNodeId,
                sessionId = sessionId,
                gatewayNodeId = gatewayNodeId,
                packetId = packetId,
                sourceAddress = sourceAddress,
                sourcePort = sourcePort,
                destinationAddress = destinationAddress,
                destinationPort = destinationPort,
                remoteHost = remoteHost,
                socket = socket,
                createdAt = now,
                lastSeen = now
            )
        sessions[session.key] = session
        return session
    }

    fun touch(
        key: String,
        packetId: String? = null
    ) {
        sessions[key]?.let { session ->
            session.lastSeen = System.currentTimeMillis()
            if (!packetId.isNullOrBlank()) {
                session.packetId = packetId
            }
        }
    }

    fun close(
        key: String
    ) {
        val removed = sessions.remove(key) ?: return
        runCatching { removed.socket.close() }
        VpnLogManager.info(
            "TCP_BRIDGE_SESSION_CLOSED",
            "Session ${removed.sessionId} untuk ${removed.destinationAddress}:${removed.destinationPort} ditutup."
        )
    }

    fun cleanupIdleSessions() {
        val now = System.currentTimeMillis()
        sessions.entries.toList().forEach { entry ->
            val expired =
                now - entry.value.lastSeen > IDLE_TTL_MS ||
                    entry.value.socket.isClosed ||
                    !entry.value.socket.isConnected
            if (expired) {
                close(entry.key)
            }
        }
    }

    fun snapshot(): List<GatewayTcpSession> {
        cleanupIdleSessions()
        return sessions.values.toList()
    }
}
