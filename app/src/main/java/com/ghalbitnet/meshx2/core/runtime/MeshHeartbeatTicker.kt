package com.ghalbitnet.meshx2.core.runtime

import com.ghalbitnet.meshx2.core.log.MeshLogger
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * =====================================================
 * GHALBIT MESH X2
 * HEARTBEAT TICKER
 * =====================================================
 *
 * Menjaga status runtime tetap hidup.
 *
 * FUTURE:
 * - kirim heartbeat terenkripsi antar node
 * - deteksi node mati
 * - reward berdasarkan uptime
 * - Proof of Connectivity
 */

object MeshHeartbeatTicker {

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return

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

                    MeshLogger.i(
                        "HEARTBEAT",
                        "alive nodes=${nodes.size}"
                    )

                    delay(10000)

                } catch (e: Exception) {
                    MeshRuntimeState.setError(
                        e.message ?: "Heartbeat error"
                    )

                    MeshLogger.e(
                        "HEARTBEAT",
                        "Heartbeat failed",
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
}
