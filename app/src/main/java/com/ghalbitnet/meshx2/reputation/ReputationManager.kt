package com.ghalbitnet.meshx2.reputation

import com.ghalbitnet.meshx2.routing.MeshRegistry
import com.ghalbitnet.meshx2.token.TokenManager

object ReputationManager {
    fun updateReputation(ip: String, relaySuccess: Boolean, latencyMs: Long) {
        val node = MeshRegistry.getNode(ip) ?: return
        var newTrust = node.trusted
        if (relaySuccess) newTrust = minOf(newTrust + 2, 100) else newTrust = maxOf(newTrust - 5, 0)
        if (latencyMs > 100) newTrust = maxOf(newTrust - ((latencyMs - 100) * 0.1).toInt(), 0)
        val newBalance = if (relaySuccess) node.balance + 0.01 else node.balance
        MeshRegistry.updateNode(node.copy(trusted = newTrust, balance = newBalance))
        if (relaySuccess) TokenManager.recordReward(ip, node.name, 0.01)
    }
}