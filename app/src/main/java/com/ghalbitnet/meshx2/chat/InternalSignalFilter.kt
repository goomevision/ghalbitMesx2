package com.ghalbitnet.meshx2.chat

import android.util.Log
import com.ghalbitnet.meshx2.call.CallSignalParser
import org.json.JSONObject

data class InternalSignalDecision(
    val hidden: Boolean,
    val messageType: MessageVisibility,
    val visibilityType: MessageVisibility,
    val internalSignalType: String?,
    val humanText: String?,
    val debugText: String?
)

object InternalSignalFilter {
    private val internalTypes =
        setOf(
            "CALL_INVITE",
            "CALL_START",
            "CALL_ACCEPT",
            "CALL_REJECT",
            "CALL_END",
            "VOICE_HELLO",
            "VOICE_HELLO_ACK",
            "VOICE_ACK",
            "VOICE_PROBE",
            "VOICE_PROBE_ACK",
            "VOICE_TRANSPORT_PROBE",
            "VOICE_TRANSPORT_ACK",
            "VOICE_STREAM_START",
            "VOICE_STREAM_ACTIVE_ACK",
            "VOICE_HEARTBEAT",
            "VOICE_STREAM_END",
            "ROUTE_UPDATE",
            "HEARTBEAT_SIGNAL",
            "RELAY_PREPARED",
            "CALL_RINGING_ACK"
        )

    fun classify(type: String, payload: String): InternalSignalDecision {
        val normalizedType = type.trim().uppercase()
        val json = runCatching { JSONObject(payload) }.getOrNull()
        val looksInternalJson =
            json != null && (
                json.has("callId") ||
                    json.has("sourceNodeId") ||
                    json.has("sourceGlobalId") ||
                    json.has("sourcePublicKeyHash") ||
                    json.has("relaySessionId") ||
                    json.has("routeToken")
                )
        val isInternal = normalizedType in internalTypes || looksInternalJson
        if (!isInternal) {
            return InternalSignalDecision(
                hidden = false,
                messageType = MessageVisibility.USER_VISIBLE_MESSAGE,
                visibilityType = MessageVisibility.VISIBLE,
                internalSignalType = null,
                humanText = null,
                debugText = null
            )
        }
        val parsed = CallSignalParser.parse(normalizedType, payload)
        val messageType =
            when {
                normalizedType.startsWith("CALL_") -> MessageVisibility.CALL_SIGNAL
                normalizedType.startsWith("VOICE_") -> MessageVisibility.VOICE_SIGNAL
                normalizedType.startsWith("ROUTE_") -> MessageVisibility.ROUTE_SIGNAL
                normalizedType.startsWith("RELAY_") -> MessageVisibility.RELAY_SIGNAL
                else -> MessageVisibility.INTERNAL_SIGNAL
            }
        Log.d("GHALBIT-SIGNAL", "hidden internal signal type=$normalizedType")
        return InternalSignalDecision(
            hidden = true,
            messageType = messageType,
            visibilityType = MessageVisibility.HIDDEN,
            internalSignalType = normalizedType,
            humanText = parsed?.humanEvent,
            debugText = parsed?.debugSummary ?: payload.take(240)
        )
    }
}
