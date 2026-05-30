package com.ghalbitnet.meshx2.online

interface PresenceApi {
    suspend fun registerOnline(presence: OnlinePresence): Boolean

    suspend fun updatePresence(presence: OnlinePresence): Boolean

    suspend fun checkPeerOnline(targetGlobalId: String): OnlinePresence?

    suspend fun heartbeat(presence: OnlinePresence): RemotePresenceResult
}
