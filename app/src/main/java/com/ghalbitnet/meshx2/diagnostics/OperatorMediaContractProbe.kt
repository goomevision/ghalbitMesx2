package com.ghalbitnet.meshx2.diagnostics

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.BuildConfig
import com.ghalbitnet.meshx2.call.AdaptiveVoiceMode
import com.ghalbitnet.meshx2.call.VoicePacket
import com.ghalbitnet.meshx2.call.VoicePacketPriority
import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager
import com.ghalbitnet.meshx2.online.InternetRoute
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import com.ghalbitnet.meshx2.online.RelayRealtimeChannel
import com.ghalbitnet.meshx2.security.NodeSigningIdentityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OperatorMediaContractProbeResult(
    val configured: Boolean,
    val status: String,
    val routeLabel: String,
    val messageId: String,
    val error: String? = null
)

object OperatorMediaContractProbe {
    suspend fun run(context: Context): OperatorMediaContractProbeResult = withContext(Dispatchers.IO) {
        val relayBase = OnlineFallbackTransport.relayBaseUrl()
        val targetGlobalId = RelayRealtimeChannel.currentBoundGlobalId().orEmpty().ifBlank { MeshRuntimeManager.localGlobalId() }
        if (!BuildConfig.INTERNET_RELAY_CONFIGURED || relayBase.isBlank() || targetGlobalId.isBlank()) {
            Log.w("GHALBIT-CALL-MEDIA-PROBE", "STATUS=SERVER_NOT_CONFIGURED relay=$relayBase target=$targetGlobalId")
            return@withContext OperatorMediaContractProbeResult(
                configured = false,
                status = "SERVER_NOT_CONFIGURED",
                routeLabel = "server_operator_unavailable",
                messageId = "",
                error = "missing_config_or_target"
            )
        }

        val identity = NodeSigningIdentityManager.getOrCreate(context)
        val packet =
            VoicePacket(
                sessionId = "operator-media-probe-${System.currentTimeMillis()}",
                senderId = identity.nodeId,
                sequence = 1,
                timestamp = System.currentTimeMillis(),
                mode = AdaptiveVoiceMode.LIVE_VOICE,
                payload = ByteArray(320) { 0 },
                priority = VoicePacketPriority.HIGH
            )
        val result =
            OnlineFallbackTransport.sendVoiceFrameViaOperator(
                context = context,
                route = InternetRoute(targetGlobalId, relayBase),
                packet = packet,
                targetNodeId = identity.nodeId,
                targetGlobalId = targetGlobalId
            )
        Log.i(
            "GHALBIT-CALL-MEDIA-PROBE",
            "STATUS=${result.status} route=${result.mediaPath} messageId=${result.messageId} ok=${result.successful} error=${result.error.orEmpty()}"
        )
        OperatorMediaContractProbeResult(
            configured = true,
            status = result.status,
            routeLabel = result.mediaPath,
            messageId = result.messageId,
            error = result.error
        )
    }
}
