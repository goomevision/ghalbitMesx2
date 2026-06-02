package com.ghalbitnet.meshx2.diagnostics.evidence

import android.content.Context

object RuntimeEvidenceReportGenerator {
    fun toHumanSummary(context: Context): String {
        val events = RuntimeEvidenceCollector.snapshot(context)
        if (events.isEmpty()) return "Belum ada runtime evidence."
        val counts = events.groupingBy { it.event }.eachCount().toList().sortedByDescending { it.second }
        val last = events.takeLast(20)
        return buildString {
            appendLine("RUNTIME EVIDENCE")
            appendLine("total=${events.size}")
            appendLine("file=${RuntimeEvidenceStore.file(context).absolutePath}")
            appendLine()
            appendLine("Top events:")
            counts.take(10).forEach { (name, count) ->
                appendLine("- $name = $count")
            }
            appendLine()
            appendLine("Last events:")
            last.forEach { event ->
                appendLine("${event.ts} | ${event.event} | ${event.source} | ${event.status ?: "-"} | ${event.details ?: "-"}")
            }
        }
    }
}
