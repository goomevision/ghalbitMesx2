package com.ghalbitnet.meshx2.future.sync

import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.routing.MeshRoute
import com.ghalbitnet.meshx2.zone.ZoneLedgerEntry

data class OfflineMeshMemorySnapshot(
    val knownNodes: List<MeshNode>,
    val knownZones: List<String>,
    val lastRoutes: List<MeshRoute>,
    val trustedRelays: List<String>,
    val pendingPackets: List<String>,
    val zoneEntries: List<ZoneLedgerEntry>,
    val savedAt: Long = System.currentTimeMillis()
)
