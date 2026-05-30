package com.ghalbitnet.meshx2.zone.sync

interface RegionalSyncBridge {
    suspend fun publishRegionalDelta(zoneId: String, delta: ZoneSyncDelta): ZoneSyncResult
}
