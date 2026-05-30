package com.ghalbitnet.meshx2.core.node

import android.util.Log
import com.ghalbitnet.meshx2.model.MeshNode
import java.util.concurrent.ConcurrentHashMap

object NodeStatusManager {
    private const val TAG = "GHALBIT-CONTACT-SYNC"

    private val nodes =
        ConcurrentHashMap<String, MeshNode>()

    private val lastSeen =
        ConcurrentHashMap<String, Long>()

    fun upsertNode(
        node: MeshNode
    ) {
        val now =
            System.currentTimeMillis()

        nodes[node.name] = node.copy(
            online = true,
            lastSeen = now
        )

        lastSeen[node.name] =
            now
    }

    fun getOnlineNodes(): List<MeshNode> {
        val now =
            System.currentTimeMillis()

        return nodes.values.map { node ->
            val seen =
                lastSeen[node.name] ?: 0L

            node.copy(
                online = now - seen < 30000,
                lastSeen = seen
            )
        }
    }

    fun getKnownNodes(): List<MeshNode> {
        return nodes.values.map { node ->
            node.copy(
                online = (System.currentTimeMillis() - (lastSeen[node.name] ?: 0L)) < 30000,
                lastSeen = lastSeen[node.name] ?: node.lastSeen
            )
        }
    }

    fun onlineCount(): Int {
        return getOnlineNodes()
            .count { it.online }
    }

    fun pruneStaleNodes(
        maxAgeMs: Long = 120000L
    ): Int {
        val now =
            System.currentTimeMillis()

        val staleKeys =
            lastSeen.entries
                .filter { now - it.value > maxAgeMs }
                .map { it.key }

        staleKeys.forEach { key ->
            nodes.remove(key)
            lastSeen.remove(key)
        }

        if (staleKeys.isNotEmpty()) {
            Log.d(TAG, "Pruned ${staleKeys.size} stale nodes")
        }

        return staleKeys.size
    }

    fun report(): String {
        return buildString {
            appendLine("NODE STATUS")
            appendLine("===================")

            getOnlineNodes().forEach { node ->
                appendLine(
                    "${node.name} | ${node.ipAddress} | online=${node.online}"
                )
            }
        }
    }
}
