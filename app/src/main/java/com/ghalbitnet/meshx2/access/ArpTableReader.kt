package com.ghalbitnet.meshx2.access

import java.io.File

object ArpTableReader {

    data class ArpEntry(
        val ipAddress: String,
        val macAddress: String?,
        val device: String?
    )

    fun read(): List<ArpEntry> {
        val arpFile = File("/proc/net/arp")
        if (!arpFile.exists()) return emptyList()
        return runCatching {
            arpFile.useLines { lines ->
                lines.drop(1)
                    .mapNotNull { line ->
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size < 6) return@mapNotNull null
                        val ipAddress = parts[0]
                        val macAddress =
                            parts[3].takeIf {
                                it.isNotBlank() && it != "00:00:00:00:00:00"
                            }
                        val device = parts[5].takeIf { it.isNotBlank() }
                        ArpEntry(ipAddress, macAddress, device)
                    }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }
}
