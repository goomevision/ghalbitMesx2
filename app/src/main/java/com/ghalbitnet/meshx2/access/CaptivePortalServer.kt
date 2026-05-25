package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.vpn.VpnLogManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object CaptivePortalServer {
    const val PORT = 8080

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
                VpnLogManager.info("CAPTIVE_PORTAL_SERVER_START", "Portal lokal aktif di ${gatewayIp(context)}:$PORT")
                while (running.get()) {
                    val client = serverSocket?.accept() ?: break
                    val activeExecutor = executor
                    if (activeExecutor == null || activeExecutor.isShutdown || activeExecutor.isTerminated) {
                        try { client.close() } catch (_: Exception) {}
                        break
                    }
                    activeExecutor.execute {
                        handleClient(context.applicationContext, client)
                    }
                }
            } catch (e: Exception) {
                if (running.get()) {
                    VpnLogManager.error("CAPTIVE_PORTAL_SERVER_ERROR", "Portal lokal gagal berjalan.", e)
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
        VpnLogManager.info("CAPTIVE_PORTAL_SERVER_STOP", "Portal lokal dihentikan.")
    }

    fun gatewayIp(context: Context): String {
        return hotspotGatewayIp() ?: "192.168.43.1"
    }

    private fun handleClient(context: Context, client: Socket) {
        try {
            client.soTimeout = 4000
            val remoteIp = client.inetAddress?.hostAddress.orEmpty()
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine().orEmpty()
            while (reader.readLine()?.isNotEmpty() == true) {
                // drain headers
            }
            val authorizedRecord =
                PeerAuthRegistry.getByIp(remoteIp)
            val clientRecord =
                if (authorizedRecord != null) {
                    UnauthorizedClientRegistry.markAuthorized(
                        ipAddress = remoteIp,
                        nodeId = authorizedRecord.nodeId,
                        detail = "HELLO_AUTH valid"
                    )
                } else {
                    VpnLogManager.warn(
                        "CAPTIVE_PORTAL_UNAUTHORIZED_CLIENT",
                        "client=$remoteIp request=$requestLine"
                    )
                    CaptivePortalRedirector.noteUnauthorizedClient(context, remoteIp)
                    UnauthorizedClientRegistry.touchUnauthorized(
                        ipAddress = remoteIp,
                        detail = "Belum ada HELLO_AUTH"
                    )
                }

            val body =
                JoinCommunityPage.render(
                    gatewayUrl = CaptivePortalPolicy.gatewayUrl(context),
                    proxyHost = gatewayIp(context),
                    proxyPort = LocalProxyServer.PORT,
                    portalPort = PORT,
                    status = clientRecord.status,
                    detail = clientRecord.detail
                )
            val writer = OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8)
            writer.write("HTTP/1.1 200 OK\r\n")
            writer.write("Content-Type: text/html; charset=utf-8\r\n")
            writer.write("Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n")
            writer.write("Connection: close\r\n")
            writer.write("\r\n")
            writer.write(body)
            writer.flush()
        } catch (e: Exception) {
            VpnLogManager.error("CAPTIVE_PORTAL_CLIENT_ERROR", "Portal lokal gagal melayani klien.", e)
        } finally {
            runCatching { client.close() }
        }
    }

    private fun hotspotGatewayIp(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val name = networkInterface.name.orEmpty().lowercase()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                if (
                    !name.contains("ap") &&
                    !name.contains("softap") &&
                    !name.contains("wlan1") &&
                    !name.contains("rndis") &&
                    !name.contains("usb")
                ) {
                    continue
                }
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
