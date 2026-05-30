package com.ghalbitnet.meshx2.economy

import android.content.Context
import com.ghalbitnet.meshx2.model.MeshPacket
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

object UsageSessionRecorder {

    private const val PREFS_NAME = "usage_session_recorder"
    private const val KEY_SESSIONS = "sessions"
    private const val MAX_SESSIONS = 80
    private const val SESSION_BUCKET_MS = 5 * 60 * 1000L

    private var appContext: Context? = null

    data class UsageSession(
        val sessionKey: String,
        val serviceFamily: ServiceFamily,
        val packetType: String,
        val source: String,
        val destination: String,
        val bucketStartedAt: Long,
        val bytesSent: Long,
        val bytesReceived: Long,
        val bytesRelayed: Long,
        val firstSeenAt: Long,
        val lastSeenAt: Long,
        val packetCount: Int
    ) {
        val totalBytes: Long
            get() = bytesSent + bytesReceived + bytesRelayed

        val durationMs: Long
            get() = max(1L, lastSeenAt - firstSeenAt)
    }

    fun initialize(
        context: Context
    ) {
        appContext = context.applicationContext
    }

    fun recordSend(
        packet: MeshPacket
    ) {
        record(packet, bytesSent = estimatePacketBytes(packet))
    }

    fun recordReceive(
        packet: MeshPacket
    ) {
        record(packet, bytesReceived = estimatePacketBytes(packet))
    }

    fun recordRelay(
        packet: MeshPacket
    ) {
        record(packet, bytesRelayed = estimatePacketBytes(packet))
    }

    fun latestObservedSession(
        context: Context
    ): UsageSession? {
        return recentSessions(context, 1).firstOrNull()
    }

    fun latestObservedSessionByFamily(
        context: Context,
        family: ServiceFamily
    ): UsageSession? {
        return recentSessions(context, 30)
            .firstOrNull { it.serviceFamily == family }
    }

    fun recentSessions(
        context: Context,
        limit: Int = 10
    ): List<UsageSession> {
        val prefs =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val array =
            JSONArray(prefs.getString(KEY_SESSIONS, "[]"))

        val items = mutableListOf<UsageSession>()

        for (index in array.length() - 1 downTo 0) {
            items += deserialize(array.getJSONObject(index))
            if (items.size >= limit) {
                break
            }
        }

        return items
    }

    private fun record(
        packet: MeshPacket,
        bytesSent: Long = 0L,
        bytesReceived: Long = 0L,
        bytesRelayed: Long = 0L
    ) {
        val context = appContext ?: return
        val prefs =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val array =
            JSONArray(prefs.getString(KEY_SESSIONS, "[]"))

        val sessionKey =
            buildSessionKey(packet)

        val now =
            System.currentTimeMillis()

        var updated = false

        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            if (item.optString("sessionKey") == sessionKey) {
                item.put("bytesSent", item.optLong("bytesSent") + bytesSent)
                item.put("bytesReceived", item.optLong("bytesReceived") + bytesReceived)
                item.put("bytesRelayed", item.optLong("bytesRelayed") + bytesRelayed)
                item.put("lastSeenAt", now)
                item.put("packetCount", item.optInt("packetCount") + 1)
                updated = true
                break
            }
        }

        if (!updated) {
            array.put(
                JSONObject()
                    .put("sessionKey", sessionKey)
                    .put("serviceFamily", classifyFamily(packet).name)
                    .put("packetType", packet.type)
                    .put("source", packet.source)
                    .put("destination", packet.destination)
                    .put("bucketStartedAt", sessionBucket(packet.timestamp))
                    .put("bytesSent", bytesSent)
                    .put("bytesReceived", bytesReceived)
                    .put("bytesRelayed", bytesRelayed)
                    .put("firstSeenAt", now)
                    .put("lastSeenAt", now)
                    .put("packetCount", 1)
            )
        }

        val trimmed =
            JSONArray().apply {
                val start = maxOf(0, array.length() - MAX_SESSIONS)
                for (index in start until array.length()) {
                    put(array.getJSONObject(index))
                }
            }

        prefs.edit()
            .putString(KEY_SESSIONS, trimmed.toString())
            .apply()
    }

    private fun buildSessionKey(
        packet: MeshPacket
    ): String {
        return listOf(
            packet.type,
            packet.source,
            packet.destination.ifBlank { "BROADCAST" },
            sessionBucket(packet.timestamp).toString()
        ).joinToString("|")
    }

    private fun sessionBucket(
        timestamp: Long
    ): Long {
        return timestamp / SESSION_BUCKET_MS * SESSION_BUCKET_MS
    }

    private fun estimatePacketBytes(
        packet: MeshPacket
    ): Long {
        return packet.payload.toByteArray().size.toLong() + 96L
    }

    fun classifyFamily(
        packet: MeshPacket
    ): ServiceFamily {
        return classifyFamily(packet.type, packet.packetId)
    }

    fun classifyFamily(
        packetType: String,
        packetId: String = ""
    ): ServiceFamily {
        val type = packetType.uppercase()
        val id = packetId.uppercase()

        return when {
            type == "CHAT" || id.startsWith("CHAT-") -> ServiceFamily.CHAT
            type == "FILE_CHUNK" ||
                type == "AUDIO_RECEIVED" ||
                type == "AUDIO_PLAYED" ||
                id.startsWith("AUDIO-") -> ServiceFamily.MEDIA
            type.startsWith("CALL_") || id.startsWith("CALL-") -> ServiceFamily.CALL
            type == "SOS" || id.startsWith("SOS-") -> ServiceFamily.SOS
            type == "ACK" || type == "RREQ" || type == "RREP" || type == "DATA" -> ServiceFamily.CONTROL
            else -> ServiceFamily.OTHER
        }
    }

    private fun deserialize(
        source: JSONObject
    ): UsageSession {
        return UsageSession(
            sessionKey = source.optString("sessionKey"),
            serviceFamily = ServiceFamily.valueOf(source.optString("serviceFamily", ServiceFamily.OTHER.name)),
            packetType = source.optString("packetType"),
            source = source.optString("source"),
            destination = source.optString("destination"),
            bucketStartedAt = source.optLong("bucketStartedAt"),
            bytesSent = source.optLong("bytesSent"),
            bytesReceived = source.optLong("bytesReceived"),
            bytesRelayed = source.optLong("bytesRelayed"),
            firstSeenAt = source.optLong("firstSeenAt"),
            lastSeenAt = source.optLong("lastSeenAt"),
            packetCount = source.optInt("packetCount")
        )
    }
}
