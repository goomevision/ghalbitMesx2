package com.ghalbitnet.meshx2.reliability

object EscalationVisibilityBoard {

    fun entry(context: android.content.Context): EscalationVisibilityEntry {
        val signals = ReliabilitySignalCollector.collect(context)
        val pressure = ReliabilityPressureAggregator.snapshot(signals)
        val confidence = ReliabilitySnapshotAggregator.confidence().name.lowercase()
        val freshness =
            ReliabilitySnapshotAggregator.snapshots()
                .map { ReliabilitySnapshotAggregator.freshnessState(it) }
                .maxByOrNull { it.ordinal }
                ?.name
                ?.lowercase()
                ?: "unknown"
        val blockers = ReliabilityBlockerReview.blockers(context)
        val state =
            when (pressure.category) {
                ReliabilityPressureScore.NOMINAL -> EscalationVisibilityState.NOMINAL
                ReliabilityPressureScore.ELEVATED -> EscalationVisibilityState.GUARDED
                ReliabilityPressureScore.STRESSED,
                ReliabilityPressureScore.OVERLOADED -> EscalationVisibilityState.ELEVATED
                ReliabilityPressureScore.CRITICAL -> EscalationVisibilityState.CRITICAL
            }
        val reason =
            when {
                blockers.isNotEmpty() -> blockers.first().label
                else -> pressure.category.name.lowercase()
            }
        return EscalationVisibilityEntry(
            state = state,
            reason = reason,
            source = "pressure/readiness/blocker synthesis",
            confidence = confidence,
            freshness = freshness,
            blockers = if (blockers.isEmpty()) "-" else blockers.joinToString { it.label }
        )
    }

    fun report(context: android.content.Context): String {
        val entry = entry(context)
        return buildString {
            appendLine("ESCALATION VISIBILITY")
            appendLine("======================")
            appendLine("Escalation level: ${entry.state.name.lowercase()}")
            appendLine("Escalation reason: ${entry.reason}")
            appendLine("Escalation source: ${entry.source}")
            appendLine("Escalation confidence: ${entry.confidence}")
            appendLine("Escalation freshness: ${entry.freshness}")
            appendLine("Escalation blockers: ${entry.blockers}")
        }.trimEnd()
    }
}
