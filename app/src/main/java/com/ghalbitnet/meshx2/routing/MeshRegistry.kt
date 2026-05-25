package com.ghalbitnet.meshx2.routing
import com.ghalbitnet.meshx2.model.MeshNode
import java.util.concurrent.ConcurrentHashMap

object MeshRegistry {
    private val nodes = ConcurrentHashMap<String, MeshNode>()
    fun updateNode(node: MeshNode) { nodes[node.ipAddress] = node }
    fun getNodes(): List<MeshNode> = nodes.values.toList()
    fun getNode(ip: String): MeshNode? = nodes[ip]
    fun clear() = nodes.clear()
}