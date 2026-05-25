package com.ghalbitnet.meshx2.core.health

import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeState

object MeshHealthReporter {

    fun report(): String {
        return buildString {
            appendLine("GHALBIT MESH HEALTH")
            appendLine("===================")
            appendLine("Running        : ${MeshRuntimeState.isMeshRunning}")
            appendLine("Low Power Mode : ${MeshRuntimeState.isLowPowerMode}")
            appendLine("Node Count     : ${MeshRuntimeState.nodeCount}")
            appendLine("Last Heartbeat : ${MeshRuntimeState.lastHeartbeat}")
            appendLine("Last Error     : ${MeshRuntimeState.lastError.ifEmpty { "-" }}")
        }
    }
}
