package com.ghalbitnet.meshx2.vpn

import java.util.concurrent.ConcurrentHashMap

object VpnSessionTable {

    private const val SESSION_TTL_MS = 120_000L
    private const val PACKET_TTL_MS = 60_000L
    private const val MAX_PAYLOAD_BYTES = 64 * 1024

    data class Session(
        val sessionId: String,
        val gatewayId: String,
        val gatewayName: String,
        val routeMode: MeshForwardMode,
        val createdAt: Long,
        @Volatile var lastSeen: Long
    )

    enum class ReturnValidation {
        ACCEPTED,
        UNKNOWN_SESSION,
        DUPLICATE_PACKET,
        UNTRUSTED_GATEWAY,
        EMPTY_PAYLOAD,
        PAYLOAD_TOO_LARGE
    }

    private val sessions = ConcurrentHashMap<String, Session>()
    private val processedPackets = ConcurrentHashMap<String, Long>()

    fun registerOutgoingSession(
        sessionId: String,
        gatewayId: String,
        gatewayName: String,
        routeMode: MeshForwardMode
    ) {
        val now = System.currentTimeMillis()
        sessions[sessionId] =
            Session(
                sessionId = sessionId,
                gatewayId = gatewayId,
                gatewayName = gatewayName,
                routeMode = routeMode,
                createdAt = now,
                lastSeen = now
            )
    }

    fun validateReturnPacket(
        sessionId: String,
        packetId: String,
        gatewayId: String,
        payloadSize: Int
    ): ReturnValidation {
        cleanup()
        val session = sessions[sessionId] ?: return ReturnValidation.UNKNOWN_SESSION
        if (gatewayId.isNotBlank() && session.gatewayId.isNotBlank() && gatewayId != session.gatewayId) {
            return ReturnValidation.UNTRUSTED_GATEWAY
        }
        if (payloadSize <= 0) {
            return ReturnValidation.EMPTY_PAYLOAD
        }
        if (payloadSize > MAX_PAYLOAD_BYTES) {
            return ReturnValidation.PAYLOAD_TOO_LARGE
        }
        val packetKey = "$sessionId|$packetId"
        if (packetId.isNotBlank() && processedPackets.containsKey(packetKey)) {
            return ReturnValidation.DUPLICATE_PACKET
        }
        session.lastSeen = System.currentTimeMillis()
        if (packetId.isNotBlank()) {
            processedPackets[packetKey] = System.currentTimeMillis()
        }
        return ReturnValidation.ACCEPTED
    }

    fun touch(sessionId: String) {
        sessions[sessionId]?.lastSeen = System.currentTimeMillis()
    }

    fun cleanup() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { now - it.value.lastSeen > SESSION_TTL_MS }
        processedPackets.entries.removeIf { now - it.value > PACKET_TTL_MS }
    }
}
