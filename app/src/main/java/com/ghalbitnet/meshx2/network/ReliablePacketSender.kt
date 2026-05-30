package com.ghalbitnet.meshx2.network

import com.ghalbitnet.meshx2.core.log.MeshLogger
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.stats.MeshStatistics
import kotlinx.coroutines.delay

/**
 * =====================================================
 * GHALBIT MESH X2
 * RELIABLE PACKET SENDER
 * =====================================================
 *
 * Pengiriman packet dengan retry sederhana.
 *
 * FUTURE:
 * - ACK tracking
 * - timeout per packet
 * - priority queue
 * - QoS class
 * - route failover
 * - multi-hop retry
 */

object ReliablePacketSender {

    suspend fun sendWithRetry(
        ipAddress: String,
        packet: MeshPacket,
        retryCount: Int = 3,
        delayMs: Long = 700L
    ): Boolean {
        // TODO unified identity:
        // callers should resolve destination by globalId first and use
        // ipAddress here strictly as a transport hop hint.

        AckTracker.track(packet.packetId)

        repeat(retryCount) { attempt ->

            val sent =
                MeshSocketClient.sendBlocking(
                    ipAddress,
                    packet
                )

            if (sent) {
                MeshStatistics.sentPacket(
                    packet.type
                )

                MeshLogger.i(
                    "RELIABLE_SEND",
                    "sent ${packet.type} to $ipAddress attempt=${attempt + 1}"
                )

                return true
            }

            MeshLogger.w(
                "RELIABLE_SEND",
                "failed attempt=${attempt + 1} to $ipAddress"
            )

            delay(delayMs)
        }

        return false
    }
}
