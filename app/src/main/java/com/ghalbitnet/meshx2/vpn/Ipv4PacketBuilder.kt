package com.ghalbitnet.meshx2.vpn

object Ipv4PacketBuilder {

    fun buildIpv4Packet(
        sourceIp: String,
        destIp: String,
        protocol: Int,
        payload: ByteArray
    ): ByteArray {
        val source = ipToBytes(sourceIp)
        val dest = ipToBytes(destIp)
        val header = ByteArray(20)
        val totalLength = 20 + payload.size
        header[0] = 0x45
        header[1] = 0
        header[2] = ((totalLength ushr 8) and 0xFF).toByte()
        header[3] = (totalLength and 0xFF).toByte()
        header[4] = 0
        header[5] = 0
        header[6] = 0x40
        header[7] = 0
        header[8] = 64
        header[9] = protocol.toByte()
        header[10] = 0
        header[11] = 0
        System.arraycopy(source, 0, header, 12, 4)
        System.arraycopy(dest, 0, header, 16, 4)
        val checksum = ChecksumUtils.ipv4Checksum(header)
        header[10] = ((checksum ushr 8) and 0xFF).toByte()
        header[11] = (checksum and 0xFF).toByte()
        return header + payload
    }

    fun ipToBytes(ip: String): ByteArray {
        val parts = ip.split('.')
        if (parts.size != 4) return byteArrayOf(0, 0, 0, 0)
        return ByteArray(4) { index ->
            (parts.getOrNull(index)?.toIntOrNull() ?: 0).coerceIn(0, 255).toByte()
        }
    }
}
