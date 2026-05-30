package com.ghalbitnet.meshx2.call

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.online.InternetRoute
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import org.json.JSONObject

class InternetRelaySignalingChannel(
    private val context: Context,
    private val route: InternetRoute
) : CallSignalingChannel {
    override suspend fun sendOffer(target: VoipTarget, payload: String): Boolean =
        sendTyped(target, CallManager.SIGNAL_CALL_WEBRTC_OFFER, payload)

    override suspend fun sendAnswer(target: VoipTarget, payload: String): Boolean =
        sendTyped(target, CallManager.SIGNAL_CALL_WEBRTC_ANSWER, payload)

    override suspend fun sendIceCandidate(target: VoipTarget, payload: String): Boolean =
        sendTyped(target, CallManager.SIGNAL_CALL_WEBRTC_ICE, payload)

    override suspend fun sendCallInvite(target: VoipTarget, payload: String): Boolean =
        sendTyped(target, CallManager.SIGNAL_CALL_INVITE, payload)

    override suspend fun sendCallAccept(target: VoipTarget, payload: String): Boolean =
        sendTyped(target, CallManager.SIGNAL_CALL_ACCEPT, payload)

    override suspend fun sendCallEnd(target: VoipTarget, payload: String): Boolean =
        sendTyped(target, CallManager.SIGNAL_CALL_END, payload)

    private suspend fun sendTyped(target: VoipTarget, type: String, rawPayload: String): Boolean {
        val enriched = enrichPayload(target, type, rawPayload)
        val parsed = runCatching { JSONObject(enriched) }.getOrNull()
        if (parsed?.optString("callId").isNullOrBlank()) {
            Log.w("GHALBIT-CALL-SIGNAL", "missingCallId type=$type target=${target.globalId ?: target.nodeId}")
        }
        if (parsed?.optString("targetGlobalId").isNullOrBlank()) {
            Log.w("GHALBIT-CALL-SIGNAL", "missingTargetGlobalId type=$type targetNodeId=${target.nodeId}")
        }
        Log.d(
            "GHALBIT-CALL-SERVER",
            "send type=$type callId=${target.callId} target=${target.globalId ?: target.nodeId} relay=${route.relayUrl}"
        )
        val ok = OnlineFallbackTransport.sendCallSignalViaInternet(context, route, type, enriched)
        Log.d("GHALBIT-CALL-SERVER", "result type=$type callId=${target.callId} ok=$ok")
        if (ok) {
            Log.d("GHALBIT-CALL-SIGNAL", "relayAccepted type=$type callId=${target.callId}")
        } else {
            Log.w("GHALBIT-CALL-SIGNAL", "relayRejected type=$type callId=${target.callId}")
        }
        return ok
    }

    private fun enrichPayload(target: VoipTarget, type: String, rawPayload: String): String {
        val base = runCatching { JSONObject(rawPayload) }.getOrElse { JSONObject().put("payload", rawPayload) }
        return base
            .put("callId", target.callId)
            .put("signalType", type)
            .put("targetNodeId", target.nodeId)
            .put("targetGlobalId", target.globalId)
            .put("targetPublicKeyHash", target.publicKeyHash)
            .put("targetDisplayName", target.displayName)
            .put("routeType", target.routeType.name)
            .put("routeHint", target.routeHint)
            .put("incoming", target.incoming)
            .put("createdAt", System.currentTimeMillis())
            .toString()
    }
}
