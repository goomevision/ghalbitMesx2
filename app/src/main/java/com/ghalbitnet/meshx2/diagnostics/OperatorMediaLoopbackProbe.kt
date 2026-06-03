package com.ghalbitnet.meshx2.diagnostics

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.BuildConfig
import com.ghalbitnet.meshx2.call.AdaptiveVoiceMode
import com.ghalbitnet.meshx2.call.CallManager
import com.ghalbitnet.meshx2.call.VoicePacket
import com.ghalbitnet.meshx2.call.VoicePacketPriority
import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager
import com.ghalbitnet.meshx2.online.InternetRoute
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import com.ghalbitnet.meshx2.online.RelayRealtimeChannel
import com.ghalbitnet.meshx2.security.NodeSigningIdentityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

data class OperatorMediaLoopbackProbeResult(
    val configured: Boolean,
    val status: String,
    val sentMessageId: String,
    val inboxMessages: Int,
    val parsedSequence: Int,
    val parsedBytes: Int,
    val error: String? = null
)

object OperatorMediaLoopbackProbe {
    suspend fun run(context: Context): OperatorMediaLoopbackProbeResult = withContext(Dispatchers.IO) {
        val relayBase = OnlineFallbackTransport.relayBaseUrl()
        val targetGlobalId = RelayRealtimeChannel.currentBoundGlobalId().orEmpty().ifBlank { MeshRuntimeManager.localGlobalId() }
        if (!BuildConfig.INTERNET_RELAY_CONFIGURED || relayBase.isBlank() || targetGlobalId.isBlank()) {
            Log.w("GHALBIT-CALL-MEDIA-PROBE", "LOOPBACK_STATUS=SERVER_NOT_CONFIGURED relay=$relayBase target=$targetGlobalId")
            return@withContext OperatorMediaLoopbackProbeResult(
                configured = false,
                status = "SERVER_NOT_CONFIGURED",
                sentMessageId = "",
                inboxMessages = 0,
                parsedSequence = -1,
                parsedBytes = 0,
                error = "missing_config_or_target"
            )
        }

        val identity = NodeSigningIdentityManager.getOrCreate(context)
        val packet =
            VoicePacket(
                sessionId = "operator-media-loop-${System.currentTimeMillis()}",
                senderId = identity.nodeId,
                sequence = 1,
                timestamp = System.currentTimeMillis(),
                mode = AdaptiveVoiceMode.LIVE_VOICE,
                payload = generateToneFrame(),
                priority = VoicePacketPriority.HIGH
            )
        val sendResult =
            OnlineFallbackTransport.sendVoiceFrameViaOperator(
                context = context,
                route = InternetRoute(targetGlobalId, relayBase),
                packet = packet,
                targetNodeId = identity.nodeId,
                targetGlobalId = targetGlobalId
            )
        if (!sendResult.successful) {
            Log.w("GHALBIT-CALL-MEDIA-PROBE", "LOOPBACK_SEND_FAIL status=${sendResult.status} error=${sendResult.error.orEmpty()}")
            return@withContext OperatorMediaLoopbackProbeResult(
                configured = true,
                status = sendResult.status,
                sentMessageId = sendResult.messageId,
                inboxMessages = 0,
                parsedSequence = -1,
                parsedBytes = 0,
                error = sendResult.error
            )
        }

        val inbox = OnlineFallbackTransport.fetchInbox(context, targetGlobalId)
        val mediaMessage = inbox.messages.firstOrNull { it.contentType == "CALL_AUDIO_FRAME" && it.messageId == sendResult.messageId }
        if (mediaMessage == null) {
            Log.w("GHALBIT-CALL-MEDIA-PROBE", "LOOPBACK_FETCH_MISS messageId=${sendResult.messageId} inbox=${inbox.messages.size}")
            return@withContext OperatorMediaLoopbackProbeResult(
                configured = true,
                status = "LOOPBACK_NOT_RETURNED",
                sentMessageId = sendResult.messageId,
                inboxMessages = inbox.messages.size,
                parsedSequence = -1,
                parsedBytes = 0,
                error = inbox.error
            )
        }

        val parsed = CallManager.parseVoicePacket(mediaMessage.payload)
        if (parsed == null) {
            Log.w("GHALBIT-CALL-MEDIA-PROBE", "LOOPBACK_PARSE_FAIL messageId=${sendResult.messageId}")
            return@withContext OperatorMediaLoopbackProbeResult(
                configured = true,
                status = "LOOPBACK_PARSE_FAIL",
                sentMessageId = sendResult.messageId,
                inboxMessages = inbox.messages.size,
                parsedSequence = -1,
                parsedBytes = 0
            )
        }

        Log.i(
            "GHALBIT-CALL-MEDIA-PROBE",
            "LOOPBACK_OK messageId=${sendResult.messageId} seq=${parsed.sequence} bytes=${parsed.payload.size} inbox=${inbox.messages.size}"
        )
        OperatorMediaLoopbackProbeResult(
            configured = true,
            status = "LOOPBACK_OK",
            sentMessageId = sendResult.messageId,
            inboxMessages = inbox.messages.size,
            parsedSequence = parsed.sequence,
            parsedBytes = parsed.payload.size
        )
    }

    private fun generateToneFrame(sampleRate: Int = 8000, frequencyHz: Double = 440.0, frameBytes: Int = 320): ByteArray {
        val sampleCount = frameBytes / 2
        val output = ByteArray(frameBytes)
        for (i in 0 until sampleCount) {
            val sample = (sin(2.0 * PI * frequencyHz * i / sampleRate) * 8000.0).roundToInt()
            output[i * 2] = (sample and 0xFF).toByte()
            output[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return output
    }
}
