package com.ghalbitnet.meshx2.call

import android.content.Context
import com.ghalbitnet.meshx2.online.InternetRoute
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport

class InternetRelaySignalingChannel(
    private val context: Context,
    private val route: InternetRoute
) : CallSignalingChannel {
    override suspend fun sendOffer(target: VoipTarget, payload: String): Boolean =
        OnlineFallbackTransport.sendCallSignalViaInternet(context, route, CallManager.SIGNAL_CALL_WEBRTC_OFFER, payload)

    override suspend fun sendAnswer(target: VoipTarget, payload: String): Boolean =
        OnlineFallbackTransport.sendCallSignalViaInternet(context, route, CallManager.SIGNAL_CALL_WEBRTC_ANSWER, payload)

    override suspend fun sendIceCandidate(target: VoipTarget, payload: String): Boolean =
        OnlineFallbackTransport.sendCallSignalViaInternet(context, route, CallManager.SIGNAL_CALL_WEBRTC_ICE, payload)

    override suspend fun sendCallInvite(target: VoipTarget, payload: String): Boolean =
        OnlineFallbackTransport.sendCallSignalViaInternet(context, route, CallManager.SIGNAL_CALL_INVITE, payload)

    override suspend fun sendCallAccept(target: VoipTarget, payload: String): Boolean =
        OnlineFallbackTransport.sendCallSignalViaInternet(context, route, CallManager.SIGNAL_CALL_ACCEPT, payload)

    override suspend fun sendCallEnd(target: VoipTarget, payload: String): Boolean =
        OnlineFallbackTransport.sendCallSignalViaInternet(context, route, CallManager.SIGNAL_CALL_END, payload)
}
