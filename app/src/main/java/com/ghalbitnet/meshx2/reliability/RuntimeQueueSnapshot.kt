package com.ghalbitnet.meshx2.reliability

data class RuntimeQueueSnapshot(
    val category: String,
    val state: QueueObservationState = QueueObservationState.UNKNOWN,
    val metrics: RuntimeQueueMetrics = RuntimeQueueMetrics(),
    val capturedAt: Long = System.currentTimeMillis()
)
