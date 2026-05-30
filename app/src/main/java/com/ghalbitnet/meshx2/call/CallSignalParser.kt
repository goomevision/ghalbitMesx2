package com.ghalbitnet.meshx2.call

import android.util.Log
import org.json.JSONObject

data class ParsedCallSignal(
    val type: String,
    val callId: String?,
    val sourceNodeId: String?,
    val sourceGlobalId: String?,
    val peerName: String?,
    val relaySessionId: String?,
    val routeToken: String?,
    val humanEvent: String,
    val debugSummary: String
)

object CallSignalParser {
    fun parse(type: String, payload: String): ParsedCallSignal? {
        val normalizedType = type.trim().uppercase()
        return runCatching {
            val json = runCatching { JSONObject(payload) }.getOrElse { JSONObject() }
            val callId = json.optString("callId").ifBlank { null }
            val sourceNodeId = json.optString("sourceNodeId").ifBlank { json.optString("peerName").ifBlank { null } }
            val sourceGlobalId = json.optString("sourceGlobalId").ifBlank { null }
            val peerName = json.optString("peerName").ifBlank { null }
            val relaySessionId = json.optString("relaySessionId").ifBlank { null }
            val routeToken = json.optString("routeToken").ifBlank { null }
            val humanEvent =
                when (normalizedType) {
                    "CALL_INVITE", "CALL_START" -> "${peerName ?: "Kontak"} memulai panggilan"
                    "CALL_ACCEPT" -> "Menyambungkan suara..."
                    "CALL_REJECT" -> "Panggilan ditolak"
                    "CALL_END" -> "Panggilan berakhir"
                    "CALL_RINGING_ACK" -> "Panggilan berdering"
                    "VOICE_HELLO", "VOICE_HELLO_ACK", "VOICE_ACK" -> "Menguji suara..."
                    "VOICE_PROBE", "VOICE_PROBE_ACK", "VOICE_TRANSPORT_PROBE", "VOICE_TRANSPORT_ACK" -> "Menguji jalur suara"
                    "VOICE_STREAM_START", "VOICE_STREAM_ACTIVE_ACK" -> "Suara aktif"
                    "VOICE_HEARTBEAT" -> "Jalur suara aktif"
                    "VOICE_STREAM_END" -> "Suara dihentikan"
                    "ROUTE_UPDATE" -> "Beralih ke jalur lain"
                    "RELAY_PREPARED" -> "Relay cadangan siap"
                    "HEARTBEAT_SIGNAL" -> "Sesi tetap aktif"
                    else -> "Event panggilan"
                }
            val debugSummary =
                buildString {
                    append("type=")
                    append(normalizedType)
                    callId?.let { append(" callId="); append(it) }
                    sourceNodeId?.let { append(" sourceNodeId="); append(it) }
                    sourceGlobalId?.let { append(" sourceGlobalId="); append(it) }
                    relaySessionId?.let { append(" relaySessionId="); append(it) }
                }
            Log.d("GHALBIT-CALL-SIGNAL", "parsed")
            ParsedCallSignal(
                type = normalizedType,
                callId = callId,
                sourceNodeId = sourceNodeId,
                sourceGlobalId = sourceGlobalId,
                peerName = peerName,
                relaySessionId = relaySessionId,
                routeToken = routeToken,
                humanEvent = humanEvent,
                debugSummary = debugSummary
            )
        }.getOrElse {
            Log.w("GHALBIT-CALL-SIGNAL", "invalid payload")
            null
        }
    }
}
