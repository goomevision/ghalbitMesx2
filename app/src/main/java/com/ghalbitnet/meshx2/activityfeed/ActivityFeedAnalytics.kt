package com.ghalbitnet.meshx2.activityfeed

import android.content.Context
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
    val summaryText: String
)

object ActivityFeedAnalytics {

    fun summarize(context: Context, limit: Int = 1000): ActivityFeedAnalyticsSummary {
        val repository = ActivityFeedRepository(context.applicationContext)
        val items = repository.latest(limit)
        val now = System.currentTimeMillis()
        val hourAgo = now - TimeUnit.HOURS.toMillis(1)
        val dayAgo = now - TimeUnit.DAYS.toMillis(1)

        val byCategory = items.groupingBy { it.category.uppercase() }.eachCount()
        val failed = items.count {
            it.type.equals(ActivityFeedType.SYNC_FAILED.name, ignoreCase = true) ||
                it.title.contains("gagal", ignoreCase = true) ||
                it.message.contains("error", ignoreCase = true) ||
                it.severity.equals(ActivityFeedSeverity.DANGER.name, ignoreCase = true)
        }
        val warning = items.count {
            it.title.contains("warning", ignoreCase = true) ||
                it.message.contains("warning", ignoreCase = true) ||
                it.severity.equals(ActivityFeedSeverity.WARNING.name, ignoreCase = true)
        }
        val topCategory = byCategory.maxByOrNull { it.value }?.key ?: "NONE"
        val healthScore = computeHealthScore(
            total = items.size,
            failed = failed,
            warning = warning,
            sos = byCategory[ActivityFeedCategory.SOS.name] ?: 0
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
            summaryText = buildSummaryText(
                total = items.size,
                lastHour = items.count { it.timestamp >= hourAgo },
                failed = failed,
                warning = warning,
                topCategory = topCategory,
                healthScore = healthScore
            )
        )
    }

    private fun computeHealthScore(
        total: Int,
        failed: Int,
        warning: Int,
        sos: Int
    ): Int {
        if (total == 0) return 100
        val failedPenalty = failed * 8
        val warningPenalty = warning * 3
        val sosPenalty = sos * 2
        return (100 - failedPenalty - warningPenalty - sosPenalty).coerceIn(0, 100)
    }

    private fun buildSummaryText(
        total: Int,
        lastHour: Int,
        failed: Int,
        warning: Int,
        topCategory: String,
        healthScore: Int
    ): String {
        return when {
            total == 0 -> "Belum ada aktivitas yang bisa dianalisis."
            failed > 0 -> "Health $healthScore/100 • $failed error perlu diperiksa • kategori dominan $topCategory."
            warning > 0 -> "Health $healthScore/100 • $warning warning terdeteksi • $lastHour event dalam 1 jam."
            else -> "Health $healthScore/100 • sistem terlihat stabil • $lastHour event dalam 1 jam • dominan $topCategory."
        }
    }
}
