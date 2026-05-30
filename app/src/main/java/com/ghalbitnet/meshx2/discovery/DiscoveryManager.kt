package com.ghalbitnet.meshx2.discovery
import com.ghalbitnet.meshx2.model.MeshNode
object DiscoveryManager {
    private val nodes = mutableMapOf<String, MeshNode>()

    fun addNode(node: MeshNode) {
        synchronized(nodes) {
            nodes[node.ipAddress] =
                node.copy(
                    online = true,
                    lastSeen = System.currentTimeMillis()
                )
        }
    }

    fun addNodes(list: List<MeshNode>) = list.forEach { addNode(it) }

    fun discoverNodes(): List<MeshNode> {
        val now =
            System.currentTimeMillis()

        return synchronized(nodes) {
            nodes.values.map { node ->
                node.copy(
                    online = now - node.lastSeen < 30000
                )
            }
        }
    }

    fun clear() = nodes.clear()
}
