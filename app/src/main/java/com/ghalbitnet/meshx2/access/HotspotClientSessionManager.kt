package com.ghalbitnet.meshx2.access

import java.util.concurrent.ConcurrentHashMap

object HotspotClientSessionManager {

    data class ClientSession(
        val clientIp: String,
        val nodeId: String?,
        val status: GatewayClientPolicy.ClientStatus,
        val detail: String,
        val lastSeen: Long,
        val accessToken: String? = null
    )

    private val sessions = ConcurrentHashMap<String, ClientSession>()

    fun upsert(
        clientIp: String,
        nodeId: String?,
        status: GatewayClientPolicy.ClientStatus,
        detail: String,
        accessToken: String? = null
    ): ClientSession {
        val session =
            ClientSession(
                clientIp = clientIp,
                nodeId = nodeId,
                status = status,
                detail = detail,
                lastSeen = System.currentTimeMillis(),
                accessToken = accessToken
            )
        sessions[clientIp] = session
        return session
    }

    fun get(clientIp: String): ClientSession? = sessions[clientIp]

    fun all(): List<ClientSession> = sessions.values.sortedByDescending { it.lastSeen }
}
