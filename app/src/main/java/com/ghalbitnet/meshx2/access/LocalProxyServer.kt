package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.vpn.VpnLogManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object LocalProxyServer {
    const val PORT = 8888

    private val running = AtomicBoolean(false)
    @Volatile
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var executor: ExecutorService? = null

    fun start(context: Context) {
        if (running.get()) return
        running.set(true)
        if (executor == null || executor?.isShutdown == true || executor?.isTerminated == true) {
            executor = Executors.newCachedThreadPool()
        }
        Thread {
            try {
                serverSocket = ServerSocket(PORT)
                VpnLogManager.info("GATEWAY_PROXY_STARTED", "Proxy lokal aktif di ${CaptivePortalServer.gatewayIp(context)}:$PORT")
                while (running.get()) {
                    val client = serverSocket?.accept() ?: break
                    val activeExecutor = executor
                    if (activeExecutor == null || activeExecutor.isShutdown || activeExecutor.isTerminated) {
                        runCatching { client.close() }
                        break
                    }
                    activeExecutor.execute {
                        handleClient(context.applicationContext, client)
                    }
                }
            } catch (e: Exception) {
                if (running.get()) {
                    VpnLogManager.error("GATEWAY_PROXY_ERROR", "Proxy lokal gagal berjalan.", e)
                }
            } finally {
                running.set(false)
            }
        }.start()
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        val activeExecutor = executor
        executor = null
        activeExecutor?.shutdownNow()
        VpnLogManager.info("GATEWAY_PROXY_STOP", "Proxy lokal dihentikan.")
    }

    private fun handleClient(context: Context, client: Socket) {
        try {
            client.soTimeout = 4000
            val remoteIp = client.inetAddress?.hostAddress.orEmpty()
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine().orEmpty()
            val headers = mutableMapOf<String, String>()
            var line: String?
            do {
                line = reader.readLine()
                if (!line.isNullOrBlank() && line.contains(':')) {
                    val key = line.substringBefore(':').trim().lowercase()
                    val value = line.substringAfter(':').trim()
                    headers[key] = value
                }
            } while (!line.isNullOrEmpty())

            val presentedToken =
                headers["x-ghalbit-access-token"]
                    ?: requestLine.substringAfter("token=", "").substringBefore(' ').ifBlank { null }
            val decision = GatewayAccessController.evaluate(remoteIp, presentedToken)
            HotspotClientSessionManager.upsert(
                clientIp = remoteIp,
                nodeId = decision.nodeId,
                status = decision.status,
                detail = decision.detail,
                accessToken = decision.accessToken
            )

            when (decision.status) {
                GatewayClientPolicy.ClientStatus.AUTHORIZED -> {
                    CommunitySessionRepository.syncFromCurrentState(context)
                    CommunitySessionRepository.recordProxyTraffic(
                        context,
                        clientIp = remoteIp,
                        uploadDelta = requestLine.length.toLong(),
                        downloadDelta = 128L
                    )
                    VpnLogManager.info(
                        "GATEWAY_PROXY_AUTHORIZED_ACCESS",
                        "client=$remoteIp nodeId=${decision.nodeId ?: "-"} request=$requestLine"
                    )
                    writeHttpResponse(
                        client,
                        statusLine = "HTTP/1.1 200 OK",
                        body = """
                            Ghalbit proxy menerima client authorized.
                            Node: ${decision.nodeId ?: "-"}
                            Token: ${decision.accessToken?.take(12) ?: "-"}

                            Fondasi proxy aktif. Upstream internet penuh belum dinyalakan di tahap ini.
                        """.trimIndent()
                    )
                }

                GatewayClientPolicy.ClientStatus.UNAUTHORIZED,
                GatewayClientPolicy.ClientStatus.UNKNOWN,
                GatewayClientPolicy.ClientStatus.TOKEN_EXPIRED -> {
                    val gatewayIp = CaptivePortalServer.gatewayIp(context)
                    val portalUrl = "http://$gatewayIp:${CaptivePortalServer.PORT}"
                    CaptivePortalRedirector.noteUnauthorizedClient(context, remoteIp)
                    UnauthorizedClientRegistry.touchUnauthorized(remoteIp, decision.detail)
                    VpnLogManager.warn(
                        "GATEWAY_PROXY_UNAUTHORIZED_REDIRECT",
                        "client=$remoteIp request=$requestLine portal=$portalUrl"
                    )
                    VpnLogManager.warn(
                        "HOTSPOT_AUTO_REDIRECT_REQUIRES_SYSTEM_GATEWAY",
                        "Android standar perlu proxy manual atau mode gateway level sistem untuk redirect penuh."
                    )
                    CommunitySessionRepository.syncFromCurrentState(context)
                    writeHttpRedirect(client, portalUrl, GatewayPortalAdvisor.unauthorizedInstruction(gatewayIp))
                }
            }
        } catch (e: Exception) {
            VpnLogManager.error("GATEWAY_PROXY_CLIENT_ERROR", "Proxy lokal gagal melayani klien.", e)
        } finally {
            runCatching { client.close() }
        }
    }

    private fun writeHttpRedirect(client: Socket, location: String, body: String) {
        val writer = OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8)
        writer.write("HTTP/1.1 302 Found\r\n")
        writer.write("Location: $location\r\n")
        writer.write("Content-Type: text/plain; charset=utf-8\r\n")
        writer.write("Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n")
        writer.write("Connection: close\r\n")
        writer.write("\r\n")
        writer.write(body)
        writer.flush()
    }

    private fun writeHttpResponse(client: Socket, statusLine: String, body: String) {
        val writer = OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8)
        writer.write("$statusLine\r\n")
        writer.write("Content-Type: text/plain; charset=utf-8\r\n")
        writer.write("Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n")
        writer.write("Connection: close\r\n")
        writer.write("\r\n")
        writer.write(body)
        writer.flush()
    }
}
