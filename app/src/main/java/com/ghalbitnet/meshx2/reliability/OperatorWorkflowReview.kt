package com.ghalbitnet.meshx2.reliability

data class OperatorWorkflowReview(
    val currentStage: OperatorWorkflowStage,
    val workflowState: OperatorWorkflowState,
    val pendingStages: List<OperatorWorkflowStage>,
    val note: String
)
