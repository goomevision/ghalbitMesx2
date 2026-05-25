package com.ghalbitnet.meshx2.routing
import com.ghalbitnet.meshx2.model.MeshNode

object QosManager {
    fun selectBestNeighbor(destIp: String, nodes: List<MeshNode>): MeshNode? {
        return nodes.filter { it.online && it.ipAddress != destIp }
            .sortedWith(compareByDescending<MeshNode> { it.trusted }.thenBy { it.latency })
            .firstOrNull()
    }
}