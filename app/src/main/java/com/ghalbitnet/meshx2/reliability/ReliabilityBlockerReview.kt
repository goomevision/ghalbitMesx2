package com.ghalbitnet.meshx2.reliability

object ReliabilityBlockerReview {

    fun blockers(context: android.content.Context): List<ReliabilityBlocker> {
        val signals = ReliabilitySignalCollector.collect(context)
        val pressure = ReliabilityPressureAggregator.snapshot(signals)
        val confidence = ReliabilitySnapshotAggregator.confidence()
        val freshness = ReliabilitySnapshotAggregator.report().lowercase()
        val stress = RuntimeStressDiagnostics.highestSeverity(signals)
        val blockers = mutableListOf<ReliabilityBlocker>()
        if (freshness.contains("stale") || freshness.contains("expired")) {
            blockers += ReliabilityBlocker("stale telemetry", ReliabilityBlockerSeverity.WARNING, "telemetry freshness degraded")
        }
        if (pressure.retryPressureScore >= 60) {
            blockers += ReliabilityBlocker("retry saturation", ReliabilityBlockerSeverity.HIGH, "retry pressure score=${pressure.retryPressureScore}")
        }
        if (pressure.relayPressureScore >= 60) {
            blockers += ReliabilityBlocker("relay congestion", ReliabilityBlockerSeverity.HIGH, "relay pressure score=${pressure.relayPressureScore}")
            blockers += ReliabilityBlocker("custody backlog", ReliabilityBlockerSeverity.WARNING, "custody backlog follows relay pressure")
        }
        if (confidence == ReliabilityObservationConfidence.LOW || confidence == ReliabilityObservationConfidence.UNKNOWN) {
            blockers += ReliabilityBlocker("low observational confidence", ReliabilityBlockerSeverity.WARNING, "confidence=${confidence.name.lowercase()}")
        }
        if (RuntimeStressDiagnostics.report(signals).contains("hotspot instability")) {
            blockers += ReliabilityBlocker("hotspot instability", ReliabilityBlockerSeverity.WARNING, "hotspot state is unstable/derived")
        }
        if (RuntimeStressDiagnostics.report(signals).contains("vpn lifecycle instability")) {
            blockers += ReliabilityBlocker("VPN instability", ReliabilityBlockerSeverity.WARNING, "vpn lifecycle signal is unstable/derived")
        }
        if (stress == RuntimeStressSeverity.HIGH || stress == RuntimeStressSeverity.CRITICAL) {
            blockers += ReliabilityBlocker("battery instability", ReliabilityBlockerSeverity.WARNING, "stress severity=${stress.name.lowercase()}")
        }
        return blockers
    }

    fun report(context: android.content.Context): String =
        buildString {
            appendLine("BLOCKER REVIEW")
            appendLine("======================")
            val blockers = blockers(context)
            if (blockers.isEmpty()) {
                appendLine("No structured blockers detected")
            } else {
                blockers.forEach {
                    appendLine("${it.label}: ${it.severity.name.lowercase()} (${it.detail})")
                }
            }
        }.trimEnd()
}
