package com.ghalbitnet.meshx2.call

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class FullDuplexCallEngine(
    private val sessionProvider: () -> CallSession?,
    private val endpointProvider: () -> CallPeerEndpoint?,
    private val onRealtimeFailure: (String) -> Unit,
    private val onTruthEvent: ((stage: String, details: String) -> Unit)? = null
) {
    data class AudioRuntimeMetrics(
        val capturedFrames: Long,
        val txFrames: Long,
        val txFailedFrames: Long,
        val rxFrames: Long,
        val queuedFrames: Int,
        val playedFrames: Long,
        val droppedFrames: Long,
        val concealFrames: Long,
        val rxActiveForMs: Long
    )
    private val running = AtomicBoolean(false)
    private val muted = AtomicBoolean(false)
    private val sequence = AtomicInteger(0)
    private val missingSessionFrames = AtomicInteger(0)
    private val missingEndpointFrames = AtomicInteger(0)
    private val capturedFrames = AtomicInteger(0)
    private val sentFrames = AtomicInteger(0)
    private val failedFrames = AtomicInteger(0)
    private val retransmitManager = VoiceRetransmitManager()
    private val captureWorker =
        AudioCaptureWorker { frame ->
            val captured = capturedFrames.incrementAndGet()
            if (captured == 1 || captured % 50 == 0) {
                Log.d("GHALBIT-CALL-AUDIO-CAPTURE", "frames=$captured bytes=${frame.size}")
            }
            if (captured == 1) {
                onTruthEvent?.invoke("capture", "frames=$captured bytes=${frame.size}")
            }
            if (muted.get()) return@AudioCaptureWorker
            val session = sessionProvider()
            if (session == null) {
                val count = missingSessionFrames.incrementAndGet()
                if (count == 1 || count % 50 == 0) {
                    Log.w("GHALBIT-CALL-RTC", "drop capture missing session count=$count")
                }
                if (count == 5) {
                    onRealtimeFailure("Sesi suara belum siap")
                }
                return@AudioCaptureWorker
            }
            val endpoint = endpointProvider()
            if (endpoint == null || (endpoint.routeHint.isNullOrBlank() && endpoint.transportIp.isNullOrBlank())) {
                val count = missingEndpointFrames.incrementAndGet()
                if (count == 1 || count % 50 == 0) {
                    Log.w("GHALBIT-CALL-RTC", "drop capture missing endpoint count=$count route=${endpoint?.routeHint ?: endpoint?.transportIp ?: "-"}")
                }
                if (count == 5) {
                    onRealtimeFailure("Jalur suara belum siap")
                }
                return@AudioCaptureWorker
            }
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
            if (sent) {
                val total = sentFrames.incrementAndGet()
                if (total == 1 || total % 50 == 0) {
                    Log.d("GHALBIT-CALL-RTC", "capture sent seq=${voicePacket.sequence} total=$total")
                }
                if (total == 1) {
                    onTruthEvent?.invoke("tx", "seq=${voicePacket.sequence} bytes=${voicePacket.payload.size} total=$total")
                }
            } else {
                val failed = failedFrames.incrementAndGet()
                Log.w("GHALBIT-CALL-RTC", "capture send failed seq=${voicePacket.sequence} failed=$failed")
                if (failed == 3 || failed % 25 == 0) {
                    onRealtimeFailure("Realtime call tidak stabil")
                }
            }
        }
    private val jitterBuffer = AudioPacketJitterBuffer(frameBytes = captureWorker.frameBytes)
    private val playbackWorker =
        AudioPlaybackWorker(
            jitterBuffer = jitterBuffer,
            frameBytes = captureWorker.frameBytes,
            onPlaybackFrame = { sequence, written, realFrame, _, safeMode ->
                if (realFrame && written > 0 && sequence != null && (sequence == 1 || sequence % 25 == 0)) {
                    onTruthEvent?.invoke("play", "seq=$sequence written=$written safeMode=$safeMode")
                }
            }
        )

    fun start() {
        if (!running.compareAndSet(false, true)) {
            Log.d("GHALBIT-CALL-RTC", "start ignored already running")
            return
        }
        missingSessionFrames.set(0)
        missingEndpointFrames.set(0)
        capturedFrames.set(0)
        sentFrames.set(0)
        failedFrames.set(0)
        Log.d("GHALBIT-CALL-RTC", "start realtime engine")
        Log.d("GHALBIT-CALL-RTC", "sessionReady=${sessionProvider() != null} endpointReady=${endpointProvider() != null}")
        playbackWorker.start()
        captureWorker.start()
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        Log.d("GHALBIT-CALL-RTC", "stop realtime engine sent=${sentFrames.get()} failed=${failedFrames.get()} missingSession=${missingSessionFrames.get()} missingEndpoint=${missingEndpointFrames.get()}")
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
        if (voicePacket.sequence == 1 || voicePacket.sequence % 25 == 0) {
            onTruthEvent?.invoke("rx", "seq=${voicePacket.sequence} bytes=${voicePacket.payload.size}")
        }
    }

    fun audioMetricsSnapshot(): AudioRuntimeMetrics {
        val m = jitterBuffer.metricsSnapshot()
        return AudioRuntimeMetrics(
            capturedFrames = capturedFrames.get().toLong(),
            txFrames = sentFrames.get().toLong(),
            txFailedFrames = failedFrames.get().toLong(),
            rxFrames = m.rxFrames,
            queuedFrames = m.queuedFrames,
            playedFrames = m.playedFrames,
            droppedFrames = m.droppedFrames,
            concealFrames = m.concealFrames,
            rxActiveForMs = m.rxActiveForMs
        )
    }

    fun enableSafePlaybackMode(enabled: Boolean) {
        playbackWorker.setSafeMode(enabled)
    }
}
