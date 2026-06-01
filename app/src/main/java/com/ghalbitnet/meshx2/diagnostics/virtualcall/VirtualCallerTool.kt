package com.ghalbitnet.meshx2.diagnostics.virtualcall

import android.content.Context
import com.ghalbitnet.meshx2.call.CallSessionActivity
import java.util.UUID

object VirtualCallerTool {
    fun triggerIncomingCall(context: Context, scenario: VirtualCallScenario): String {
        val callId = "virt-${UUID.randomUUID().toString().take(8)}"
        val intent = CallSessionActivity.createIntent(
            context = context,
            peerName = scenario.callerPeerId,
            peerIp = scenario.routeHint,
            callId = callId,
            incoming = true,
            peerGlobalId = scenario.callerGlobalId,
            peerPublicKey = null,
            peerWalletAddress = null,
            peerDisplayName = scenario.callerDisplayName
        ).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return callId
    }
}

