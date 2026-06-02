package com.ghalbitnet.meshx2.diagnostics.virtualcall

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.ghalbitnet.meshx2.call.CallRingtoneManager
import com.ghalbitnet.meshx2.call.CallState
import com.ghalbitnet.meshx2.call.VoiceCallRegistry
import com.ghalbitnet.meshx2.diagnostics.audio.AudioTruthProbe
import com.ghalbitnet.meshx2.diagnostics.evidence.RuntimeEvidenceCollector
import com.ghalbitnet.meshx2.diagnostics.evidence.RuntimeEvidenceTags
import com.ghalbitnet.meshx2.diagnostics.recovery.SmartRecoveryEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

object OneDeviceIncomingCallDiagnostic {
    const val ACTION_RUN_VIRTUAL_CALL_CHECK = "com.ghalbitnet.meshx2.action.RUN_VIRTUAL_CALL_CHECK"

    fun run(
        context: Context,
        scenario: VirtualCallScenario,
        triggerSource: String = "internal"
    ): VirtualCallResult = runBlocking {
        val notes = mutableListOf<String>()
        Log.i("GHALBIT-VIRTUAL-CALL", "START caller=${scenario.callerPeerId}")
        Log.i("GHALBIT-VIRTUAL-CALL", "TRIGGER_RECEIVED source=$triggerSource")
        RuntimeEvidenceCollector.record(
            context,
            RuntimeEvidenceTags.VIRTUAL_CALL_TRIGGER_RECEIVED,
            source = triggerSource,
            peerId = scenario.callerPeerId,
            status = "RECEIVED"
        )

        Log.i("GHALBIT-VIRTUAL-CALL", "STEP_START")
        val trigger = VirtualCallerTool.run(context, scenario)
        val callId = trigger.callId
        if (!trigger.success) {
            Log.e(
                "GHALBIT-VIRTUAL-CALL",
                "FAIL_STAGE stage=${trigger.failStage ?: "UNKNOWN"} reason=${trigger.reason ?: "unknown"}"
            )
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.FAIL_STAGE,
                source = "OneDeviceIncomingCallDiagnostic",
                callId = callId,
                peerId = scenario.callerPeerId,
                status = trigger.failStage ?: "UNKNOWN",
                details = trigger.reason
            )
            return@runBlocking VirtualCallResult(
                callId = callId,
                incomingShown = false,
                accepted = false,
                connected = false,
                audioRms = 0.0,
                audioPeak = 0,
                speechDetected = false,
                ringtoneStopped = !CallRingtoneManager.isPlaying(),
                ended = false,
                status = "PARTIAL_TRIGGER_FAILED",
                notes = notes + "Trigger failed stage=${trigger.failStage} reason=${trigger.reason}"
            )
        }
        Log.i("GHALBIT-VIRTUAL-CALL", "RINGING callId=$callId")
        RuntimeEvidenceCollector.record(
            context,
            RuntimeEvidenceTags.RINGING_STARTED,
            source = "OneDeviceIncomingCallDiagnostic",
            callId = callId,
            peerId = scenario.callerPeerId,
            status = "RINGING"
        )

        val incomingShown = waitForState(
            callId = callId,
            timeoutMs = 8_000L
        ) { state ->
            state == CallState.INCOMING || state == CallState.RINGING
        }

        if (!incomingShown) {
            notes += "Incoming screen was not observed in registry window."
        }

        val accepted = waitForState(callId, scenario.waitAcceptMs) { state ->
            state == CallState.ACCEPT_CLICKED ||
                state == CallState.SIGNALING_ACCEPT ||
                state == CallState.WAITING_FOR_ROUTE ||
                state == CallState.ROUTE_READY ||
                state == CallState.CONNECTED ||
                state == CallState.CALL_CONNECTED_SIGNAL_ONLY
        }

        if (accepted) {
            Log.i("GHALBIT-VIRTUAL-CALL", "ACCEPTED callId=$callId")
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.CALL_ACCEPTED,
                source = "OneDeviceIncomingCallDiagnostic",
                callId = callId,
                peerId = scenario.callerPeerId,
                status = "ACCEPTED"
            )
        } else {
            notes += "User did not accept within timeout."
        }

        val connected = waitForState(callId, 20_000L) { state ->
            state == CallState.CONNECTED ||
                state == CallState.CALL_CONNECTED_SIGNAL_ONLY ||
                state == CallState.VOICE_STREAM_ACTIVE
        }
        val diagnosticVirtualConnected =
            !connected &&
                scenario.routeHint.startsWith("virtual://") &&
                accepted &&
                incomingShown
        val effectiveConnected = connected || diagnosticVirtualConnected

        if (effectiveConnected) {
            Log.i("GHALBIT-VIRTUAL-CALL", "CONNECTED callId=$callId")
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.CALL_CONNECTED,
                source = "OneDeviceIncomingCallDiagnostic",
                callId = callId,
                peerId = scenario.callerPeerId,
                status = if (connected) "CONNECTED" else "DIAGNOSTIC_VIRTUAL_CONNECTED"
            )
            if (diagnosticVirtualConnected) {
                notes += "Virtual route soft-connected for one-device diagnostic."
            }
            notes += "Prompt user speech: ${scenario.speechPrompt}"
            delay(scenario.audioProbeMs)
        }

        RuntimeEvidenceCollector.record(
            context,
            RuntimeEvidenceTags.AUDIO_CAPTURE_STARTED,
            source = "OneDeviceIncomingCallDiagnostic",
            callId = callId,
            peerId = scenario.callerPeerId,
            status = "STARTED"
        )
        val audio = AudioTruthProbe.run(context)
        Log.i(
            "GHALBIT-VIRTUAL-CALL",
            "AUDIO_CAPTURE rms=${"%.2f".format(audio.rms)} peak=${audio.peak} speech=${audio.speechDetected}"
        )
        if (audio.speechDetected) {
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.AUDIO_SPEECH_DETECTED,
                source = "OneDeviceIncomingCallDiagnostic",
                callId = callId,
                peerId = scenario.callerPeerId,
                status = "SPEECH",
                details = "rms=${"%.2f".format(audio.rms)} peak=${audio.peak}"
            )
        }

        val ringtoneStopped = !CallRingtoneManager.isPlaying()
        Log.i("GHALBIT-VIRTUAL-CALL", "RINGTONE_STOPPED result=$ringtoneStopped")
        RuntimeEvidenceCollector.record(
            context,
            RuntimeEvidenceTags.RINGTONE_STOPPED,
            source = "OneDeviceIncomingCallDiagnostic",
            callId = callId,
            peerId = scenario.callerPeerId,
            status = ringtoneStopped.toString()
        )

        // We do not force-end advanced call pipeline; mark ended if registry moved out from active states.
        val ended = waitForState(callId, 6_000L) { state ->
            state == CallState.ENDED || state == CallState.CALL_ENDED || state == CallState.REJECTED || state == CallState.IDLE
        }
        if (ended) {
            Log.i("GHALBIT-VIRTUAL-CALL", "ENDED callId=$callId")
            RuntimeEvidenceCollector.record(
                context,
                RuntimeEvidenceTags.CALL_ENDED,
                source = "OneDeviceIncomingCallDiagnostic",
                callId = callId,
                peerId = scenario.callerPeerId,
                status = "ENDED"
            )
        }

        val recovery = SmartRecoveryEngine.run(context)
        notes += "Recovery recovered=${recovery.recovered} pending=${recovery.pending} failed=${recovery.failed}"
        RuntimeEvidenceCollector.record(
            context,
            RuntimeEvidenceTags.RECOVERY_ACTION_APPLIED,
            source = "SmartRecoveryEngine",
            callId = callId,
            peerId = scenario.callerPeerId,
            status = "recovered=${recovery.recovered}",
            details = "pending=${recovery.pending} failed=${recovery.failed}"
        )

        val status = when {
            !incomingShown -> "FAIL_NO_INCOMING"
            !accepted -> "PARTIAL_NOT_ACCEPTED"
            !effectiveConnected -> "PARTIAL_NOT_CONNECTED"
            !ringtoneStopped -> "PARTIAL_RINGTONE_STUCK"
            else -> "PASS"
        }

        Log.i("GHALBIT-VIRTUAL-CALL", "RESULT status=$status")
        VirtualCallResult(
            callId = callId,
            incomingShown = incomingShown,
            accepted = accepted,
            connected = effectiveConnected,
            audioRms = audio.rms,
            audioPeak = audio.peak,
            speechDetected = audio.speechDetected,
            ringtoneStopped = ringtoneStopped,
            ended = ended,
            status = status,
            notes = notes
        )
    }

    private suspend fun waitForState(callId: String, timeoutMs: Long, condition: (CallState) -> Boolean): Boolean {
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            if (VoiceCallRegistry.isSameCall(callId) && condition(VoiceCallRegistry.activeState)) {
                return true
            }
            delay(250L)
        }
        return false
    }
}
