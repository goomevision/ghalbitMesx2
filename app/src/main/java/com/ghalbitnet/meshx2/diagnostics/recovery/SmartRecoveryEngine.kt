package com.ghalbitnet.meshx2.diagnostics.recovery

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.call.VoipReadinessChecker
import com.ghalbitnet.meshx2.chat.ChatDeliveryManager
import com.ghalbitnet.meshx2.diagnostics.InternetServerOperatorReadinessProbe
import com.ghalbitnet.meshx2.diagnostics.ServerTruthProbe
import com.ghalbitnet.meshx2.diagnostics.audio.AudioTruthProbe
import com.ghalbitnet.meshx2.online.PendingMessageStore

object SmartRecoveryEngine {

    fun run(context: Context): RecoveryRunResult {
        val signals = collectSignals(context)
        return runWithSignals(signals)
    }

    fun runWithSignals(signals: List<RecoverySignal>): RecoveryRunResult {
        val issues = signals.mapNotNull {
            val issue = ErrorClassifier.classify(it)
            if (issue != null) {
                safeLog { Log.w("GHALBIT-RECOVERY", "DETECT error=${issue.type} source=${issue.source}") }
                safeLog { Log.w("GHALBIT-RECOVERY", "CLASSIFY type=${issue.type} severity=${issue.severity}") }
            }
            issue
        }

        val actions = issues.map { issue ->
            val action = RecoveryPolicy.decide(issue)
            if (action.applied) {
                safeLog { Log.i("GHALBIT-RECOVERY", "ACTION name=${action.name} result=${action.result}") }
            } else {
                safeLog { Log.i("GHALBIT-RECOVERY", "SKIP reason=${issue.type}") }
            }
            action
        }

        val suggestions = issues.map { issue ->
            val s = suggestionFor(issue)
            safeLog { Log.i("GHALBIT-RECOVERY", "SUGGEST file=${s.file} action=${s.safePatch}") }
            s
        }

        val recovered = actions.count { it.applied && !it.requiresHumanApproval }
        val pending = actions.count { it.requiresHumanApproval }
        val failed = issues.size - recovered - pending
        safeLog { Log.i("GHALBIT-RECOVERY", "RESULT recovered=$recovered pending=$pending failed=$failed") }
        return RecoveryRunResult(
            issues = issues,
            actions = actions,
            suggestions = suggestions,
            recovered = recovered.coerceAtLeast(0),
            pending = pending.coerceAtLeast(0),
            failed = failed.coerceAtLeast(0)
        )
    }

    private inline fun safeLog(block: () -> Unit) {
        runCatching { block() }
    }

    private fun collectSignals(context: Context): List<RecoverySignal> {
        val signals = mutableListOf<RecoverySignal>()
        val urls = ServerTruthProbe.baseUrls()
        if (urls["relayBaseUrl"].isNullOrBlank()) {
            signals += RecoverySignal("server.baseUrl", "")
        }
        val serverReadiness = runCatching { kotlinx.coroutines.runBlocking { InternetServerOperatorReadinessProbe.run(context) } }.getOrNull()
        if (serverReadiness != null) {
            when {
                !serverReadiness.configured -> signals += RecoverySignal("server.status", "SERVER_NOT_CONFIGURED")
                serverReadiness.status == "FAILED" -> signals += RecoverySignal("server.status", "SERVER_DOWN")
            }
            serverReadiness.checks.forEach { check ->
                if (!check.ok) {
                    when (check.code) {
                        401, 403 -> signals += RecoverySignal("server.status", "AUTH_REQUIRED")
                        404 -> signals += RecoverySignal("server.status", "ENDPOINT_MISSING")
                        500 -> signals += RecoverySignal("server.status", "SERVER_ERROR")
                    }
                    val detail = (check.detail.ifBlank { "" } + " " + check.name).uppercase()
                    if (detail.contains("TIMEOUT")) signals += RecoverySignal("server.error", "TIMEOUT")
                    if (detail.contains("ECONNREFUSED") || detail.contains("CONNECTION REFUSED")) {
                        signals += RecoverySignal("server.status", "SERVER_DOWN")
                    }
                }
            }
        }
        val pending = PendingMessageStore.all(context)
        if (pending.size > 50) {
            signals += RecoverySignal("pending.queue", "STUCK count=${pending.size}")
        }
        if (pending.any { it.deliveryStatus.name.contains("FAILED", ignoreCase = true) && (it.expiresAt == 0L || it.expiresAt > System.currentTimeMillis()) }) {
            signals += RecoverySignal("chat.failed", "BEFORE_TTL")
        }

        val dryRun = ChatDeliveryManager.dryRun(context).summary
        if (dryRun.contains("duplicate", ignoreCase = true)) {
            signals += RecoverySignal("chat.message", "DUPLICATE")
        }
        if (dryRun.contains("receipt mismatch", ignoreCase = true)) {
            signals += RecoverySignal("receipt.state", "MISMATCH")
        }

        val readiness = VoipReadinessChecker.check(context)
        val reason = readiness.failureReason.orEmpty()
        if (reason.contains("ringing", ignoreCase = true)) {
            signals += RecoverySignal("call.state", "STUCK_RINGING")
        }
        if (reason.contains("accept", ignoreCase = true) && reason.contains("connected", ignoreCase = true).not()) {
            signals += RecoverySignal("call.state", "ACCEPT_NOT_CONNECTED")
        }

        val audio = AudioTruthProbe.run(context)
        if (audio.micFrames <= 0 || audio.rms < 40.0) signals += RecoverySignal("audio.input", "SILENCE")
        if (audio.clippingDetected) signals += RecoverySignal("audio.input", "CLIPPING")
        if (audio.outputUnderrun) signals += RecoverySignal("audio.output", "UNDERRUN")
        if (!audio.loopbackOk) signals += RecoverySignal("audio.loopback", "FAILED")
        return signals
    }

    private fun suggestionFor(issue: RecoveryIssue): AutoFixSuggestion {
        return when (issue.type) {
            RecoveryErrorType.SERVER_BASE_URL_MISSING -> AutoFixSuggestion(
                error = issue.type,
                file = "app/build.gradle",
                reason = "Base relay/presence URL kosong",
                safePatch = "Set GHALBIT_RELAY_URL/GHALBIT_PRESENCE_URL via gradle.properties",
                risk = "Low",
                humanApprovalRequired = true
            )
            RecoveryErrorType.ECONNREFUSED -> AutoFixSuggestion(
                error = issue.type,
                file = "app/src/main/java/com/ghalbitnet/meshx2/network/MeshSocketClient.kt",
                reason = "Host menolak koneksi",
                safePatch = "Tambah cooldown host 30 detik + retry backoff",
                risk = "Low",
                humanApprovalRequired = false
            )
            RecoveryErrorType.FAILED_BEFORE_TTL -> AutoFixSuggestion(
                error = issue.type,
                file = "app/src/main/java/com/ghalbitnet/meshx2/chat/ChatDeliveryManager.kt",
                reason = "Pesan gagal sebelum TTL habis",
                safePatch = "Map ke PENDING sampai expiresAt lewat",
                risk = "Medium",
                humanApprovalRequired = false
            )
            RecoveryErrorType.CALL_RX_WITHOUT_PLAYBACK -> AutoFixSuggestion(
                error = issue.type,
                file = "app/src/main/java/com/ghalbitnet/meshx2/call/CallSessionActivity.kt",
                reason = "Frame masuk tapi tidak diputar",
                safePatch = "Aktifkan safe playback mode ketika rx>0 && played=0 > 3s",
                risk = "Medium",
                humanApprovalRequired = true
            )
            else -> AutoFixSuggestion(
                error = issue.type,
                file = "app/src/main/java/com/ghalbitnet/meshx2/diagnostics/recovery/SmartRecoveryEngine.kt",
                reason = "Butuh tuning rule recovery",
                safePatch = "Tambah rule classifier/policy spesifik error",
                risk = "Low",
                humanApprovalRequired = false
            )
        }
    }
}
