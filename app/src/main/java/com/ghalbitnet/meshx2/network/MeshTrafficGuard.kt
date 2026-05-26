package com.ghalbitnet.meshx2.network

import com.ghalbitnet.meshx2.model.MeshPacket
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

object MeshTrafficGuard {
    private const val MAX_PACKET_PAYLOAD = 96 * 1024
    private const val MAX_CHAT_PAYLOAD = 12 * 1024
    private const val MAX_SOS_PAYLOAD = 1024
    private const val MAX_PACKET_ID_LENGTH = 96
    private const val MAX_PEER_ID_LENGTH = 96
    private const val GENERAL_WINDOW_MS = 60_000L
    private const val FILE_WINDOW_MS = 10_000L
    private const val MAX_GENERAL_PACKETS = 80
    private const val MAX_CHAT_PACKETS = 30
    private const val MAX_SOS_PACKETS = 6
    private const val MAX_ACK_PACKETS = 160
    private const val MAX_AUDIO_STATUS_PACKETS = 120
    private const val MAX_CALL_SIGNAL_PACKETS = 40
    private const val MAX_ROUTE_PROBE_PACKETS = 120
    private const val MAX_FILE_CHUNKS = 45

    private val allowedPacketTypes =
        setOf(
            "CHAT", "ACK", "SOS", "FILE_CHUNK", "DATA",
            "AUDIO_RECEIVED", "AUDIO_PLAYED",
            "CALL_INVITE", "CALL_ACCEPT", "CALL_REJECT", "CALL_END", "CALL_BUSY",
            "ROUTE_CHECK", "ROUTE_ACK",
            "VOICE_PROBE", "VOICE_ACK", "VOICE_STREAM_START", "VOICE_STREAM_END"
        )

    private val blockedExtensions =
        setOf(
            "apk", "apks", "aab", "dex",
            "exe", "dll", "com", "scr", "msi",
            "bat", "cmd", "ps1", "vbs", "js", "jse", "wsf", "sh",
            "jar", "class", "so",
            "docm", "xlsm", "pptm"
        )

    private val blockedMimeHints =
        listOf(
            "application/vnd.android.package-archive",
            "application/x-msdownload",
            "application/x-sh",
            "application/x-msdos-program",
            "application/java-archive"
        )

    private val windows =
        ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>>()

    data class GuardResult(
        val allowed: Boolean,
        val reason: String = ""
    )

    fun validatePacket(packet: MeshPacket): GuardResult {
        val type = packet.type.uppercase(Locale.US)

        if (type !in allowedPacketTypes) {
            return GuardResult(false, "Tipe paket tidak dikenal.")
        }

        if (
            packet.packetId.length > MAX_PACKET_ID_LENGTH ||
            packet.source.length > MAX_PEER_ID_LENGTH ||
            packet.destination.length > MAX_PEER_ID_LENGTH
        ) {
            return GuardResult(false, "Identitas paket tidak valid.")
        }

        if (packet.payload.length > MAX_PACKET_PAYLOAD) {
            return GuardResult(false, "Payload paket terlalu besar.")
        }

        if (type == "CHAT" && packet.payload.length > MAX_CHAT_PAYLOAD) {
            return GuardResult(false, "Pesan terlalu besar.")
        }

        if (type == "SOS" && packet.payload.length > MAX_SOS_PAYLOAD) {
            return GuardResult(false, "Payload SOS terlalu besar.")
        }

        if (!allowRate(packet.source.ifBlank { "unknown" }, type)) {
            return GuardResult(false, "Lalu lintas terlalu padat.")
        }

        return GuardResult(true)
    }

    fun validateFile(
        fileName: String,
        mimeType: String
    ): GuardResult {
        val cleanName = fileName.lowercase(Locale.US)
        val extension = cleanName.substringAfterLast('.', "")
        val mime = mimeType.lowercase(Locale.US)

        if (extension in blockedExtensions) {
            return GuardResult(false, "Jenis file ini diblokir demi keamanan.")
        }

        if (blockedMimeHints.any { mime.contains(it) }) {
            return GuardResult(false, "Jenis file ini diblokir demi keamanan.")
        }

        return GuardResult(true)
    }

    private fun allowRate(
        source: String,
        type: String
    ): Boolean {
        val now = System.currentTimeMillis()
        val isFile = type == "FILE_CHUNK"
        val windowMs = if (isFile) FILE_WINDOW_MS else GENERAL_WINDOW_MS
        val limit =
            when (type) {
                "FILE_CHUNK" -> MAX_FILE_CHUNKS
                "CHAT" -> MAX_CHAT_PACKETS
                "SOS" -> MAX_SOS_PACKETS
                "ACK" -> MAX_ACK_PACKETS
                "AUDIO_RECEIVED", "AUDIO_PLAYED" -> MAX_AUDIO_STATUS_PACKETS
                "CALL_INVITE", "CALL_ACCEPT", "CALL_REJECT", "CALL_END", "CALL_BUSY" -> MAX_CALL_SIGNAL_PACKETS
                "ROUTE_CHECK", "ROUTE_ACK", "VOICE_PROBE", "VOICE_ACK", "VOICE_STREAM_START", "VOICE_STREAM_END" -> MAX_ROUTE_PROBE_PACKETS
                else -> MAX_GENERAL_PACKETS
            }

        val key = "$source:$type"
        val queue =
            windows.getOrPut(key) {
                ConcurrentLinkedDeque()
            }

        while (true) {
            val first = queue.peekFirst() ?: break
            if (now - first <= windowMs) break
            queue.pollFirst()
        }

        if (queue.size >= limit) {
            return false
        }

        queue.addLast(now)
        return true
    }
}
