package com.ghalbitnet.meshx2.core.runtime

import android.content.Context
import com.ghalbitnet.meshx2.core.device.DeviceCapability
import com.ghalbitnet.meshx2.core.log.MeshLogger
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object LightweightMeshSupervisor {

    private var job: Job? = null

    fun start(context: Context) {
        if (job?.isActive == true) return

        MeshRuntimeState.updateLowPowerMode(
            DeviceCapability.isLowEndDevice(context)
        )

        MeshRuntimeState.markStarted()

        job = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    val nodes =
                        DiscoveryManager.discoverNodes()

                    MeshRuntimeState.updateNodeCount(
                        nodes.size
                    )

                    MeshRuntimeState.heartbeat()

                    delay(
                        DeviceCapability.recommendedHeartbeatInterval(context)
                    )

                } catch (e: Exception) {
                    MeshRuntimeState.setError(
                        e.message ?: "Supervisor error"
                    )

                    MeshLogger.e(
                        "SUPERVISOR",
                        "Supervisor error",
                        e
                    )

                    delay(5000)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        MeshRuntimeState.markStopped()
    }

    fun enableLowPowerMode() {
        MeshRuntimeState.updateLowPowerMode(true)
    }

    fun disableLowPowerMode() {
        MeshRuntimeState.updateLowPowerMode(false)
    }
}
