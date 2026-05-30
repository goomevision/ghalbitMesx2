package com.ghalbitnet.meshx2.reliability

object ActivationReviewWorkflow {

    fun decision(context: android.content.Context): ActivationReviewDecision {
        val snapshot = OperationalReviewSnapshotBoard.snapshot(context)
        return when (snapshot.status) {
            OperationalReviewStatus.BLOCKED -> ActivationReviewDecision.BLOCKED
            OperationalReviewStatus.REVIEW_REQUIRED -> ActivationReviewDecision.REVIEW_REQUIRED
            OperationalReviewStatus.GUARDED -> ActivationReviewDecision.GUARDED
            OperationalReviewStatus.STABLE -> ActivationReviewDecision.ELIGIBLE_BUT_DISABLED
        }
    }

    fun report(context: android.content.Context): String {
        val decision = decision(context)
        val currentStage =
            when (decision) {
                ActivationReviewDecision.BLOCKED -> ActivationReviewStage.BLOCKER_REVIEW
                ActivationReviewDecision.REVIEW_REQUIRED -> ActivationReviewStage.TELEMETRY_REVIEW
                ActivationReviewDecision.GUARDED -> ActivationReviewStage.READINESS_REVIEW
                ActivationReviewDecision.ELIGIBLE_BUT_DISABLED -> ActivationReviewStage.ACTIVATION_ELIGIBILITY_REVIEW
            }
        val pendingStages =
            ActivationReviewStage.entries.filter { it.ordinal >= currentStage.ordinal }
        return buildString {
            appendLine("ACTIVATION REVIEW WORKFLOW")
            appendLine("======================")
            appendLine("Decision: ${decision.name.lowercase()}")
            appendLine("Current stage: ${currentStage.name.lowercase()}")
            appendLine(
                "Pending stages: ${
                    pendingStages.joinToString { it.name.lowercase() }
                }"
            )
            appendLine("Runtime remains disabled: yes")
            appendLine("Retry activation: no")
            appendLine("Delayed sync activation: no")
            appendLine("Adaptive orchestration activation: no")
        }.trimEnd()
    }
}
