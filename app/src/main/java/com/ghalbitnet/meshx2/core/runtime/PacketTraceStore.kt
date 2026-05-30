package com.ghalbitnet.meshx2.core.runtime

import android.text.format.DateFormat
import android.util.Log
import java.util.ArrayDeque

object PacketTraceStore {
    private const val MAX_ENTRIES = 100
    private val entries = ArrayDeque<PacketTraceEntry>()

    @Synchronized
    fun record(entry: PacketTraceEntry) {
        entries.addLast(entry)
        while (entries.size > MAX_ENTRIES) {
            entries.removeFirst()
        }
        Log.d(
            "GHALBIT-PACKET-TRACE",
            "type=${entry.packetType} source=${entry.sourceNodeId} target=${entry.targetNodeId} route=${entry.routeType} transport=${entry.transport} state=${entry.deliveryState}"
        )
    }

    @Synchronized
    fun recent(limit: Int = 8): List<PacketTraceEntry> = entries.toList().takeLast(limit).reversed()

    fun recentLines(limit: Int = 8): List<String> {
        return recent(limit).map {
            "${DateFormat.format("HH:mm:ss", it.timestamp)} ${it.packetType} ${it.routeType} ${it.deliveryState}"
        }
    }
}
