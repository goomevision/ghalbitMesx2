package com.ghalbitnet.meshx2.vpn

object ChecksumUtils {

    fun ipv4Checksum(header: ByteArray): Int {
        return internetChecksum(header, 0, header.size)
    }

    fun tcpChecksum(
        sourceIp: ByteArray,
        destIp: ByteArray,
        tcpSegment: ByteArray
    ): Int {
        val pseudoHeader = ByteArray(12 + tcpSegment.size)
        System.arraycopy(sourceIp, 0, pseudoHeader, 0, 4)
        System.arraycopy(destIp, 0, pseudoHeader, 4, 4)
        pseudoHeader[8] = 0
        pseudoHeader[9] = 6
        pseudoHeader[10] = ((tcpSegment.size ushr 8) and 0xFF).toByte()
        pseudoHeader[11] = (tcpSegment.size and 0xFF).toByte()
        System.arraycopy(tcpSegment, 0, pseudoHeader, 12, tcpSegment.size)
        val checksum = internetChecksum(pseudoHeader, 0, pseudoHeader.size)
        VpnLogManager.info("TCP_CHECKSUM_BUILT", "checksum tcp selesai untuk ${tcpSegment.size} byte")
        return checksum
    }

    private fun internetChecksum(
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Int {
        var sum = 0L
        var index = offset
        while (index + 1 < offset + length) {
            val word =
                ((buffer[index].toInt() and 0xFF) shl 8) or
                    (buffer[index + 1].toInt() and 0xFF)
            sum += word.toLong()
            while (sum > 0xFFFF) {
                sum = (sum and 0xFFFF) + (sum ushr 16)
            }
            index += 2
        }
        if (index < offset + length) {
            sum += ((buffer[index].toInt() and 0xFF) shl 8).toLong()
            while (sum > 0xFFFF) {
                sum = (sum and 0xFFFF) + (sum ushr 16)
            }
        }
        return sum.inv().toInt() and 0xFFFF
    }
}
