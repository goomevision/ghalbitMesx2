package com.ghalbitnet.meshx2.diagnostics.virtualcall

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.ghalbitnet.meshx2.call.CallRingtoneManager
import com.ghalbitnet.meshx2.call.CallState
import com.ghalbitnet.meshx2.call.VoiceCallRegistry
import com.ghalbitnet.meshx2.diagnostics.audio.AudioTruthProbe
import com.ghalbitnet.meshx2.diagnostics.recovery.SmartRecoveryEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

object OneDeviceIncomingCallDiagnostic {
    fun run(context: Context, scenario: VirtualCallScenario): VirtualCallResult = runBlocking {
        val notes = mutableListOf<String>()
        Log.i("GHALBIT-VIRTUAL-CALL", "START caller=${scenario.callerPeerId}")

        val callId = VirtualCallerTool.triggerIncomingCall(context, scenario)
        Log.i("GHALBIT-VIRTUAL-CALL", "RINGING callId=$callId")

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
        } else {
            notes += "User did not accept within timeout."
        }

        val connected = waitForState(callId, 20_000L) { state ->
            state == CallState.CONNECTED ||
                state == CallState.CALL_CONNECTED_SIGNAL_ONLY ||
                state == CallState.VOICE_STREAM_ACTIVE
        }

        if (connected) {
            Log.i("GHALBIT-VIRTUAL-CALL", "CONNECTED callId=$callId")
            notes += "Prompt user speech: ${scenario.speechPrompt}"
            delay(scenario.audioProbeMs)
        }

        val audio = AudioTruthProbe.run(context)
        Log.i(
            "GHALBIT-VIRTUAL-CALL",
            "AUDIO_CAPTURE rms=${"%.2f".format(audio.rms)} peak=${audio.peak} speech=${audio.speechDetected}"
        )

        val ringtoneStopped = !CallRingtoneManager.isPlaying()
        Log.i("GHALBIT-VIRTUAL-CALL", "RINGTONE_STOPPED result=$ringtoneStopped")

        // We do not force-end advanced call pipeline; mark ended if registry moved out from active states.
        val ended = waitForState(callId, 6_000L) { state ->
            state == CallState.ENDED || state == CallState.CALL_ENDED || state == CallState.REJECTED || state == CallState.IDLE
        }
        if (ended) {
            Log.i("GHALBIT-VIRTUAL-CALL", "ENDED callId=$callId")
        }

        val recovery = SmartRecoveryEngine.run(context)
        notes += "Recovery recovered=${recovery.recovered} pending=${recovery.pending} failed=${recovery.failed}"

        val status = when {
            !incomingShown -> "FAIL_NO_INCOMING"
            !accepted -> "PARTIAL_NOT_ACCEPTED"
            !connected -> "PARTIAL_NOT_CONNECTED"
            !ringtoneStopped -> "PARTIAL_RINGTONE_STUCK"
            else -> "PASS"
        }

        Log.i("GHALBIT-VIRTUAL-CALL", "RESULT status=$status")
        VirtualCallResult(
            callId = callId,
            incomingShown = incomingShown,
            accepted = accepted,
            connected = connected,
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

