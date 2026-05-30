package com.ghalbitnet.meshx2.network

import android.util.Log
import com.ghalbitnet.meshx2.economy.ServicePathRecorder
import com.ghalbitnet.meshx2.economy.UsageSessionRecorder
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.model.SecurePacket
import org.json.JSONObject
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

object MeshSocketClient {
    private const val PORT = 56565
    private const val TIMEOUT = 5000
    private const val ENVELOPE_MESH_PACKET = "MESH_PACKET"
    private const val ENVELOPE_SECURE_PACKET = "SECURE_PACKET"
    private val executor = Executors.newCachedThreadPool()

    fun send(host: String, packet: MeshPacket) {
        executor.execute {
            sendBlocking(host, packet)
        }
    }

    fun sendBlocking(host: String, packet: MeshPacket): Boolean {
        var socket: Socket? = null

        return try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, PORT), TIMEOUT)

            val writer = PrintWriter(socket.getOutputStream(), true)
            val json = JSONObject().apply {
                put("type", ENVELOPE_MESH_PACKET)
                put("packetType", packet.type)
                put("packetId", packet.packetId)
                put("source", packet.source)
                put("destination", packet.destination)
                put("payload", packet.payload)
                put("hopCount", packet.hopCount)
                put("maxHop", packet.maxHop)
                put("timestamp", packet.timestamp)
                put("encrypted", packet.encrypted)
            }

            writer.println(json.toString())
            writer.flush()

            ServicePathRecorder.recordSend(packet, host)
            UsageSessionRecorder.recordSend(packet)
            !writer.checkError()
        } catch (e: Exception) {
            Log.e("GHALBIT", "send failed to $host: ${e.message}")
            false
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    fun sendSecure(host: String, secure: SecurePacket) {
        executor.execute {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(host, PORT), TIMEOUT)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val json = JSONObject().apply {
                    put("type", ENVELOPE_SECURE_PACKET)
                    put("sourcePublicKey", secure.sourcePublicKey)
                    put("destinationPublicKey", secure.destinationPublicKey)
                    put("encryptedPayload", secure.encryptedPayload)
                    put("packetId", secure.packetId)
                    put("hopCount", secure.hopCount)
                    put("maxHop", secure.maxHop)
                    put("timestamp", secure.timestamp)
                }
                writer.println(json.toString())
                writer.flush()
                Log.d("GHALBIT", "Secure packet sent to $host")
            } catch (e: Exception) {
                Log.e("GHALBIT", "sendSecure failed to $host: ${e.message}")
            } finally { try { socket?.close() } catch (_: Exception) {} }
        }
    }

    fun sendRaw(host: String, data: Map<String, Any>) {
        executor.execute {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(host, PORT), TIMEOUT)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val json = JSONObject(data)
                writer.println(json.toString())
                writer.flush()
            } catch (e: Exception) {
                Log.e("GHALBIT", "sendRaw failed to $host: ${e.message}")
            } finally { try { socket?.close() } catch (_: Exception) {} }
        }
    }
}
