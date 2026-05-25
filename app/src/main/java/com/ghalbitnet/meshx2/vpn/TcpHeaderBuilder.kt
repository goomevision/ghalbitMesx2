package com.ghalbitnet.meshx2.vpn

object TcpHeaderBuilder {

    const val FLAG_FIN = 0x01
    const val FLAG_SYN = 0x02
    const val FLAG_RST = 0x04
    const val FLAG_PSH = 0x08
    const val FLAG_ACK = 0x10

    fun buildTcpHeader(
        sourcePort: Int,
        destPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        window: Int,
        payload: ByteArray,
        sourceIp: ByteArray,
        destIp: ByteArray
    ): ByteArray {
        val header = ByteArray(20)
        header[0] = ((sourcePort ushr 8) and 0xFF).toByte()
        header[1] = (sourcePort and 0xFF).toByte()
        header[2] = ((destPort ushr 8) and 0xFF).toByte()
        header[3] = (destPort and 0xFF).toByte()
        writeInt(header, 4, seq)
        writeInt(header, 8, ack)
        header[12] = (5 shl 4).toByte()
        header[13] = (flags and 0x3F).toByte()
        header[14] = ((window ushr 8) and 0xFF).toByte()
        header[15] = (window and 0xFF).toByte()
        header[16] = 0
        header[17] = 0
        header[18] = 0
        header[19] = 0

        val segment = header + payload
        val checksum = ChecksumUtils.tcpChecksum(sourceIp, destIp, segment)
        header[16] = ((checksum ushr 8) and 0xFF).toByte()
        header[17] = (checksum and 0xFF).toByte()
        return header
    }

    private fun writeInt(
        target: ByteArray,
        offset: Int,
        value: Long
    ) {
        target[offset] = ((value ushr 24) and 0xFF).toByte()
        target[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 3] = (value and 0xFF).toByte()
    }
}
