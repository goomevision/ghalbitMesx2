package com.ghalbitnet.meshx2.vpn

object PacketUsageParser {

    data class ParsedPacket(
        val ipVersion: Int,
        val protocol: String,
        val sourceIp: String?,
        val sourcePort: Int?,
        val destinationIp: String?,
        val destinationPort: Int?,
        val packetBytes: Int
    )

    fun parse(packet: ByteArray): ParsedPacket {
        if (packet.isEmpty()) {
            return ParsedPacket(
                ipVersion = 0,
                protocol = "UNKNOWN",
                sourceIp = null,
                sourcePort = null,
                destinationIp = null,
                destinationPort = null,
                packetBytes = 0
            )
        }
        val version = (packet[0].toInt() ushr 4) and 0x0F
        return when (version) {
            4 -> parseIpv4(packet)
            6 -> ParsedPacket(
                ipVersion = 6,
                protocol = "IPV6",
                sourceIp = null,
                sourcePort = null,
                destinationIp = null,
                destinationPort = null,
                packetBytes = packet.size
            )
            else -> ParsedPacket(
                ipVersion = version,
                protocol = "UNKNOWN",
                sourceIp = null,
                sourcePort = null,
                destinationIp = null,
                destinationPort = null,
                packetBytes = packet.size
            )
        }
    }

    private fun parseIpv4(packet: ByteArray): ParsedPacket {
        if (packet.size < 20) {
            return ParsedPacket(4, "UNKNOWN", null, null, null, null, packet.size)
        }
        val ihlBytes = (packet[0].toInt() and 0x0F) * 4
        val protocolNumber = packet[9].toInt() and 0xFF
        val protocol =
            when (protocolNumber) {
                6 -> "TCP"
                17 -> "UDP"
                1 -> "ICMP"
                else -> "UNKNOWN"
            }
        val sourceIp =
            "${packet[12].toInt() and 0xFF}.${packet[13].toInt() and 0xFF}.${packet[14].toInt() and 0xFF}.${packet[15].toInt() and 0xFF}"
        val destinationIp =
            "${packet[16].toInt() and 0xFF}.${packet[17].toInt() and 0xFF}.${packet[18].toInt() and 0xFF}.${packet[19].toInt() and 0xFF}"
        val hasPorts = (protocol == "TCP" || protocol == "UDP") && packet.size >= ihlBytes + 4
        val sourcePort =
            if (hasPorts) {
                ((packet[ihlBytes].toInt() and 0xFF) shl 8) or
                    (packet[ihlBytes + 1].toInt() and 0xFF)
            } else {
                null
            }
        val destinationPort =
            if (hasPorts) {
                ((packet[ihlBytes + 2].toInt() and 0xFF) shl 8) or
                    (packet[ihlBytes + 3].toInt() and 0xFF)
            } else {
                null
            }
        return ParsedPacket(
            ipVersion = 4,
            protocol = protocol,
            sourceIp = sourceIp,
            sourcePort = sourcePort,
            destinationIp = destinationIp,
            destinationPort = destinationPort,
            packetBytes = packet.size
        )
    }
}
