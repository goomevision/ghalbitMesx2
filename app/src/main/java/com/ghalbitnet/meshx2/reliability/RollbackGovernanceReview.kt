package com.ghalbitnet.meshx2.reliability

data class RollbackGovernanceReview(
    val state: RollbackGovernanceState,
    val readinessLevel: RollbackReadinessLevel,
    val categories: List<String>,
    val note: String
) {
    companion object {
        fun from(snapshot: OperationalReviewSnapshot): RollbackGovernanceReview {
            val state =
                when (snapshot.status) {
                    OperationalReviewStatus.BLOCKED -> RollbackGovernanceState.REVIEW_PENDING
                    OperationalReviewStatus.REVIEW_REQUIRED -> RollbackGovernanceState.REVIEW_PENDING
                    OperationalReviewStatus.GUARDED -> RollbackGovernanceState.DOCUMENTED_ONLY
                    OperationalReviewStatus.STABLE -> RollbackGovernanceState.REVIEWED
                }
            val readiness =
                when (state) {
                    RollbackGovernanceState.BLOCKED -> RollbackReadinessLevel.LOW
                    RollbackGovernanceState.REVIEW_PENDING -> RollbackReadinessLevel.GUARDED
                    RollbackGovernanceState.DOCUMENTED_ONLY -> RollbackReadinessLevel.DOCUMENTED
                    RollbackGovernanceState.REVIEWED -> RollbackReadinessLevel.READY_FOR_REVIEW
                }
            return RollbackGovernanceReview(
                state = state,
                readinessLevel = readiness,
                categories = listOf(
                    "retry rollback",
                    "delayed sync rollback",
                    "adaptive runtime rollback",
                    "governance rollback",
                    "diagnostics rollback",
                    "activation rollback"
                ),
                note = "safe disable path and observability preservation remain planning-only"
            )
        }

        fun from(context: android.content.Context): RollbackGovernanceReview {
            return from(OperationalReviewSnapshotBoard.snapshot(context))
        }
    }
}
