package com.ghalbitnet.meshx2.diagnostics

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.BuildConfig
import com.ghalbitnet.meshx2.diagnostics.evidence.RuntimeEvidenceCollector
import com.ghalbitnet.meshx2.diagnostics.evidence.RuntimeEvidenceTags
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import com.ghalbitnet.meshx2.online.OnlinePresenceManager
import com.ghalbitnet.meshx2.security.NodeSigningIdentityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VirtualPeerPresenceProbeResult(
    val globalId: String,
    val registerOk: Boolean,
    val heartbeatOk: Boolean,
    val lookupOk: Boolean,
    val lastSeen: Long,
    val status: String
)

object VirtualPeerPresenceProbe {
    suspend fun run(context: Context): VirtualPeerPresenceProbeResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val relayBase = OnlineFallbackTransport.relayBaseUrl()
        val presenceBase = OnlineFallbackTransport.presenceBaseUrl()
        val identity = NodeSigningIdentityManager.getOrCreate(appContext)

        Log.i(
            "GHALBIT-VIRTUAL-PEER",
            "PRESENCE_CHECK_START globalId=${identity.globalId} nodeId=${identity.nodeId} relay=$relayBase presence=$presenceBase"
        )

        if (!BuildConfig.INTERNET_RELAY_CONFIGURED || relayBase.isBlank() || presenceBase.isBlank()) {
            Log.w("GHALBIT-VIRTUAL-PEER", "REGISTER_FAIL reason=SERVER_NOT_CONFIGURED")
            Log.w("GHALBIT-VIRTUAL-PEER", "HEARTBEAT_FAIL reason=SERVER_NOT_CONFIGURED")
            Log.w("GHALBIT-VIRTUAL-PEER", "LOOKUP_FAIL reason=SERVER_NOT_CONFIGURED")
            RuntimeEvidenceCollector.record(
                appContext,
                RuntimeEvidenceTags.SERVER_NOT_CONFIGURED,
                source = "VirtualPeerPresenceProbe",
                peerId = identity.globalId,
                status = "SERVER_NOT_CONFIGURED",
                details = "relay=$relayBase presence=$presenceBase"
            )
            return@withContext VirtualPeerPresenceProbeResult(
                globalId = identity.globalId,
                registerOk = false,
                heartbeatOk = false,
                lookupOk = false,
                lastSeen = -1L,
                status = "SERVER_NOT_CONFIGURED"
            )
        }

        val registerOk = OnlinePresenceManager.registerOnline(
            appContext,
            identity.nodeId,
            identity.globalId,
            identity.publicKeyHash
        )
        if (registerOk) {
            Log.i("GHALBIT-VIRTUAL-PEER", "REGISTER_OK globalId=${identity.globalId}")
            RuntimeEvidenceCollector.record(
                appContext,
                RuntimeEvidenceTags.SERVER_REGISTER_OK,
                source = "VirtualPeerPresenceProbe",
                peerId = identity.globalId,
                status = "REGISTER_OK"
            )
        } else {
            Log.w("GHALBIT-VIRTUAL-PEER", "REGISTER_FAIL globalId=${identity.globalId}")
        }

        val heartbeat = OnlinePresenceManager.heartbeat(
            com.ghalbitnet.meshx2.online.OnlinePresence(
                nodeId = identity.nodeId,
                globalId = identity.globalId,
                publicKeyHash = identity.publicKeyHash,
                online = true,
                route = com.ghalbitnet.meshx2.online.InternetRoute(identity.globalId, relayBase),
                lastSeen = System.currentTimeMillis()
            )
        )
        val heartbeatOk = heartbeat.online
        if (heartbeatOk) {
            Log.i(
                "GHALBIT-VIRTUAL-PEER",
                "HEARTBEAT_OK globalId=${identity.globalId} status=${heartbeat.status}"
            )
            RuntimeEvidenceCollector.record(
                appContext,
                RuntimeEvidenceTags.SERVER_HEARTBEAT_OK,
                source = "VirtualPeerPresenceProbe",
                peerId = identity.globalId,
                status = heartbeat.status
            )
        } else {
            Log.w(
                "GHALBIT-VIRTUAL-PEER",
                "HEARTBEAT_FAIL globalId=${identity.globalId} status=${heartbeat.status} error=${heartbeat.error}"
            )
        }

        val selfPresence = OnlinePresenceManager.checkPeerOnline(appContext, identity.globalId)
        val lookupOk = selfPresence != null
        val status = if (lookupOk) "VISIBLE_ON_SERVER" else "NOT_VISIBLE_ON_SERVER"
        if (lookupOk) {
            Log.i(
                "GHALBIT-VIRTUAL-PEER",
                "LOOKUP_OK globalId=${identity.globalId} lastSeen=${selfPresence?.lastSeen ?: -1L} online=${selfPresence?.online == true}"
            )
        } else {
            Log.w("GHALBIT-VIRTUAL-PEER", "LOOKUP_FAIL globalId=${identity.globalId}")
        }

        RuntimeEvidenceCollector.record(
            appContext,
            if (lookupOk) RuntimeEvidenceTags.SERVER_HEARTBEAT_OK else RuntimeEvidenceTags.SERVER_HEALTH_FAIL,
            source = "VirtualPeerPresenceProbe",
            peerId = identity.globalId,
            status = status,
            details = "register=$registerOk heartbeat=$heartbeatOk lastSeen=${selfPresence?.lastSeen ?: -1L}"
        )

        VirtualPeerPresenceProbeResult(
            globalId = identity.globalId,
            registerOk = registerOk,
            heartbeatOk = heartbeatOk,
            lookupOk = lookupOk,
            lastSeen = selfPresence?.lastSeen ?: -1L,
            status = status
        )
    }
}
