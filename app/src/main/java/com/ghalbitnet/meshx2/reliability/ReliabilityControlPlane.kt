package com.ghalbitnet.meshx2.reliability

object ReliabilityControlPlane {

    fun state(
        signals: List<ReliabilitySignalSnapshot>
    ): ReliabilityControlState {
        val readiness = ReliabilityReadinessGate.status(signals)
        return when (readiness) {
            ReliabilityReadinessStatus.BLOCKED -> ReliabilityControlState.BLOCKED
            ReliabilityReadinessStatus.GUARDED -> ReliabilityControlState.GUARDED
            ReliabilityReadinessStatus.READY -> ReliabilityControlState.OBSERVATIONAL_ONLY
            ReliabilityReadinessStatus.UNKNOWN -> ReliabilityControlState.REVIEW_REQUIRED
        }
    }

    fun decision(
        signals: List<ReliabilitySignalSnapshot>
    ): ReliabilityControlDecision =
        when (state(signals)) {
            ReliabilityControlState.BLOCKED -> ReliabilityControlDecision.BLOCK_ACTIVATION
            ReliabilityControlState.GUARDED,
            ReliabilityControlState.REVIEW_REQUIRED -> ReliabilityControlDecision.REQUIRE_REVIEW
            ReliabilityControlState.OBSERVATIONAL_ONLY,
            ReliabilityControlState.DISABLED,
            ReliabilityControlState.ACTIVATION_READY -> ReliabilityControlDecision.KEEP_OBSERVATIONAL
        }

    fun report(
        context: android.content.Context
    ): String {
        val signals = ReliabilitySignalCollector.collect(context)
        return buildString {
            appendLine("RELIABILITY CONTROL PLANE")
            appendLine("======================")
            appendLine("State: ${state(signals).name.lowercase()}")
            appendLine("Decision: ${decision(signals).name.lowercase()}")
            appendLine("Activation: disabled")
        }.trimEnd()
    }
}
