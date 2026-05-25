package com.ghalbitnet.meshx2.core.network

import com.ghalbitnet.meshx2.model.MeshNode

object TransportPreference {

    enum class Mode(
        val label: String,
        val priority: Int
    ) {
        LAN_HOTSPOT("LAN / Hotspot", 0),
        DIRECT_IP("Jaringan Langsung", 1),
        NEARBY("Nearby", 2),
        UNKNOWN("Lainnya", 3)
    }

    fun modeForAddress(address: String): Mode {
        val host =
            address.trim().lowercase()

        if (host.isBlank()) {
            return Mode.UNKNOWN
        }

        if (host.startsWith("nearby:")) {
            return Mode.NEARBY
        }

        if (isPrivateIpv4(host)) {
            return Mode.LAN_HOTSPOT
        }

        if (isIpv4(host)) {
            return Mode.DIRECT_IP
        }

        return Mode.UNKNOWN
    }

    fun sortNodes(nodes: List<MeshNode>): List<MeshNode> {
        return nodes.sortedWith(
            compareBy<MeshNode> { modeForAddress(it.ipAddress).priority }
                .thenByDescending { it.online }
                .thenByDescending { it.signal }
                .thenBy { latencyRank(it.latency) }
                .thenBy { it.name }
        )
    }

    fun shouldPreferAddress(
        currentAddress: String?,
        candidateAddress: String
    ): Boolean {
        if (candidateAddress.isBlank()) {
            return false
        }

        if (currentAddress.isNullOrBlank()) {
            return true
        }

        val currentMode =
            modeForAddress(currentAddress)

        val candidateMode =
            modeForAddress(candidateAddress)

        if (candidateMode.priority != currentMode.priority) {
            return candidateMode.priority < currentMode.priority
        }

        return candidateAddress != currentAddress
    }

    private fun latencyRank(latency: Int): Int {
        return if (latency >= 0) latency else Int.MAX_VALUE
    }

    private fun isIpv4(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) {
            return false
        }

        return parts.all { part ->
            val value = part.toIntOrNull() ?: return@all false
            value in 0..255
        }
    }

    private fun isPrivateIpv4(host: String): Boolean {
        if (!isIpv4(host)) {
            return false
        }

        val parts = host.split(".").map { it.toInt() }

        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168)
    }
}
