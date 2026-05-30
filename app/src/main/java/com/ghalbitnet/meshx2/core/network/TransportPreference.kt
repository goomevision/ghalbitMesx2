package com.ghalbitnet.meshx2.core.network

import com.ghalbitnet.meshx2.model.MeshNode

object TransportPreference {

    enum class Mode(
        val label: String,
        val priority: Int
    ) {
        LAN_HOTSPOT(
            label = "Terhubung via LAN / Hotspot.",
            priority = 0
        ),
        DIRECT_IP(
            label = "Terhubung via jaringan langsung.",
            priority = 1
        ),
        NEARBY(
            label = "LAN / Hotspot belum tersedia. Menggunakan Nearby sebagai cadangan.",
            priority = 2
        ),
        UNKNOWN(
            label = "Menggunakan jalur cadangan lain.",
            priority = 3
        )
    }

    fun modeForAddress(address: String): Mode {
        val normalized = address.trim().lowercase()

        if (normalized.isBlank()) {
            return Mode.UNKNOWN
        }

        if (normalized == "local") {
            return Mode.LAN_HOTSPOT
        }

        return when {
            normalized.startsWith("192.168.") ||
                normalized.startsWith("10.") ||
                normalized.startsWith("172.16.") ||
                normalized.startsWith("172.17.") ||
                normalized.startsWith("172.18.") ||
                normalized.startsWith("172.19.") ||
                normalized.startsWith("172.20.") ||
                normalized.startsWith("172.21.") ||
                normalized.startsWith("172.22.") ||
                normalized.startsWith("172.23.") ||
                normalized.startsWith("172.24.") ||
                normalized.startsWith("172.25.") ||
                normalized.startsWith("172.26.") ||
                normalized.startsWith("172.27.") ||
                normalized.startsWith("172.28.") ||
                normalized.startsWith("172.29.") ||
                normalized.startsWith("172.30.") ||
                normalized.startsWith("172.31.") -> Mode.LAN_HOTSPOT

            normalized.contains("nearby") ||
                normalized.contains("ble") ||
                normalized.contains("p2p") -> Mode.NEARBY

            Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(normalized) -> Mode.DIRECT_IP
            else -> Mode.UNKNOWN
        }
    }

    fun sortNodes(nodes: List<MeshNode>): List<MeshNode> {
        return nodes.sortedWith(
            compareByDescending<MeshNode> { it.online }
                .thenBy { modeForAddress(it.ipAddress).priority }
                .thenByDescending { it.gateway }
                .thenByDescending { it.relay }
                .thenByDescending { it.trusted }
                .thenByDescending { it.signal }
                .thenBy { if (it.latency >= 0) it.latency else Int.MAX_VALUE }
                .thenBy { it.name.ifBlank { it.ipAddress } }
        )
    }
}
