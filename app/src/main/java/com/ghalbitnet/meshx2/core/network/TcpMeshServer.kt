package com.ghalbitnet.meshx2.core.network

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import kotlin.concurrent.thread

object TcpMeshServer {

    private const val TAG = "TcpMeshServer"
    private const val PORT = 56565

    fun start(
        onPacket: (MeshPacket) -> Unit
    ) {

        thread {

            try {

                val serverSocket =
                    ServerSocket(PORT)

                Log.d(
                    TAG,
                    "TCP Mesh Server Started"
                )

                while (true) {

                    val client =
                        serverSocket.accept()

                    thread {

                        try {

                            val reader =
                                BufferedReader(
                                    InputStreamReader(
                                        client.inputStream
                                    )
                                )

                            val line =
                                reader.readLine()

                            if (line != null) {

                                val json =
                                    JSONObject(line)

                                val packet =
                                    MeshPacket(
                                        id = json.getString("id"),
                                        source = json.getString("source"),
                                        destination = json.getString("destination"),
                                        type = json.getString("type"),
                                        payload = json.getString("payload"),
                                        timestamp = json.getLong("timestamp"),
                                        ttl = json.getInt("ttl")
                                    )

                                onPacket(packet)

                            }

                            client.close()

                        } catch (e: Exception) {

                            Log.e(
                                TAG,
                                "Client error: ${e.message}"
                            )
                        }
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Server error: ${e.message}"
                )
            }
        }
    }
}
