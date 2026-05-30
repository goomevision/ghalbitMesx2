package com.ghalbitnet.meshx2.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.ghalbitnet.meshx2.core.utils.GhalbitDeepLinkRouter

class CallNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(CallSessionActivity.EXTRA_CALL_ID).orEmpty()
        val peerName = intent.getStringExtra(CallSessionActivity.EXTRA_PEER_NAME).orEmpty()
        val peerIp = intent.getStringExtra(CallSessionActivity.EXTRA_PEER_IP).orEmpty()
        val peerGlobalId = intent.getStringExtra(CallSessionActivity.EXTRA_PEER_GLOBAL_ID)
        val peerPublicKey = intent.getStringExtra(CallSessionActivity.EXTRA_PEER_PUBLIC_KEY)
        val peerWalletAddress = intent.getStringExtra(CallSessionActivity.EXTRA_PEER_WALLET_ADDRESS)
        val peerDisplayName = intent.getStringExtra(CallSessionActivity.EXTRA_PEER_DISPLAY_NAME)
        val action = intent.action ?: GhalbitDeepLinkRouter.ACTION_OPEN_CALL
        CallRingtoneManager.stopIfCall(callId, "notification:$action")
        Log.d("GHALBIT-CALL-RING", "stop from notification action")
        when (action) {
            GhalbitDeepLinkRouter.ACTION_ACCEPT_CALL -> Log.d("GHALBIT-CALL", "notification accept callId=$callId")
            GhalbitDeepLinkRouter.ACTION_REJECT_CALL -> Log.d("GHALBIT-CALL", "notification reject callId=$callId")
            else -> Log.d("GHALBIT-DEEPLINK", "open call callId=$callId")
        }
        val next =
            GhalbitDeepLinkRouter.callIntent(
                context = context,
                peerName = peerName,
                peerIp = peerIp,
                callId = callId,
                peerGlobalId = peerGlobalId,
                peerPublicKey = peerPublicKey,
                peerWalletAddress = peerWalletAddress,
                peerDisplayName = peerDisplayName,
                action = action
            )
        ContextCompat.startActivity(context, next, null)
    }
}
