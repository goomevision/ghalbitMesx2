package com.ghalbitnet.meshx2.core.network

import java.net.InetSocketAddress
import java.net.Socket

object LatencyEngine {

    private const val MESH_PORT = 56565
    private const val TIMEOUT_MS = 1500

    fun calculateLatency(
        host: String,
        port: Int = MESH_PORT,
        timeoutMs: Int = TIMEOUT_MS
    ): Int {
        if (host.isBlank() || host.startsWith("nearby:")) {
            return -1
        }

        val started =
            System.currentTimeMillis()

        var socket: Socket? = null

        return try {
            socket = Socket()
            socket.connect(
                InetSocketAddress(host, port),
                timeoutMs
            )

            (System.currentTimeMillis() - started).toInt()
        } catch (_: Exception) {
            -1
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    fun scoreNode(
        signal: Int,
        latency: Int
    ): Int {
        if (latency < 0) {
            return signal
        }

        return (signal * 2) - latency
    }
}
