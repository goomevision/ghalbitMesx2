package com.ghalbitnet.meshx2.reliability

object OperatorWorkflowBoard {

    fun review(context: android.content.Context): OperatorWorkflowReview {
        val signals = ReliabilitySignalCollector.collect(context)
        val readiness = ReliabilityReadinessGate.status(signals)
        val currentStage =
            when (readiness) {
                ReliabilityReadinessStatus.BLOCKED -> OperatorWorkflowStage.DIAGNOSTICS_REVIEW
                ReliabilityReadinessStatus.UNKNOWN -> OperatorWorkflowStage.TELEMETRY_REVIEW
                ReliabilityReadinessStatus.GUARDED -> OperatorWorkflowStage.RISK_REVIEW
                ReliabilityReadinessStatus.READY -> OperatorWorkflowStage.ACTIVATION_REVIEW
            }
        val state =
            when (readiness) {
                ReliabilityReadinessStatus.BLOCKED -> OperatorWorkflowState.WAITING_ON_BLOCKERS
                ReliabilityReadinessStatus.UNKNOWN -> OperatorWorkflowState.REVIEW_ACTIVE
                ReliabilityReadinessStatus.GUARDED -> OperatorWorkflowState.REVIEW_ACTIVE
                ReliabilityReadinessStatus.READY -> OperatorWorkflowState.PASSIVE_ONLY
            }
        val pending =
            OperatorWorkflowStage.entries.filter { it.ordinal >= currentStage.ordinal }
        val note =
            when (state) {
                OperatorWorkflowState.WAITING_ON_BLOCKERS -> "workflow paused on passive blocker review"
                OperatorWorkflowState.REVIEW_ACTIVE -> "continue passive governance review stages"
                OperatorWorkflowState.PASSIVE_ONLY -> "runtime remains disabled while review stays observational"
                OperatorWorkflowState.REVIEW_COMPLETE -> "review complete with no automation enabled"
            }
        return OperatorWorkflowReview(currentStage, state, pending, note)
    }

    fun report(context: android.content.Context): String {
        val review = review(context)
        return buildString {
            appendLine("OPERATOR WORKFLOW")
            appendLine("======================")
            appendLine("Current stage: ${review.currentStage.name.lowercase()}")
            appendLine("Workflow state: ${review.workflowState.name.lowercase()}")
            appendLine(
                "Pending stages: ${
                    review.pendingStages.joinToString { it.name.lowercase() }
                }"
            )
            appendLine("Note: ${review.note}")
        }.trimEnd()
    }
}
