package com.ghalbitnet.meshx2.diagnostics

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.BuildConfig
import com.ghalbitnet.meshx2.call.CallSessionActivity
import com.ghalbitnet.meshx2.online.InternetRoute
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import com.ghalbitnet.meshx2.online.OnlinePresence
import com.ghalbitnet.meshx2.online.OnlinePresenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class VirtualPeerOutboundCallProbeResult(
    val callId: String,
    val status: String,
    val routeLabel: String,
    val targetGlobalId: String
)

object VirtualPeerOutboundCallProbe {
    private const val PREFS = "virtual_peer_call_signal_probe"
    private const val KEY_LAST_CALL_ID = "last_call_id"
    private const val VIRTUAL_TARGET_NAME = "Virtual HP B"
    private const val VIRTUAL_TARGET_NODE_ID = "virtual-peer-b"
    private const val VIRTUAL_TARGET_GLOBAL_ID = "GX-VIRTUAL-HP-B"

    suspend fun launchServerFirstOutgoingCall(context: Context): VirtualPeerOutboundCallProbeResult =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val relayBase = OnlineFallbackTransport.relayBaseUrl().trim()
            if (!BuildConfig.INTERNET_RELAY_CONFIGURED || relayBase.isBlank()) {
                Log.w("GHALBIT-VIRTUAL-PEER", "OUTBOUND_CALL_FAIL reason=SERVER_NOT_CONFIGURED")
                return@withContext VirtualPeerOutboundCallProbeResult(
                    callId = "",
                    status = "SERVER_NOT_CONFIGURED",
                    routeLabel = "no_relay",
                    targetGlobalId = VIRTUAL_TARGET_GLOBAL_ID
                )
            }

            OnlinePresenceManager.applyRealtimePresence(
                appContext,
                OnlinePresence(
                    nodeId = VIRTUAL_TARGET_NODE_ID,
                    globalId = VIRTUAL_TARGET_GLOBAL_ID,
                    online = true,
                    route = InternetRoute(VIRTUAL_TARGET_GLOBAL_ID, relayBase)
                )
            )

            val callId = "virt-out-${UUID.randomUUID()}"
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_CALL_ID, callId)
                .apply()
            val intent =
                CallSessionActivity.createIntent(
                    context = appContext,
                    peerName = VIRTUAL_TARGET_NAME,
                    peerIp = "",
                    callId = callId,
                    incoming = false,
                    peerGlobalId = VIRTUAL_TARGET_GLOBAL_ID,
                    peerDisplayName = VIRTUAL_TARGET_NAME
                ).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            appContext.startActivity(intent)
            Log.i(
                "GHALBIT-VIRTUAL-PEER",
                "OUTBOUND_CALL_START callId=$callId target=$VIRTUAL_TARGET_GLOBAL_ID route=internet_relay"
            )
            VirtualPeerOutboundCallProbeResult(
                callId = callId,
                status = "OUTBOUND_CALL_STARTED",
                routeLabel = "internet_relay",
                targetGlobalId = VIRTUAL_TARGET_GLOBAL_ID
            )
        }

    suspend fun launchServerFirstOutgoingCallWithAutoAccept(context: Context): VirtualPeerOutboundCallProbeResult =
        withContext(Dispatchers.IO) {
            val start = launchServerFirstOutgoingCall(context)
            if (start.status != "OUTBOUND_CALL_STARTED") {
                return@withContext start
            }
            delay(1200L)
            val ringing = VirtualPeerCallSignalProbe.ringing(context.applicationContext)
            delay(1200L)
            val accept = VirtualPeerCallSignalProbe.accept(context.applicationContext)
            val status =
                when {
                    !ringing.status.endsWith("_OK") -> "OUTBOUND_CALL_RINGING_FAILED"
                    !accept.status.endsWith("_OK") -> "OUTBOUND_CALL_ACCEPT_FAILED"
                    else -> "OUTBOUND_CALL_AUTO_ACCEPT_OK"
                }
            Log.i(
                "GHALBIT-VIRTUAL-PEER",
                "OUTBOUND_CALL_AUTO_ACCEPT status=$status callId=${start.callId} ringing=${ringing.status} accept=${accept.status}"
            )
            start.copy(status = status)
        }
}
