package com.ghalbitnet.meshx2.diagnostics.virtualcall

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.call.CallSessionActivity
import java.util.UUID

object VirtualCallerTool {
    data class TriggerResult(
        val callId: String,
        val success: Boolean,
        val failStage: String? = null,
        val reason: String? = null
    )

    fun run(context: Context, scenario: VirtualCallScenario): TriggerResult {
        Log.i("GHALBIT-VIRTUAL-CALL", "TOOL_START")
        val callId = "virt-${UUID.randomUUID().toString().take(8)}"
        return runCatching {
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
            TriggerResult(callId = callId, success = true)
        }.getOrElse {
            Log.e("GHALBIT-VIRTUAL-CALL", "FAIL_STAGE stage=OPEN_CALL_ACTIVITY reason=${it.message}")
            TriggerResult(
                callId = callId,
                success = false,
                failStage = "OPEN_CALL_ACTIVITY",
                reason = it.message
            )
        }
    }
}
