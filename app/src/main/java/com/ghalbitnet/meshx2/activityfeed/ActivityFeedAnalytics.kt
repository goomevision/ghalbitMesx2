package com.ghalbitnet.meshx2.activityfeed

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ActivityFeedAnalyticsSummary(
    val totalEvents: Int,
    val eventsLastHour: Int,
    val eventsLastDay: Int,
    val chatEvents: Int,
    val callEvents: Int,
    val sosEvents: Int,
    val networkEvents: Int,
    val runtimeEvents: Int,
    val securityEvents: Int,
    val failedEvents: Int,
    val warningEvents: Int,
    val topCategory: String,
    val healthScore: Int,
    val runtimeHealth: RuntimeHealthAnalytics,
    val networkQuality: NetworkQualityAnalytics,
    val communication: CommunicationAnalytics,
    val dailySummary: DailyActivitySummary,
    val intelligence: ActivityFeedIntelligence,
    val summaryText: String
)

data class RuntimeHealthAnalytics(
    val score: Int,
    val runtimeEvents: Int,
    val runtimeErrors: Int,
    val heartbeatEvents: Int,
    val listenerEvents: Int,
    val socketEvents: Int,
    val statusText: String
)

data class NetworkQualityAnalytics(
    val score: Int,
    val networkEvents: Int,
    val rediscoveryEvents: Int,
    val subnetChanges: Int,
    val recoverySuccess: Int,
    val recoveryFailed: Int,
    val statusText: String
)

data class CommunicationAnalytics(
    val chatSent: Int,
    val chatReceived: Int,
    val chatFailed: Int,
    val callStarted: Int,
    val callEnded: Int,
    val callMissed: Int,
    val sosReceived: Int,
    val sosSent: Int,
    val deliveryScore: Int,
    val statusText: String
)

data class DailyActivitySummary(
    val dateLabel: String,
    val totalToday: Int,
    val chatToday: Int,
    val callToday: Int,
    val sosToday: Int,
    val networkToday: Int,
    val errorsToday: Int,
    val warningsToday: Int,
    val summaryText: String
)

data class ActivityFeedIntelligence(
    val insights: List<String>,
    val riskLevel: String,
    val actionText: String
)

object ActivityFeedAnalytics {

    fun summarize(context: Context, limit: Int = 1000): ActivityFeedAnalyticsSummary {
        val repository = ActivityFeedRepository(context.applicationContext)
        val items = repository.latest(limit)
        val now = System.currentTimeMillis()
        val hourAgo = now - TimeUnit.HOURS.toMillis(1)
        val dayAgo = now - TimeUnit.DAYS.toMillis(1)
        val todayItems = items.filter { it.timestamp >= dayAgo }

        val byCategory = items.groupingBy { it.category.uppercase() }.eachCount()
        val failed = items.count { isFailed(it) }
        val warning = items.count { isWarning(it) }
        val topCategory = byCategory.maxByOrNull { it.value }?.key ?: "NONE"

        val runtimeHealth = runtimeHealth(items)
        val networkQuality = networkQuality(items)
        val communication = communication(items)
        val dailySummary = dailySummary(todayItems, now)
        val intelligence = intelligence(items, failed, warning, communication, networkQuality)

        val healthScore = computeHealthScore(
            total = items.size,
            failed = failed,
            warning = warning,
            sos = byCategory[ActivityFeedCategory.SOS.name] ?: 0,
            runtimeScore = runtimeHealth.score,
            networkScore = networkQuality.score,
            communicationScore = communication.deliveryScore
        )

        return ActivityFeedAnalyticsSummary(
            totalEvents = items.size,
            eventsLastHour = items.count { it.timestamp >= hourAgo },
            eventsLastDay = items.count { it.timestamp >= dayAgo },
            chatEvents = byCategory[ActivityFeedCategory.CHAT.name] ?: 0,
            callEvents = byCategory[ActivityFeedCategory.CALL.name] ?: 0,
            sosEvents = byCategory[ActivityFeedCategory.SOS.name] ?: 0,
            networkEvents = byCategory[ActivityFeedCategory.NETWORK.name] ?: 0,
            runtimeEvents = byCategory[ActivityFeedCategory.RUNTIME.name] ?: 0,
            securityEvents = byCategory[ActivityFeedCategory.SECURITY.name] ?: 0,
            failedEvents = failed,
            warningEvents = warning,
            topCategory = topCategory,
            healthScore = healthScore,
            runtimeHealth = runtimeHealth,
            networkQuality = networkQuality,
            communication = communication,
            dailySummary = dailySummary,
            intelligence = intelligence,
            summaryText = buildSummaryText(
                total = items.size,
                lastHour = items.count { it.timestamp >= hourAgo },
                failed = failed,
                warning = warning,
                topCategory = topCategory,
                healthScore = healthScore,
                riskLevel = intelligence.riskLevel
            )
        )
    }

    fun renderDashboard(summary: ActivityFeedAnalyticsSummary): String {
        return buildString {
            appendLine("📊 ANALYTICS DASHBOARD")
            appendLine("Health: ${summary.healthScore}/100 • Risk: ${summary.intelligence.riskLevel}")
            appendLine(summary.summaryText)
            appendLine()
            appendLine("Event: total ${summary.totalEvents} • 1j ${summary.eventsLastHour} • 24j ${summary.eventsLastDay}")
            appendLine("CHAT ${summary.chatEvents} • CALL ${summary.callEvents} • SOS ${summary.sosEvents}")
            appendLine("NETWORK ${summary.networkEvents} • RUNTIME ${summary.runtimeEvents} • SECURITY ${summary.securityEvents}")
            appendLine("Error ${summary.failedEvents} • Warning ${summary.warningEvents} • Top ${summary.topCategory}")
            appendLine()
            appendLine("⚙️ Runtime: ${summary.runtimeHealth.score}/100")
            appendLine(summary.runtimeHealth.statusText)
            appendLine("📡 Network: ${summary.networkQuality.score}/100")
            appendLine(summary.networkQuality.statusText)
            appendLine("💬 Communication: ${summary.communication.deliveryScore}/100")
            appendLine(summary.communication.statusText)
            appendLine("🗓️ Today: ${summary.dailySummary.totalToday} event")
            appendLine(summary.dailySummary.summaryText)
            appendLine("🧠 Insight: ${summary.intelligence.actionText}")
        }
    }

    private fun runtimeHealth(items: List<ActivityFeedItem>): RuntimeHealthAnalytics {
        val runtime = items.filter { it.category.equals(ActivityFeedCategory.RUNTIME.name, true) }
        val errors = runtime.count { isFailed(it) }
        val heartbeat = runtime.count { containsAny(it, "heartbeat", "Heartbeat") }
        val listener = runtime.count { containsAny(it, "listener", "UDP listener") }
        val socket = runtime.count { containsAny(it, "socket", "TCP listener", "Socket Server") }
        val score = (100 - errors * 12).coerceIn(0, 100)
        val status = when {
            runtime.isEmpty() -> "Runtime belum memiliki cukup data."
            errors > 0 -> "Runtime perlu diperiksa: $errors error terdeteksi."
            heartbeat > 0 && listener > 0 -> "Runtime aktif: heartbeat dan listener terpantau."
            else -> "Runtime berjalan, tetapi data heartbeat/listener masih terbatas."
        }
        return RuntimeHealthAnalytics(score, runtime.size, errors, heartbeat, listener, socket, status)
    }

    private fun networkQuality(items: List<ActivityFeedItem>): NetworkQualityAnalytics {
        val network = items.filter { it.category.equals(ActivityFeedCategory.NETWORK.name, true) || it.source == "NetworkHandoffMonitor" }
        val rediscovery = network.count { containsAny(it, "rediscovery", "Rediscovery") }
        val subnet = network.count { containsAny(it, "subnet", "Perubahan jaringan", "IP") }
        val success = network.count { it.type.equals(ActivityFeedType.SYNC_SUCCESS.name, true) || containsAny(it, "aktif", "tersedia", "berhasil") }
        val failed = network.count { isFailed(it) || containsAny(it, "gagal", "terputus", "belum tersedia") }
        val score = (100 - failed * 10 - rediscovery.coerceAtMost(10) * 2 + success.coerceAtMost(10)).coerceIn(0, 100)
        val status = when {
            network.isEmpty() -> "Belum ada data kualitas jaringan."
            failed > 0 -> "Jaringan perlu perhatian: $failed gangguan, $rediscovery rediscovery."
            rediscovery > 3 -> "Jaringan aktif tetapi sering rediscovery: $rediscovery kali."
            else -> "Jaringan terlihat stabil: $success recovery/aktivasi sukses."
        }
        return NetworkQualityAnalytics(score, network.size, rediscovery, subnet, success, failed, status)
    }

    private fun communication(items: List<ActivityFeedItem>): CommunicationAnalytics {
        val chatSent = items.count { it.type.equals(ActivityFeedType.CHAT_SENT.name, true) }
        val chatReceived = items.count { it.type.equals(ActivityFeedType.CHAT_RECEIVED.name, true) }
        val chatFailed = items.count { it.category.equals(ActivityFeedCategory.CHAT.name, true) && isFailed(it) }
        val callStarted = items.count { it.type.equals(ActivityFeedType.CALL_STARTED.name, true) }
        val callEnded = items.count { it.type.equals(ActivityFeedType.CALL_ENDED.name, true) }
        val callMissed = items.count { it.type.equals(ActivityFeedType.CALL_MISSED.name, true) }
        val sosReceived = items.count { it.type.equals(ActivityFeedType.SOS_RECEIVED.name, true) }
        val sosSent = items.count { it.type.equals(ActivityFeedType.SOS_SENT.name, true) }
        val totalComm = chatSent + chatReceived + callStarted + callEnded + callMissed + sosReceived + sosSent
        val score = if (totalComm == 0) 100 else (100 - chatFailed * 8 - callMissed * 5).coerceIn(0, 100)
        val status = when {
            totalComm == 0 -> "Belum ada aktivitas komunikasi."
            chatFailed > 0 -> "Komunikasi perlu diperiksa: $chatFailed chat gagal."
            callMissed > 0 -> "Ada $callMissed panggilan tidak terjawab."
            sosReceived > 0 -> "Komunikasi darurat aktif: $sosReceived SOS diterima."
            else -> "Komunikasi terlihat normal."
        }
        return CommunicationAnalytics(chatSent, chatReceived, chatFailed, callStarted, callEnded, callMissed, sosReceived, sosSent, score, status)
    }

    private fun dailySummary(todayItems: List<ActivityFeedItem>, now: Long): DailyActivitySummary {
        val dateLabel = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(now))
        val chat = todayItems.count { it.category.equals(ActivityFeedCategory.CHAT.name, true) }
        val call = todayItems.count { it.category.equals(ActivityFeedCategory.CALL.name, true) }
        val sos = todayItems.count { it.category.equals(ActivityFeedCategory.SOS.name, true) }
        val network = todayItems.count { it.category.equals(ActivityFeedCategory.NETWORK.name, true) }
        val errors = todayItems.count { isFailed(it) }
        val warnings = todayItems.count { isWarning(it) }
        val text = "Hari ini: chat $chat, call $call, SOS $sos, network $network, error $errors, warning $warnings."
        return DailyActivitySummary(dateLabel, todayItems.size, chat, call, sos, network, errors, warnings, text)
    }

    private fun intelligence(
        items: List<ActivityFeedItem>,
        failed: Int,
        warning: Int,
        communication: CommunicationAnalytics,
        network: NetworkQualityAnalytics
    ): ActivityFeedIntelligence {
        val insights = mutableListOf<String>()
        if (failed > 0) insights += "$failed error terdeteksi di Activity Feed."
        if (warning > 2) insights += "$warning warning muncul, perlu audit ringan."
        if (network.rediscoveryEvents > 3) insights += "Rediscovery berulang: ${network.rediscoveryEvents} kali."
        if (communication.chatFailed > 0) insights += "Chat gagal: ${communication.chatFailed} event."
        if (communication.callMissed > 0) insights += "Panggilan tidak terjawab: ${communication.callMissed}."
        if (communication.sosReceived > 0) insights += "SOS diterima: ${communication.sosReceived}, pastikan sudah ditindaklanjuti."
        if (items.isEmpty()) insights += "Belum ada data untuk insight."
        val risk = when {
            failed >= 5 || network.score < 50 -> "HIGH"
            failed > 0 || warning >= 3 || communication.callMissed >= 2 -> "MEDIUM"
            else -> "LOW"
        }
        val action = when (risk) {
            "HIGH" -> "Periksa Network, Runtime, dan error terbaru."
            "MEDIUM" -> "Pantau warning dan retry komunikasi."
            else -> "Sistem terlihat aman, lanjutkan pemantauan."
        }
        return ActivityFeedIntelligence(insights.take(5), risk, action)
    }

    private fun computeHealthScore(
        total: Int,
        failed: Int,
        warning: Int,
        sos: Int,
        runtimeScore: Int,
        networkScore: Int,
        communicationScore: Int
    ): Int {
        if (total == 0) return 100
        val base = ((runtimeScore + networkScore + communicationScore) / 3)
        val penalty = failed * 4 + warning * 2 + sos.coerceAtMost(10)
        return (base - penalty).coerceIn(0, 100)
    }

    private fun buildSummaryText(
        total: Int,
        lastHour: Int,
        failed: Int,
        warning: Int,
        topCategory: String,
        healthScore: Int,
        riskLevel: String
    ): String {
        return when {
            total == 0 -> "Belum ada aktivitas yang bisa dianalisis."
            failed > 0 -> "Health $healthScore/100 • risk $riskLevel • $failed error perlu diperiksa • dominan $topCategory."
            warning > 0 -> "Health $healthScore/100 • risk $riskLevel • $warning warning • $lastHour event dalam 1 jam."
            else -> "Health $healthScore/100 • risk $riskLevel • sistem stabil • $lastHour event dalam 1 jam • dominan $topCategory."
        }
    }

    private fun isFailed(item: ActivityFeedItem): Boolean {
        return item.type.equals(ActivityFeedType.SYNC_FAILED.name, ignoreCase = true) ||
            item.title.contains("gagal", ignoreCase = true) ||
            item.message.contains("error", ignoreCase = true) ||
            item.message.contains("failed", ignoreCase = true) ||
            item.severity.equals(ActivityFeedSeverity.DANGER.name, ignoreCase = true)
    }

    private fun isWarning(item: ActivityFeedItem): Boolean {
        return item.title.contains("warning", ignoreCase = true) ||
            item.message.contains("warning", ignoreCase = true) ||
            item.severity.equals(ActivityFeedSeverity.WARNING.name, ignoreCase = true)
    }

    private fun containsAny(item: ActivityFeedItem, vararg needles: String): Boolean {
        val haystack = "${item.title} ${item.message} ${item.metadata ?: ""}"
        return needles.any { haystack.contains(it, ignoreCase = true) }
    }
}
