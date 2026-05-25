package com.ghalbitnet.meshx2.core.runtime

import java.util.concurrent.CopyOnWriteArraySet

object MeshRuntimeState {

    data class Snapshot(
        val isMeshRunning: Boolean,
        val isLowPowerMode: Boolean,
        val lastHeartbeat: Long,
        val lastError: String,
        val nodeCount: Int,
        val gatewaySummary: String
    )

    @Volatile
    var isMeshRunning: Boolean = false
        private set

    @Volatile
    var isLowPowerMode: Boolean = false
        private set

    @Volatile
    var lastHeartbeat: Long = 0L
        private set

    @Volatile
    var lastError: String = ""
        private set

    @Volatile
    var nodeCount: Int = 0
        private set

    @Volatile
    var gatewaySummary: String = ""
        private set

    private val listeners =
        CopyOnWriteArraySet<(Snapshot) -> Unit>()

    fun startMesh() {
        markStarted()
    }

    fun stopMesh() {
        markStopped()
    }

    fun markStarted() {
        isMeshRunning = true
        heartbeat()
        clearError()
        notifyChanged()
    }

    fun markStopped() {
        isMeshRunning = false
        notifyChanged()
    }

    fun heartbeat() {
        lastHeartbeat = System.currentTimeMillis()
        notifyChanged()
    }

    fun updateLowPowerMode(enabled: Boolean) {
        isLowPowerMode = enabled
        notifyChanged()
    }

    fun updateNodeCount(count: Int) {
        nodeCount = count
        notifyChanged()
    }

    fun updateGatewaySummary(summary: String) {
        gatewaySummary = summary
        notifyChanged()
    }

    fun setError(message: String) {
        lastError = message
        notifyChanged()
    }

    fun clearError() {
        lastError = ""
        notifyChanged()
    }

    fun snapshot(): Snapshot {
        return Snapshot(
            isMeshRunning = isMeshRunning,
            isLowPowerMode = isLowPowerMode,
            lastHeartbeat = lastHeartbeat,
            lastError = lastError,
            nodeCount = nodeCount,
            gatewaySummary = gatewaySummary
        )
    }

    fun addListener(listener: (Snapshot) -> Unit) {
        listeners.add(listener)
        listener(snapshot())
    }

    fun removeListener(listener: (Snapshot) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        val snapshot = snapshot()
        listeners.forEach { listener ->
            listener(snapshot)
        }
    }
}
