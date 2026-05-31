package com.ghalbitnet.meshx2.verified.card

object CardSyncManager {
    fun needsSync(snapshot: CardSyncSnapshot): Boolean {
        return snapshot.remoteVersion > snapshot.localVersion
    }

    fun markSynced(snapshot: CardSyncSnapshot): CardSyncSnapshot {
        return snapshot.copy(
            localVersion = snapshot.remoteVersion,
            syncState = "SYNCED"
        )
    }
}
