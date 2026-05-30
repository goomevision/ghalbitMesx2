package com.ghalbitnet.meshx2.core.runtime

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.chat.ConversationKeepAliveManager
import com.ghalbitnet.meshx2.chat.LiveContactSync
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.routing.IntelligentRouteMemory

object MultiNodeValidationManager {
    fun verifyPeerVisibility(context: Context): String {
        val contacts = LiveContactSync.build(context.applicationContext)
        val visible = contacts.count { it.isLive }
        val result = "peerVisibility live=$visible total=${contacts.size}"
        Log.d("GHALBIT-KEEPALIVE-HEALTH", result)
        return result
    }

    fun verifyHeartbeatConsistency(): String {
        val online = NodeStatusManager.onlineCount()
        val alive = MeshRuntimeManager.aliveNodes.value
        return "heartbeatConsistency alive=$alive online=$online consistent=${alive == online}"
    }

    fun verifyContactRosterConsistency(context: Context): String {
        val roster = LiveContactSync.build(context.applicationContext)
        val runtimeRoster = MeshRuntimeManager.contactRoster.value
        return "contactRosterConsistency runtime=${runtimeRoster.size} rebuilt=${roster.size} consistent=${runtimeRoster.size == roster.size}"
    }

    fun verifyRouteConsistency(context: Context): String {
        val routes = IntelligentRouteMemory.getAllHints(context.applicationContext)
        val live = routes.count { System.currentTimeMillis() - it.lastSeen < 120000L }
        return "routeConsistency total=${routes.size} fresh=$live"
    }

    fun verifyKeepAliveConsistency(): String {
        val active = ConversationKeepAliveManager.stateFlow.value.size
        val connected = ConversationKeepAliveManager.stateFlow.value.values.count { it.routeHealth != com.ghalbitnet.meshx2.chat.RouteHealthStatus.OFFLINE_PENDING }
        return "keepAliveConsistency active=$active healthy=$connected"
    }
}
