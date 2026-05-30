package com.ghalbitnet.meshx2.reliability

object ReliabilitySnapshotAggregator {

    fun snapshots(): List<ReliabilityRuntimeSnapshot> {
        val now = System.currentTimeMillis()
        return listOf(
            ReliabilityRuntimeSnapshot(ReliabilityRuntimeState.QUEUE_STATE, now, now + 15_000L, "observational"),
            ReliabilityRuntimeSnapshot(ReliabilityRuntimeState.RETRY_STATE, now, now + 15_000L, "observational"),
            ReliabilityRuntimeSnapshot(ReliabilityRuntimeState.DELAYED_SYNC_STATE, now, now + 15_000L, "placeholder"),
            ReliabilityRuntimeSnapshot(ReliabilityRuntimeState.RELAY_CUSTODY_STATE, now, now + 15_000L, "estimated"),
            ReliabilityRuntimeSnapshot(ReliabilityRuntimeState.RUNTIME_STRESS_STATE, now, now + 15_000L, "derived"),
            ReliabilityRuntimeSnapshot(ReliabilityRuntimeState.READINESS_STATE, now, now + 15_000L, "derived")
        )
    }

    fun freshnessState(snapshot: ReliabilityRuntimeSnapshot): SnapshotFreshnessState {
        val now = System.currentTimeMillis()
        return when {
            now > snapshot.expiresAt -> SnapshotFreshnessState.EXPIRED
            now - snapshot.capturedAt > 10_000L -> SnapshotFreshnessState.STALE
            now - snapshot.capturedAt > 5_000L -> SnapshotFreshnessState.AGING
            else -> SnapshotFreshnessState.FRESH
        }
    }

    fun confidence(): ReliabilityObservationConfidence {
        val labels = snapshots().map { it.confidenceLabel }
        return when {
            labels.count { it == "observational" } >= 3 -> ReliabilityObservationConfidence.HIGH
            labels.any { it == "observational" } || labels.any { it == "derived" } -> ReliabilityObservationConfidence.MEDIUM
            labels.any { it == "estimated" || it == "placeholder" } -> ReliabilityObservationConfidence.LOW
            else -> ReliabilityObservationConfidence.UNKNOWN
        }
    }

    fun report(): String =
        buildString {
            appendLine("RELIABILITY SNAPSHOTS")
            appendLine("======================")
            appendLine("Observation confidence: ${confidence().name.lowercase()}")
            snapshots().forEach { snapshot ->
                appendLine("${snapshot.state.name.lowercase()}: ${freshnessState(snapshot).name.lowercase()} [${snapshot.confidenceLabel}]")
            }
        }.trimEnd()
}
