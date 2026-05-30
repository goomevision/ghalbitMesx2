package com.ghalbitnet.meshx2.reliability

object OperationalTelemetryConfidenceBoard {

    fun report(context: android.content.Context): String {
        val signals = ReliabilitySignalCollector.collect(context)
        val confidence = ReliabilitySnapshotAggregator.confidence()
        val freshnessStates =
            ReliabilitySnapshotAggregator.snapshots()
                .map { ReliabilitySnapshotAggregator.freshnessState(it) }
        val staleCount =
            freshnessStates.count {
                it == SnapshotFreshnessState.STALE || it == SnapshotFreshnessState.EXPIRED
            }
        val signalLabels = signals.groupingBy { it.label }.eachCount()
        val placeholderCount =
            ReliabilitySnapshotAggregator.snapshots()
                .count { it.confidenceLabel == "placeholder" }
        val label =
            when (confidence) {
                ReliabilityObservationConfidence.HIGH -> "high confidence"
                ReliabilityObservationConfidence.MEDIUM -> "medium confidence"
                ReliabilityObservationConfidence.LOW -> "low confidence"
                ReliabilityObservationConfidence.UNKNOWN -> "insufficient data"
            }
        return buildString {
            appendLine("TELEMETRY CONFIDENCE")
            appendLine("======================")
            appendLine("Label: $label")
            appendLine("Signal freshness: ${freshnessStates.maxByOrNull { it.ordinal }?.name?.lowercase() ?: "unknown"}")
            appendLine("Signal source type: ${signalLabels.keys.sorted().joinToString()}")
            appendLine("Observational confidence: ${confidence.name.lowercase()}")
            appendLine("Placeholder ratio: $placeholderCount/${ReliabilitySnapshotAggregator.snapshots().size}")
            appendLine("Stale signal count: $staleCount")
            appendLine("Missing signal count: ${signals.count { it.value == "unknown" }}")
            appendLine("Real-signal coverage: ${signals.count { it.label == "observational" }}/${signals.size}")
        }.trimEnd()
    }
}
