package com.ghalbitnet.meshx2.reliability

object ReliabilityPressureAggregator {

    fun snapshot(
        signals: List<ReliabilitySignalSnapshot> = emptyList()
    ): ReliabilityHealthSnapshot {
        val queuePressure =
            signalInt(signals, ReliabilitySignalType.ACK_PENDING_COUNT, 10)
                .coerceIn(0, 100)
        val retryPressure =
            (signalInt(signals, ReliabilitySignalType.RETRY_METADATA_COUNT, 10) * 10)
                .coerceIn(0, 100)
        val relayPressure =
            ((signalInt(signals, ReliabilitySignalType.ROUTE_REQUEST_COUNT, 0) * 10) +
                (signalInt(signals, ReliabilitySignalType.PENDING_TRANSFER_COUNT, 0) * 5))
                .coerceIn(0, 100)
        val syncPressure =
            when (
                signals.firstOrNull {
                    it.type == ReliabilitySignalType.CONNECTIVITY_SCOPE
                }?.value
            ) {
                "offline" -> 80
                "local_only" -> 40
                "internet_only" -> 35
                "internet_and_local" -> 15
                else -> 10
            }
        val overall =
            (queuePressure + retryPressure + relayPressure + syncPressure) / 4
        return ReliabilityHealthSnapshot(
            queuePressureScore = queuePressure,
            retryPressureScore = retryPressure,
            relayPressureScore = relayPressure,
            syncPressureScore = syncPressure,
            overallRuntimeHealthScore = overall,
            category = categoryFor(overall)
        )
    }

    fun report(
        signals: List<ReliabilitySignalSnapshot> = emptyList()
    ): String {
        val snapshot = snapshot(signals)
        return buildString {
            appendLine("RELIABILITY PRESSURE")
            appendLine("======================")
            appendLine("Queue pressure: ${snapshot.queuePressureScore}")
            appendLine("Retry pressure: ${snapshot.retryPressureScore}")
            appendLine("Relay pressure: ${snapshot.relayPressureScore}")
            appendLine("Sync pressure: ${snapshot.syncPressureScore}")
            appendLine("Overall health: ${snapshot.overallRuntimeHealthScore}")
            appendLine("Category: ${snapshot.category.name.lowercase()}")
            appendLine("Signal basis: observational/derived")
        }.trimEnd()
    }

    private fun signalInt(
        signals: List<ReliabilitySignalSnapshot>,
        type: ReliabilitySignalType,
        fallback: Int
    ): Int {
        val raw =
            signals.firstOrNull { it.type == type }?.value ?: return fallback
        return raw.substringBefore(' ').toIntOrNull() ?: fallback
    }

    private fun categoryFor(score: Int): ReliabilityPressureScore =
        when {
            score >= 80 -> ReliabilityPressureScore.CRITICAL
            score >= 60 -> ReliabilityPressureScore.OVERLOADED
            score >= 40 -> ReliabilityPressureScore.STRESSED
            score >= 20 -> ReliabilityPressureScore.ELEVATED
            else -> ReliabilityPressureScore.NOMINAL
        }
}
