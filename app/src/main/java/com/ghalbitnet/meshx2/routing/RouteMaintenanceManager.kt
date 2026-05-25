package com.ghalbitnet.meshx2.routing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object RouteMaintenanceManager {

    private const val CLEANUP_INTERVAL_MS = 300_000L

    interface RouteMaintenanceListener {
        fun onMaintenanceStatus(message: String)
        fun onMaintenanceError(message: String, throwable: Throwable? = null)
    }

    fun start(
        scope: CoroutineScope,
        listener: RouteMaintenanceListener? = null
    ): Job {
        listener?.onMaintenanceStatus("Route maintenance aktif.")
        return scope.launch(Dispatchers.Default) {
            while (true) {
                delay(CLEANUP_INTERVAL_MS)
                runCatching {
                    RouteDiscovery.clearExpiredRoutes()
                }.onFailure {
                    listener?.onMaintenanceError("Route cleanup gagal.", it)
                }
            }
        }
    }
}

