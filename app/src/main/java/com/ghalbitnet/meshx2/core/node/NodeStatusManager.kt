package com.ghalbitnet.meshx2.core.node

import com.ghalbitnet.meshx2.core.network.TransportPreference
import com.ghalbitnet.meshx2.model.MeshNode
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

object NodeStatusManager {

    data class NodeActivityRecord(
        val timestamp: Long,
        val summary: String
    )

    private const val ONLINE_WINDOW_MS = 30000L
    private const val MAX_ACTIVITY_RECORDS = 6

    private val nodes =
        ConcurrentHashMap<String, MeshNode>()

    private val lastSeen =
        ConcurrentHashMap<String, Long>()

    private val recentActivity =
        ConcurrentHashMap<String, ArrayDeque<NodeActivityRecord>>()

    fun upsertNode(
        node: MeshNode
    ) {
        val now =
            System.currentTimeMillis()

        val previousNode =
            nodes[node.name]

        nodes[node.name] = node.copy(
            online = true,
            lastSeen = now
        )

        lastSeen[node.name] =
            now

        appendActivity(
            nodeName = node.name,
            record = NodeActivityRecord(
                timestamp = now,
                summary = buildActivitySummary(
                    previousNode = previousNode,
                    currentNode = node
                )
            )
        )
    }

    fun getOnlineNodes(): List<MeshNode> {
        val now =
            System.currentTimeMillis()

        return TransportPreference.sortNodes(
            nodes.values.map { node ->
                val seen =
                    lastSeen[node.name] ?: 0L

                node.copy(
                    online = now - seen < ONLINE_WINDOW_MS,
                    lastSeen = seen
                )
            }
        )
    }

    fun findNode(
        nodeName: String
    ): MeshNode? {
        val node =
            nodes[nodeName] ?: return null

        val seen =
            lastSeen[nodeName] ?: node.lastSeen

        return node.copy(
            online = System.currentTimeMillis() - seen < ONLINE_WINDOW_MS,
            lastSeen = seen
        )
    }

    fun isOnline(
        nodeName: String
    ): Boolean {
        val seen =
            lastSeen[nodeName] ?: return false

        return System.currentTimeMillis() - seen < ONLINE_WINDOW_MS
    }

    fun getLastSeen(
        nodeName: String
    ): Long {
        return lastSeen[nodeName] ?: 0L
    }

    fun getRecentActivity(
        nodeName: String
    ): List<NodeActivityRecord> {
        val deque =
            recentActivity[nodeName] ?: return emptyList()

        return synchronized(deque) {
            deque.toList()
        }.sortedByDescending { it.timestamp }
    }

    fun onlineCount(): Int {
        return getOnlineNodes()
            .count { it.online }
    }

    fun report(): String {
        return buildString {
            appendLine("NODE STATUS")
            appendLine("===================")

            getOnlineNodes().forEach { node ->
                appendLine(
                    "${node.name} | ${node.ipAddress} | ${TransportPreference.modeForAddress(node.ipAddress).label} | online=${node.online}"
                )
            }
        }
    }

    private fun appendActivity(
        nodeName: String,
        record: NodeActivityRecord
    ) {
        val deque =
            recentActivity.getOrPut(nodeName) { ArrayDeque() }

        synchronized(deque) {
            val latest =
                deque.lastOrNull()

            if (latest?.summary == record.summary) {
                deque.removeLast()
                deque.addLast(record)
            } else {
                deque.addLast(record)
            }

            while (deque.size > MAX_ACTIVITY_RECORDS) {
                deque.removeFirst()
            }
        }
    }

    private fun buildActivitySummary(
        previousNode: MeshNode?,
        currentNode: MeshNode
    ): String {
        val modeLabel =
            TransportPreference.modeForAddress(currentNode.ipAddress).label
        val roleLabel =
            when {
                currentNode.gateway && currentNode.relay -> "penyedia internet dan relay"
                currentNode.gateway -> "penyedia internet"
                currentNode.relay -> "relay"
                else -> "node biasa"
            }

        if (previousNode == null) {
            return "Node baru terlihat via $modeLabel sebagai $roleLabel."
        }

        if (previousNode.ipAddress != currentNode.ipAddress) {
            return "Jalur berubah ke $modeLabel (${currentNode.ipAddress})."
        }

        if (previousNode.gateway != currentNode.gateway) {
            return if (currentNode.gateway) {
                "Node sekarang menyediakan internet untuk mesh."
            } else {
                "Node tidak lagi terdeteksi sebagai penyedia internet."
            }
        }

        val signalDelta =
            currentNode.signal - previousNode.signal
        val latencyDelta =
            currentNode.latency - previousNode.latency

        return when {
            signalDelta >= 10 ->
                "Sinyal membaik ke ${currentNode.signal.coerceIn(0, 100)}%."

            signalDelta <= -10 ->
                "Sinyal melemah ke ${currentNode.signal.coerceIn(0, 100)}%."

            latencyDelta >= 15 ->
                "Latency naik ke ${currentNode.latency.coerceAtLeast(0)} ms."

            latencyDelta <= -15 ->
                "Latency membaik ke ${currentNode.latency.coerceAtLeast(0)} ms."

            previousNode.trusted != currentNode.trusted ->
                "Trust diperbarui ke ${currentNode.trusted.coerceIn(0, 100)}%."

            else ->
                "Aktif via $modeLabel. Sinyal ${currentNode.signal.coerceIn(0, 100)}%, latency ${currentNode.latency.coerceAtLeast(0)} ms."
        }
    }
}
