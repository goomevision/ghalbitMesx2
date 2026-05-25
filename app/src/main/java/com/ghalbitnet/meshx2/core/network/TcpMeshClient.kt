package com.ghalbitnet.meshx2.core.network

import android.util.Log
import org.json.JSONObject
import java.io.PrintWriter
import java.net.Socket
import kotlin.concurrent.thread

object TcpMeshClient {

    private const val TAG = "TcpMeshClient"
    private const val PORT = 56565

    fun send(
        ip: String,
        packet: MeshPacket
    ) {

        thread {

            try {

                val socket =
                    Socket(ip, PORT)

                val writer =
                    PrintWriter(
                        socket.getOutputStream(),
                        true
                    )

                val json =
                    JSONObject()

                json.put("id", packet.id)
                json.put("source", packet.source)
                json.put("destination", packet.destination)
                json.put("type", packet.type)
                json.put("payload", packet.payload)
                json.put("timestamp", packet.timestamp)
                json.put("ttl", packet.ttl)

                writer.println(json.toString())

                socket.close()

                Log.d(
                    TAG,
                    "Packet sent to $ip"
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Send error: ${e.message}"
                )
            }
        }
    }
}
