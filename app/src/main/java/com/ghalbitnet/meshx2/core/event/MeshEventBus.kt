package com.ghalbitnet.meshx2.core.event

import com.ghalbitnet.meshx2.core.log.MeshLogger

/**
 * =====================================================
 * GHALBIT MESH X2
 * INTERNAL EVENT BUS
 * =====================================================
 *
 * Pusat event internal aplikasi.
 *
 * FUTURE:
 * - event untuk AI routing
 * - reward token berdasarkan kontribusi
 * - IDS / deteksi serangan
 * - sinkronisasi antar modul
 */

object MeshEventBus {

    private val listeners =
        mutableListOf<(MeshEvent) -> Unit>()

    @Synchronized
    fun subscribe(
        listener: (MeshEvent) -> Unit
    ) {
        listeners.add(listener)
    }

    @Synchronized
    fun unsubscribe(
        listener: (MeshEvent) -> Unit
    ) {
        listeners.remove(listener)
    }

    @Synchronized
    fun publish(
        event: MeshEvent
    ) {
        MeshLogger.i(
            "EVENT",
            "${event.type} ${event.message}"
        )

        listeners.forEach { listener ->
            try {
                listener(event)
            } catch (e: Exception) {
                MeshLogger.e(
                    "EVENT",
                    "Listener failed",
                    e
                )
            }
        }
    }
}

data class MeshEvent(
    val type: String,
    val message: String,
    val source: String = "-",
    val timestamp: Long = System.currentTimeMillis()
)
