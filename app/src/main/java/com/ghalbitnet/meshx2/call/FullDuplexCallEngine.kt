package com.ghalbitnet.meshx2.call

import android.util.Base64
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
    private val virtualLoopbackFrames = AtomicInteger(0)
    private val retransmitManager = VoiceRetransmitManager()
    @Volatile private var captureStartedAt = 0L
    private val captureWorker =
        AudioCaptureWorker { frame ->
            val captured = capturedFrames.incrementAndGet()
            if (captured == 1 || captured % 50 == 0) {
                Log.d("GHALBIT-CALL-AUDIO-CAPTURE", "frames=$captured bytes=${frame.size}")
            }
            if (captured == 1) {
                onTruthEvent?.invoke("capture", "frames=$captured bytes=${frame.size}")
            }
            if (captured == 1 || captured % 50 == 0) {
                Log.d("GHALBIT-CALL-AUDIO-BRIDGE", "CAPTURE_READY frames=$captured bytes=${frame.size}")
            }
            if (captured == 1) {
                onTruthEvent?.invoke("capture_ready", "frames=$captured bytes=${frame.size}")
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
            val targetRoute = endpoint.routeHint ?: endpoint.transportIp ?: "unknown"
            val encodedAudio = Base64.encodeToString(frame, Base64.NO_WRAP)
            if (captured == 1 || captured % 50 == 0) {
                Log.d("GHALBIT-CALL-AUDIO-BRIDGE", "ENCODE_START route=$targetRoute frames=$captured")
                Log.d("GHALBIT-CALL-AUDIO-BRIDGE", "ENCODE_OK bytes=${encodedAudio.length} route=$targetRoute")
                Log.d("GHALBIT-CALL-AUDIO-BRIDGE", "PACKET_READY bytes=${frame.size} encoded=${encodedAudio.length} route=$targetRoute")
                Log.d("GHALBIT-CALL-AUDIO-BRIDGE", "TX_ATTEMPT route=$targetRoute")
            }
            if (captured == 1) {
                onTruthEvent?.invoke("encode_ok", "encodedBytes=${encodedAudio.length} route=$targetRoute")
                onTruthEvent?.invoke("tx_attempt", "route=$targetRoute bytes=${frame.size}")
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
                if (isVirtualDiagnosticEndpoint(endpoint)) {
                    deliverToVirtualSink(voicePacket)
                } else {
                    CallManager.sendVoicePacket(
                        peer = endpoint,
                        localNodeId = session.localNodeId,
                        packet = voicePacket
                    )
                }
            if (sent) {
                val total = sentFrames.incrementAndGet()
                if (total == 1 || total % 50 == 0) {
                    Log.d("GHALBIT-CALL-RTC", "capture sent seq=${voicePacket.sequence} total=$total")
                    Log.d(
                        "GHALBIT-CALL-AUDIO-BRIDGE",
                        "TX_SUCCESS frames=$total route=${if (isVirtualDiagnosticEndpoint(endpoint)) "virtual_diagnostic" else targetRoute}"
                    )
                }
                if (total == 1) {
                    onTruthEvent?.invoke(
                        "tx",
                        "seq=${voicePacket.sequence} bytes=${voicePacket.payload.size} total=$total route=${if (isVirtualDiagnosticEndpoint(endpoint)) "virtual_diagnostic" else targetRoute}"
                    )
                }
            } else {
                val failed = failedFrames.incrementAndGet()
                Log.w("GHALBIT-CALL-RTC", "capture send failed seq=${voicePacket.sequence} failed=$failed")
                if (failed == 1 || failed % 50 == 0) {
                    Log.w("GHALBIT-CALL-AUDIO-BRIDGE", "TX_FAIL reason=send_failed route=$targetRoute failed=$failed")
                }
                if (failed == 1) {
                    onTruthEvent?.invoke("tx_fail", "reason=send_failed route=$targetRoute failed=$failed")
                }
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
        virtualLoopbackFrames.set(0)
        captureStartedAt = System.currentTimeMillis()
        Log.d("GHALBIT-CALL-RTC", "start realtime engine")
        Log.d("GHALBIT-CALL-RTC", "sessionReady=${sessionProvider() != null} endpointReady=${endpointProvider() != null}")
        playbackWorker.start()
        captureWorker.start()
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        val durationMs = if (captureStartedAt == 0L) 0L else System.currentTimeMillis() - captureStartedAt
        Log.d("GHALBIT-CALL-RTC", "stop realtime engine sent=${sentFrames.get()} failed=${failedFrames.get()} missingSession=${missingSessionFrames.get()} missingEndpoint=${missingEndpointFrames.get()}")
        Log.d("GHALBIT-CALL-AUDIO-BRIDGE", "CAPTURE_STOP reason=stop_called durationMs=$durationMs sent=${sentFrames.get()} failed=${failedFrames.get()} virtualLoopback=${virtualLoopbackFrames.get()}")
        onTruthEvent?.invoke(
            "capture_stop",
            "reason=stop_called durationMs=$durationMs sent=${sentFrames.get()} failed=${failedFrames.get()} virtualLoopback=${virtualLoopbackFrames.get()}"
        )
        captureWorker.stop()
        playbackWorker.stop()
        jitterBuffer.clear()
        retransmitManager.clearSession()
        sequence.set(0)
        captureStartedAt = 0L
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

    private fun isVirtualDiagnosticEndpoint(endpoint: CallPeerEndpoint): Boolean {
        val route = endpoint.routeHint ?: endpoint.transportIp ?: ""
        return route.startsWith("virtual://", ignoreCase = true) || endpoint.nodeId == "VIRTUAL_CALLER_PC"
    }

    private fun deliverToVirtualSink(packet: VoicePacket): Boolean {
        val loopback = virtualLoopbackFrames.incrementAndGet()
        jitterBuffer.offer(packet.sequence, packet.payload)
        Log.d("GHALBIT-CALL-AUDIO-TX", "frames=$loopback route=virtual_diagnostic")
        Log.d("GHALBIT-CALL-AUDIO-RX", "seq=${packet.sequence} bytes=${packet.payload.size} route=virtual_diagnostic")
        if (loopback == 1 || loopback % 50 == 0) {
            onTruthEvent?.invoke("rx", "seq=${packet.sequence} bytes=${packet.payload.size} route=virtual_diagnostic")
        }
        return true
    }
}
