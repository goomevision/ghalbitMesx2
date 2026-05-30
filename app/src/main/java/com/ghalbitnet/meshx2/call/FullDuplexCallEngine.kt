package com.ghalbitnet.meshx2.call

import android.util.Base64
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class FullDuplexCallEngine(
    private val sessionProvider: () -> CallSession?,
    private val endpointProvider: () -> CallPeerEndpoint?,
    private val onRealtimeFailure: (String) -> Unit
) {
    private val running = AtomicBoolean(false)
    private val muted = AtomicBoolean(false)
    private val sequence = AtomicInteger(0)
    private val retransmitManager = VoiceRetransmitManager()
    private val captureWorker =
        AudioCaptureWorker { frame ->
            if (muted.get()) return@AudioCaptureWorker
            val session = sessionProvider() ?: return@AudioCaptureWorker
            val endpoint = endpointProvider() ?: return@AudioCaptureWorker
            val voicePacket =
                VoicePacket(
                    sessionId = session.callId,
                    senderId = session.localNodeId,
                    sequence = sequence.incrementAndGet(),
                    timestamp = System.currentTimeMillis(),
                    mode = AdaptiveVoiceMode.LIVE_VOICE,
                    payload = frame,
                    priority = VoicePacketPriority.HIGH
                )
            retransmitManager.remember(voicePacket)
            val sent =
                CallManager.sendVoicePacket(
                    peer = endpoint,
                    localNodeId = session.localNodeId,
                    packet = voicePacket
                )
            if (!sent) {
                onRealtimeFailure("Realtime call tidak stabil")
            }
        }
    private val jitterBuffer = AudioPacketJitterBuffer(frameBytes = captureWorker.frameBytes)
    private val playbackWorker =
        AudioPlaybackWorker(
            jitterBuffer = jitterBuffer,
            frameBytes = captureWorker.frameBytes
        )

    fun start() {
        if (!running.compareAndSet(false, true)) return
        Log.d("GHALBIT-CALL-RTC", "start realtime engine")
        playbackWorker.start()
        captureWorker.start()
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        Log.d("GHALBIT-CALL-RTC", "stop realtime engine")
        captureWorker.stop()
        playbackWorker.stop()
        jitterBuffer.clear()
        retransmitManager.clearSession()
        sequence.set(0)
    }

    fun release() {
        stop()
        captureWorker.release()
        playbackWorker.release()
    }

    fun setMuted(value: Boolean) {
        muted.set(value)
        Log.d("GHALBIT-CALL-RTC", "mute=$value")
    }

    fun onIncomingAudioPacket(payload: String) {
        val voicePacket = CallManager.parseVoicePacket(payload) ?: return
        CallManager.recordAudioFrameReceived()
        jitterBuffer.offer(voicePacket.sequence, voicePacket.payload)
        Log.d("GHALBIT-CALL-AUDIO-RX", "seq=${voicePacket.sequence} bytes=${voicePacket.payload.size}")
    }
}
