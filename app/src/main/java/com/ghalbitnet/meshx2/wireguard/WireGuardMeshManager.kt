package com.ghalbitnet.meshx2.wireguard

import android.content.Context

class WireGuardMeshManager(private val context: Context) {
    suspend fun startMesh(ipAddress: String = "10.0.0.1/24", listenPort: Int = 51820) {}
    fun addPeer(ip: String, publicKey: String, endpoint: String? = null) {}
    fun removePeer(ip: String) {}
    fun getMyIp(): String = "10.0.0.3"
    fun getPeerStats(ip: String): Pair<Long, Long>? = null
    fun stop() {}
}