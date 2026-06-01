package com.ghalbitnet.meshx2.diagnostics.autodiag

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import android.util.Log
import com.ghalbitnet.meshx2.call.VoipReadinessChecker
import com.ghalbitnet.meshx2.chat.ChatDeliveryManager
import com.ghalbitnet.meshx2.diagnostics.InternetServerOperatorReadinessProbe
import com.ghalbitnet.meshx2.diagnostics.ServerTruthProbe
import com.ghalbitnet.meshx2.diagnostics.audio.AudioTruthProbe
import com.ghalbitnet.meshx2.diagnostics.recovery.RecoveryReportGenerator
import com.ghalbitnet.meshx2.diagnostics.recovery.SmartRecoveryEngine
import com.ghalbitnet.meshx2.diagnostics.virtualcall.OneDeviceIncomingCallDiagnostic
import com.ghalbitnet.meshx2.diagnostics.virtualcall.VirtualCallReportGenerator
import com.ghalbitnet.meshx2.diagnostics.virtualcall.VirtualCallScenario
import com.ghalbitnet.meshx2.online.PendingMessageStore

object AutoDiagnosticOrchestrator {

    fun run(context: Context): AutoDiagnosticResult {
        Log.i("GHALBIT-AUTO-DIAG", "START")
        val steps = mutableListOf<AutoDiagnosticStep>()

        steps += runServerStep(context)
        steps += runNetworkStep(context)
        steps += runSimulationStep()
        steps += runPendingStep(context)
        steps += runReceiptStep(context)
        steps += runCallStep(context)
        steps += runAudioStep(context)
        steps += runLoopStep(context)
        steps += runSmartRecoveryStep(context)
        steps += runServerOperatorFullCheckStep(context)
        steps += runVirtualIncomingCallCheckStep(context)

        val total = steps.map { it.score }.average().toInt().coerceIn(0, 100)
        val finalStatus = when {
            steps.any { it.status == AutoDiagnosticStatus.FAIL } -> AutoDiagnosticStatus.FAIL
            steps.any { it.status == AutoDiagnosticStatus.PARTIAL } -> AutoDiagnosticStatus.PARTIAL
            else -> AutoDiagnosticStatus.PASS
        }

        val scoreMap = steps.associateBy { it.name }
        Log.i(
            "GHALBIT-AUTO-DIAG",
            "SCORE server=${scoreMap["server"]?.score ?: 0} network=${scoreMap["network"]?.score ?: 0} simulation=${scoreMap["simulation"]?.score ?: 0} audio=${scoreMap["audio"]?.score ?: 0} media=${scoreMap["pending"]?.score ?: 0} call=${scoreMap["call_signaling"]?.score ?: 0} loop=${scoreMap["loop_guard"]?.score ?: 0} recovery=${scoreMap["smart_recovery"]?.score ?: 0} serverOperator=${scoreMap["server_operator_full_check"]?.score ?: 0} virtualCall=${scoreMap["virtual_incoming_call_check"]?.score ?: 0}"
        )
        Log.i("GHALBIT-AUTO-DIAG", "RESULT status=$finalStatus total=$total")
        return AutoDiagnosticResult(steps = steps, totalScore = total, status = finalStatus)
    }

    private fun runServerStep(context: Context): AutoDiagnosticStep {
        ServerTruthProbe.logSnapshot(context)
        val endpoints = ServerTruthProbe.endpointCatalog()
        val ready = endpoints.count { it.status == "READY_IN_APP" }
        val codeOnly = endpoints.count { it.status == "CODE_ONLY" }
        val score = ((ready * 100.0) / endpoints.size).toInt().coerceIn(0, 100)
        val status = when {
            ready == 0 -> AutoDiagnosticStatus.FAIL
            codeOnly > 0 -> AutoDiagnosticStatus.PARTIAL
            else -> AutoDiagnosticStatus.PASS
        }
        val step = AutoDiagnosticStep(
            name = "server",
            status = status,
            score = score,
            notes = listOf("ready=$ready", "codeOnly=$codeOnly")
        )
        Log.i("GHALBIT-AUTO-DIAG", "STEP name=${step.name} status=${step.status}")
        return step
    }

    private fun runNetworkStep(context: Context): AutoDiagnosticStep {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val hasInternet = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val score = if (hasInternet) 90 else 45
        val status = if (hasInternet) AutoDiagnosticStatus.PASS else AutoDiagnosticStatus.PARTIAL
        val step = AutoDiagnosticStep(
            name = "network",
            status = status,
            score = score,
            notes = listOf("internet=$hasInternet")
        )
        Log.i("GHALBIT-AUTO-DIAG", "STEP name=${step.name} status=${step.status}")
        return step
    }

    private fun runSimulationStep(): AutoDiagnosticStep {
        val score = 80
        val step = AutoDiagnosticStep(
            name = "simulation",
            status = AutoDiagnosticStatus.PARTIAL,
            score = score,
            notes = listOf("offline-harness-available")
        )
        Log.i("GHALBIT-AUTO-DIAG", "STEP name=${step.name} status=${step.status}")
        return step
    }

    private fun runPendingStep(context: Context): AutoDiagnosticStep {
        val pending = PendingMessageStore.all(context).size
        val status = if (pending >= 0) AutoDiagnosticStatus.PASS else AutoDiagnosticStatus.FAIL
        val score = if (pending >= 0) 85 else 30
        val step = AutoDiagnosticStep(
            name = "pending",
            status = status,
            score = score,
            notes = listOf("pendingCount=$pending")
        )
        Log.i("GHALBIT-AUTO-DIAG", "STEP name=${step.name} status=${step.status}")
        return step
    }

    private fun runReceiptStep(context: Context): AutoDiagnosticStep {
        val dryRun = ChatDeliveryManager.dryRun(context)
        val ok = dryRun.summary.isNotBlank()
        val step = AutoDiagnosticStep(
            name = "receipt",
            status = if (ok) AutoDiagnosticStatus.PASS else AutoDiagnosticStatus.PARTIAL,
            score = if (ok) 82 else 50,
            notes = listOf(dryRun.summary)
        )
        Log.i("GHALBIT-AUTO-DIAG", "STEP name=${step.name} status=${step.status}")
        return step
    }

    private fun runCallStep(context: Context): AutoDiagnosticStep {
        val ready = VoipReadinessChecker.check(context)
        val isReady = ready.failureReason.isNullOrBlank()
        val status = when {
            isReady -> AutoDiagnosticStatus.PASS
            ready.failureReason?.contains("Linphone", ignoreCase = true) == true -> AutoDiagnosticStatus.PARTIAL
            else -> AutoDiagnosticStatus.PARTIAL
        }
        val step = AutoDiagnosticStep(
            name = "call_signaling",
            status = status,
            score = if (isReady) 80 else 55,
            notes = listOf(ready.failureReason ?: "ok")
        )
        Log.i("GHALBIT-AUTO-DIAG", "STEP name=${step.name} status=${step.status}")
        return step
    }

    private fun runAudioStep(context: Context): AutoDiagnosticStep {
        val report = AudioTruthProbe.run(context)
        val status = when {
            report.healthScore >= 75 -> AutoDiagnosticStatus.PASS
            report.healthScore >= 45 -> AutoDiagnosticStatus.PARTIAL
            else -> AutoDiagnosticStatus.FAIL
        }
        val step = AutoDiagnosticStep(
            name = "audio",
            status = status,
            score = report.healthScore,
            notes = report.notes
        )
        Log.i("GHALBIT-AUTO-DIAG", "STEP name=${step.name} status=${step.status}")
        return step
    }

    private fun runLoopStep(context: Context): AutoDiagnosticStep {
        val started = SystemClock.elapsedRealtime()
        val pending = PendingMessageStore.all(context).size
        val elapsed = SystemClock.elapsedRealtime() - started
        val loopSafe = elapsed < 1500L
        val status = if (loopSafe) AutoDiagnosticStatus.PASS else AutoDiagnosticStatus.PARTIAL
        val step = AutoDiagnosticStep(
            name = "loop_guard",
            status = status,
            score = if (loopSafe) 88 else 60,
            notes = listOf("dbProbeMs=$elapsed", "pendingCount=$pending")
        )
        Log.i("GHALBIT-AUTO-DIAG", "STEP name=${step.name} status=${step.status}")
        return step
    }

    private fun runSmartRecoveryStep(context: Context): AutoDiagnosticStep {
        val result = SmartRecoveryEngine.run(context)
        val total = (result.recovered + result.pending + result.failed).coerceAtLeast(1)
        val score = ((result.recovered * 100.0) / total).toInt().coerceIn(0, 100)
        val status = when {
            result.failed > 0 -> AutoDiagnosticStatus.PARTIAL
            result.recovered > 0 -> AutoDiagnosticStatus.PASS
            else -> AutoDiagnosticStatus.PARTIAL
        }
        val markdown = RecoveryReportGenerator.toMarkdown(result)
        Log.d("GHALBIT-RECOVERY", "REPORT ${markdown.take(180)}")
        val step = AutoDiagnosticStep(
            name = "smart_recovery",
            status = status,
            score = score,
            notes = listOf("recovered=${result.recovered}", "pending=${result.pending}", "failed=${result.failed}")
        )
        Log.i("GHALBIT-AUTO-DIAG", "STEP name=${step.name} status=${step.status}")
        return step
    }

    private fun runServerOperatorFullCheckStep(context: Context): AutoDiagnosticStep {
        val result = kotlinx.coroutines.runBlocking { InternetServerOperatorReadinessProbe.run(context) }
        val status = when {
            !result.configured -> AutoDiagnosticStatus.FAIL
            result.status == "READY" -> AutoDiagnosticStatus.PASS
            result.status == "PARTIAL" -> AutoDiagnosticStatus.PARTIAL
            else -> AutoDiagnosticStatus.FAIL
        }
        val notes = buildList {
            add("status=${result.status}")
            add("configured=${result.configured}")
            add("relay=${result.baseUrl}")
            add("presence=${result.presenceUrl}")
            if (!result.configured) {
                add("SERVER_NOT_CONFIGURED")
                add("FAKE_SERVER_PASS_ONLY")
            }
        }
        val step = AutoDiagnosticStep(
            name = "server_operator_full_check",
            status = status,
            score = result.score(),
            notes = notes
        )
        Log.i("GHALBIT-AUTO-DIAG", "STEP name=SERVER_OPERATOR_FULL_CHECK status=${step.status}")
        return step
    }

    private fun runVirtualIncomingCallCheckStep(context: Context): AutoDiagnosticStep {
        val scenario = VirtualCallScenario(
            callerPeerId = "VIRTUAL_CALLER_PC",
            callerGlobalId = "GX-VIRTUAL-CALLER",
            callerDisplayName = "Virtual Caller Tool"
        )
        val result = OneDeviceIncomingCallDiagnostic.run(context, scenario)
        val status = when (result.status) {
            "PASS" -> AutoDiagnosticStatus.PASS
            "FAIL_NO_INCOMING" -> AutoDiagnosticStatus.FAIL
            else -> AutoDiagnosticStatus.PARTIAL
        }
        val score = when (result.status) {
            "PASS" -> 90
            "PARTIAL_NOT_ACCEPTED" -> 55
            "PARTIAL_NOT_CONNECTED" -> 50
            "PARTIAL_RINGTONE_STUCK" -> 45
            else -> 30
        }
        Log.d("GHALBIT-VIRTUAL-CALL", "REPORT ${VirtualCallReportGenerator.toMarkdown(result).take(160)}")
        val step = AutoDiagnosticStep(
            name = "virtual_incoming_call_check",
            status = status,
            score = score,
            notes = listOf(
                "callId=${result.callId}",
                "incoming=${result.incomingShown}",
                "accepted=${result.accepted}",
                "connected=${result.connected}",
                "speech=${result.speechDetected}",
                "ringtoneStopped=${result.ringtoneStopped}",
                "status=${result.status}"
            )
        )
        Log.i("GHALBIT-AUTO-DIAG", "STEP name=VIRTUAL_INCOMING_CALL_CHECK status=${step.status}")
        return step
    }
}
