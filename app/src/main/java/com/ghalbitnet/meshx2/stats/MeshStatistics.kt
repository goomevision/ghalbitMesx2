package com.ghalbitnet.meshx2.stats

object MeshStatistics {

    var packetsSent = 0
        private set

    var packetsReceived = 0
        private set

    var packetsForwarded = 0
        private set

    var onlineNodes = 0
        private set

    var lastPacketType = "-"
        private set

    var lastNode = "-"
        private set

    fun sentPacket(
        type: String = "UNKNOWN"
    ) {
        packetsSent++
        lastPacketType = type
    }

    fun receivedPacket(
        type: String = "UNKNOWN",
        node: String = "-"
    ) {
        packetsReceived++
        lastPacketType = type
        lastNode = node
    }

    fun forwardedPacket(
        type: String = "FORWARD"
    ) {
        packetsForwarded++
        lastPacketType = type
    }

    fun updateOnlineNodes(
        count: Int
    ) {
        onlineNodes = count
    }

    fun report(): String {
        return """
GHALBIT MESH STATISTICS
=======================
Online Nodes      : $onlineNodes
Packets Sent      : $packetsSent
Packets Received  : $packetsReceived
Packets Forwarded : $packetsForwarded
Last Packet       : $lastPacketType
Last Node         : $lastNode
""".trimIndent()
    }

    fun reset() {
        packetsSent = 0
        packetsReceived = 0
        packetsForwarded = 0
        onlineNodes = 0
        lastPacketType = "-"
        lastNode = "-"
    }
}
