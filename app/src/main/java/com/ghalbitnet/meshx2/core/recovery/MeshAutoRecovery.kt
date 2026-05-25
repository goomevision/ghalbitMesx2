package com.ghalbitnet.meshx2.core.recovery

import com.ghalbitnet.meshx2.core.config.MeshConfigCenter
import com.ghalbitnet.meshx2.core.log.MeshLogger
import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object MeshAutoRecovery {

    private var job: Job? = null

    fun start(
        onRecover: () -> Unit
    ) {
        if (!MeshConfigCenter.ENABLE_AUTO_RECOVERY) return
        if (job?.isActive == true) return

        job = CoroutineScope(Dispatchers.IO).launch {

            while (true) {

                delay(MeshConfigCenter.RECOVERY_CHECK_MS)

                val now = System.currentTimeMillis()
                val last = MeshRuntimeState.lastHeartbeat

                val timeout =
                    if (MeshRuntimeState.isLowPowerMode) {
                        MeshConfigCenter.RECOVERY_TIMEOUT_LOW_POWER_MS
                    } else {
                        MeshConfigCenter.RECOVERY_TIMEOUT_NORMAL_MS
                    }

                if (
                    MeshRuntimeState.isMeshRunning &&
                    last > 0L &&
                    now - last > timeout
                ) {
                    try {
                        MeshLogger.w("RECOVERY", "Heartbeat timeout. Recovery started.")

                        onRecover()

                        MeshRuntimeState.heartbeat()
                        MeshRuntimeState.clearError()

                    } catch (e: Exception) {

                        MeshRuntimeState.setError(
                            e.message ?: "Recovery failed"
                        )

                        MeshLogger.e("RECOVERY", "Recovery failed", e)
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
