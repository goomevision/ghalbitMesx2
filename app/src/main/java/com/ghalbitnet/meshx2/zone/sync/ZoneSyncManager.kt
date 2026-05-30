package com.ghalbitnet.meshx2.zone.sync

interface ZoneSyncManager {
    suspend fun syncLocalDevice(): ZoneSyncResult
    suspend fun syncLocalZone(zoneId: String): ZoneSyncResult
}
