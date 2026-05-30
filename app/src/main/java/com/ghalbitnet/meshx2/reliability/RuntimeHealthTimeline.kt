package com.ghalbitnet.meshx2.reliability

object RuntimeHealthTimeline {

    private val events = mutableListOf<RuntimeHealthEvent>()

    private fun append(event: RuntimeHealthEvent) {
        if (events.lastOrNull() != event) {
            events += event
        }
        if (events.size > 12) {
            events.removeAt(0)
        }
    }

    fun update(context: android.content.Context): List<RuntimeHealthEvent> {
        val signals = ReliabilitySignalCollector.collect(context)
        val reviewSnapshot = OperationalReviewSnapshotBoard.snapshot(context)
        val governanceSummary = GovernanceSummaryBoard.summary(context, reviewSnapshot)
        val pressure = ReliabilityPressureAggregator.snapshot(signals)
        val readiness = ReliabilityReadinessGate.status(signals)
        val confidence = ReliabilitySnapshotAggregator.confidence()
        val blockers = ReliabilityBlockerReview.blockers(context)
        val now = "current"
        append(RuntimeHealthEvent("pressure changes", pressure.category.name.lowercase(), if (pressure.overallRuntimeHealthScore >= 60) RuntimeHealthTrend.RISING_RISK else RuntimeHealthTrend.STABLE, now))
        append(RuntimeHealthEvent("readiness changes", readiness.name.lowercase(), if (readiness == ReliabilityReadinessStatus.BLOCKED) RuntimeHealthTrend.DEGRADING else RuntimeHealthTrend.STABLE, now))
        append(RuntimeHealthEvent("escalation changes", governanceSummary.escalationState, RuntimeHealthTrend.STABLE, now))
        append(RuntimeHealthEvent("telemetry degradation", confidence.name.lowercase(), if (confidence == ReliabilityObservationConfidence.LOW || confidence == ReliabilityObservationConfidence.UNKNOWN) RuntimeHealthTrend.DEGRADING else RuntimeHealthTrend.STABLE, now))
        append(RuntimeHealthEvent("blocker appearance", if (blockers.isEmpty()) "none" else blockers.joinToString { it.label }, if (blockers.isEmpty()) RuntimeHealthTrend.IMPROVING else RuntimeHealthTrend.RISING_RISK, now))
        append(RuntimeHealthEvent("governance review changes", governanceSummary.state.name.lowercase(), RuntimeHealthTrend.STABLE, now))
        return events.toList()
    }

    fun report(context: android.content.Context): String =
        buildString {
            appendLine("RUNTIME HEALTH TIMELINE")
            appendLine("=======================")
            update(context).forEach { event ->
                appendLine("${event.category}: ${event.value} (${event.trend.name.lowercase()}, ${event.timestampLabel})")
            }
        }.trimEnd()
}
