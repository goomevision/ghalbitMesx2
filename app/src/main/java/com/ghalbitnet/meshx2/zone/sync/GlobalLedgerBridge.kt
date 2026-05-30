package com.ghalbitnet.meshx2.zone.sync

interface GlobalLedgerBridge {
    suspend fun publishGlobalDelta(zoneId: String, delta: ZoneSyncDelta): ZoneSyncResult
}
