package com.ghalbitnet.meshx2.chat

import android.util.Log

object RouteTimeSlotScheduler {
    private const val SLOT_MS = 1_000L
    private const val ROUTE_COUNT = 4
    private const val LOG_INTERVAL_MS = 5_000L

    private const val TRANSPORT_LOCAL_MESH_DIRECT = "LOCAL_MESH_DIRECT"
    private const val TRANSPORT_LOCAL_RELAY = "LOCAL_RELAY"
    private const val TRANSPORT_SOS_EVIDENCE = "SOS_EVIDENCE"
    private const val TRANSPORT_PTT_EVIDENCE = "PTT_EVIDENCE"

    @Volatile private var lastLogAtMs: Long = 0L

    fun getCurrentSlot(nowMs: Long = System.currentTimeMillis()): Int {
        return ((nowMs / SLOT_MS) % ROUTE_COUNT).toInt()
    }

    fun getPreferredTransport(nowMs: Long = System.currentTimeMillis()): String {
        val slot = getCurrentSlot(nowMs)
        val transport =
            when (slot) {
                0 -> TRANSPORT_LOCAL_MESH_DIRECT
                1 -> TRANSPORT_LOCAL_RELAY
                2 -> TRANSPORT_SOS_EVIDENCE
                else -> TRANSPORT_PTT_EVIDENCE
            }
        maybeLog(nowMs, slot, transport)
        return transport
    }

    fun getNeighborSlots(nowMs: Long = System.currentTimeMillis()): List<Int> {
        val current = getCurrentSlot(nowMs)
        val previous = (current + ROUTE_COUNT - 1) % ROUTE_COUNT
        val next = (current + 1) % ROUTE_COUNT
        return listOf(current, previous, next)
    }

    fun getSlotStartedAt(nowMs: Long = System.currentTimeMillis()): Long {
        return (nowMs / SLOT_MS) * SLOT_MS
    }

    fun getSlotExpiresAt(nowMs: Long = System.currentTimeMillis()): Long {
        return getSlotStartedAt(nowMs) + SLOT_MS
    }

    private fun maybeLog(nowMs: Long, slot: Int, preferred: String) {
        val last = lastLogAtMs
        if (nowMs - last < LOG_INTERVAL_MS) return
        lastLogAtMs = nowMs
        val expiresInMs = getSlotExpiresAt(nowMs) - nowMs
        Log.d("GHALBIT-ROUTE-SLOT", "slot=$slot preferred=$preferred expiresInMs=$expiresInMs")
    }
}

