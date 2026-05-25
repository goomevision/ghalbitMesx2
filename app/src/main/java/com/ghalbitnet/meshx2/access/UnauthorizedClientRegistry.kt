package com.ghalbitnet.meshx2.access

import java.util.concurrent.ConcurrentHashMap

object UnauthorizedClientRegistry {

    data class ClientRecord(
        val ipAddress: String,
        val status: NetworkAccessPolicy.AuthStatus,
        val nodeId: String?,
        val macAddress: String?,
        val deviceName: String?,
        val detail: String,
        val firstSeen: Long,
        val lastSeen: Long
    )

    private val clients = ConcurrentHashMap<String, ClientRecord>()

    fun touchUnauthorized(ipAddress: String, detail: String): ClientRecord {
        val existing = clients[ipAddress]
        val record =
            ClientRecord(
                ipAddress = ipAddress,
                status = NetworkAccessPolicy.AuthStatus.UNAUTHORIZED,
                nodeId = null,
                macAddress = existing?.macAddress,
                deviceName = existing?.deviceName,
                detail = detail,
                firstSeen = existing?.firstSeen ?: System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis()
            )
        clients[ipAddress] = record
        return record
    }

    fun markSilentClient(
        ipAddress: String,
        macAddress: String?,
        deviceName: String?,
        detail: String
    ): ClientRecord {
        val existing = clients[ipAddress]
        val record =
            ClientRecord(
                ipAddress = ipAddress,
                status = NetworkAccessPolicy.AuthStatus.UNKNOWN_NO_HELLO_AUTH,
                nodeId = existing?.nodeId,
                macAddress = macAddress ?: existing?.macAddress,
                deviceName = deviceName ?: existing?.deviceName,
                detail = detail,
                firstSeen = existing?.firstSeen ?: System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis()
            )
        clients[ipAddress] = record
        return record
    }

    fun markPending(ipAddress: String, nodeId: String, detail: String): ClientRecord {
        val existing = clients[ipAddress]
        val record =
            ClientRecord(
                ipAddress = ipAddress,
                status = NetworkAccessPolicy.AuthStatus.AUTH_PENDING,
                nodeId = nodeId,
                macAddress = existing?.macAddress,
                deviceName = existing?.deviceName,
                detail = detail,
                firstSeen = existing?.firstSeen ?: System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis()
            )
        clients[ipAddress] = record
        return record
    }

    fun markAuthorized(ipAddress: String, nodeId: String, detail: String): ClientRecord {
        val existing = clients[ipAddress]
        val record =
            ClientRecord(
                ipAddress = ipAddress,
                status = NetworkAccessPolicy.AuthStatus.AUTHORIZED,
                nodeId = nodeId,
                macAddress = existing?.macAddress,
                deviceName = existing?.deviceName,
                detail = detail,
                firstSeen = existing?.firstSeen ?: System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis()
            )
        clients[ipAddress] = record
        return record
    }

    fun markStatus(ipAddress: String, status: NetworkAccessPolicy.AuthStatus, nodeId: String?, detail: String): ClientRecord {
        val existing = clients[ipAddress]
        val record =
            ClientRecord(
                ipAddress = ipAddress,
                status = status,
                nodeId = nodeId,
                macAddress = existing?.macAddress,
                deviceName = existing?.deviceName,
                detail = detail,
                firstSeen = existing?.firstSeen ?: System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis()
            )
        clients[ipAddress] = record
        return record
    }

    fun snapshot(ipAddress: String): ClientRecord? = clients[ipAddress]

    fun all(): List<ClientRecord> = clients.values.sortedByDescending { it.lastSeen }
}
