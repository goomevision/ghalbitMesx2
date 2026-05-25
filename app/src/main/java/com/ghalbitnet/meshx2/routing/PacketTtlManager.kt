package com.ghalbitnet.meshx2.routing

import com.ghalbitnet.meshx2.model.MeshPacket

/**
 * =====================================================
 * PACKET TTL MANAGER
 * =====================================================
 *
 * Untuk mencegah packet berputar tanpa batas.
 *
 * Format payload internal:
 * TTL|isi_pesan
 *
 * FUTURE:
 * TTL sebaiknya dipindah ke field khusus MeshPacket.
 */

object PacketTtlManager {

    private const val DEFAULT_TTL = 5

    fun attachTtl(
        payload: String,
        ttl: Int = DEFAULT_TTL
    ): String {
        if (payload.startsWith("TTL:")) {
            return payload
        }

        return "TTL:$ttl|$payload"
    }

    fun extractTtl(
        payload: String
    ): Int {
        return try {
            if (!payload.startsWith("TTL:")) {
                DEFAULT_TTL
            } else {
                payload.substringAfter("TTL:")
                    .substringBefore("|")
                    .toInt()
            }
        } catch (_: Exception) {
            DEFAULT_TTL
        }
    }

    fun extractMessage(
        payload: String
    ): String {
        return if (payload.startsWith("TTL:")) {
            payload.substringAfter("|")
        } else {
            payload
        }
    }

    fun decreasePayloadTtl(
        payload: String
    ): String? {
        val ttl =
            extractTtl(payload)

        if (ttl <= 1) {
            return null
        }

        val message =
            extractMessage(payload)

        return "TTL:${ttl - 1}|$message"
    }

    fun canForward(
        packet: MeshPacket
    ): Boolean {
        return extractTtl(packet.payload) > 1
    }
}
