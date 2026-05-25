package com.ghalbitnet.meshx2.network

import java.util.concurrent.ConcurrentHashMap

object AckTracker {

    private val pending =
        ConcurrentHashMap<String, Long>()

    private val received =
        ConcurrentHashMap<String, Long>()

    fun track(
        packetId: String
    ) {
        pending[packetId] =
            System.currentTimeMillis()
    }

    fun markAckReceived(
        packetId: String
    ) {
        pending.remove(packetId)

        received[packetId] =
            System.currentTimeMillis()
    }

    fun isPending(
        packetId: String
    ): Boolean {
        return pending.containsKey(packetId)
    }

    fun pendingCount(): Int {
        return pending.size
    }

    fun receivedCount(): Int {
        return received.size
    }

    fun report(): String {
        return """
ACK TRACKER
===================
Pending ACK  : ${pending.size}
Received ACK : ${received.size}
""".trimIndent()
    }

    fun clearExpired(
        maxAgeMs: Long = 30000L
    ) {
        val now =
            System.currentTimeMillis()

        pending.entries.removeIf {
            now - it.value > maxAgeMs
        }
    }
}
