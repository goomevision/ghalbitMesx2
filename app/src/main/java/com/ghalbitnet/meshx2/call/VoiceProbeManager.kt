package com.ghalbitnet.meshx2.call

import android.util.Log
import kotlinx.coroutines.delay

class VoiceProbeManager(
    private val sendProbe: suspend (String, String) -> Boolean,
    private val awaitAck: suspend (String) -> Boolean
) {
    suspend fun probeNearbyVoice(): Boolean {
        repeat(3) { attempt ->
            val sent = sendProbe(CallManager.SIGNAL_VOICE_PROBE, CallManager.SIGNAL_VOICE_PROBE_ACK)
            Log.d("GHALBIT-VOICE-PROBE", "sent attempt=${attempt + 1} dispatched=$sent")
            if (awaitAck(CallManager.SIGNAL_VOICE_PROBE_ACK)) {
                Log.d("GHALBIT-VOICE-PROBE", "ack")
                Log.d("GHALBIT-VOICE-PROBE", "ready")
                return true
            }
            if (attempt < 2) {
                Log.d("GHALBIT-VOICE-PROBE", "retry")
                delay(400L)
            }
        }
        Log.w("GHALBIT-VOICE-PROBE", "failed after attempts")
        return false
    }

    suspend fun handshakeVoiceTransport(): Boolean {
        Log.d("GHALBIT-VOICE-HANDSHAKE", "hello sent")
        val helloSent = sendProbe(CallManager.SIGNAL_VOICE_HELLO, CallManager.SIGNAL_VOICE_HELLO_ACK)
        Log.d("GHALBIT-VOICE-HANDSHAKE", "hello dispatched=$helloSent")
        if (!awaitAck(CallManager.SIGNAL_VOICE_HELLO_ACK)) {
            Log.w("GHALBIT-VOICE-HANDSHAKE", "timeout stage=VOICE_HELLO")
            return false
        }
        Log.d("GHALBIT-VOICE-HANDSHAKE", "hello ack")
        Log.d("GHALBIT-VOICE-HANDSHAKE", "transport probe")
        val transportSent = sendProbe(CallManager.SIGNAL_VOICE_TRANSPORT_PROBE, CallManager.SIGNAL_VOICE_TRANSPORT_ACK)
        Log.d("GHALBIT-VOICE-HANDSHAKE", "transport dispatched=$transportSent")
        if (!awaitAck(CallManager.SIGNAL_VOICE_TRANSPORT_ACK)) {
            Log.w("GHALBIT-VOICE-HANDSHAKE", "timeout stage=VOICE_TRANSPORT")
            return false
        }
        Log.d("GHALBIT-VOICE-HANDSHAKE", "transport ack")
        return true
    }
}
