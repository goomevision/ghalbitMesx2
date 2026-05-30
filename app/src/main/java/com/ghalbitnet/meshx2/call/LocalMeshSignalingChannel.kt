package com.ghalbitnet.meshx2.call

import android.content.Context

class LocalMeshSignalingChannel(
    private val context: Context,
    private val peer: CallPeerEndpoint,
    private val localNodeId: String,
    private val localGlobalId: String?,
    private val localPublicKeyHash: String?
) : CallSignalingChannel {
    override suspend fun sendOffer(target: VoipTarget, payload: String): Boolean =
        CallManager.sendCustomSignal(context, peer, CallManager.SIGNAL_CALL_WEBRTC_OFFER, payload, localNodeId)

    override suspend fun sendAnswer(target: VoipTarget, payload: String): Boolean =
        CallManager.sendCustomSignal(context, peer, CallManager.SIGNAL_CALL_WEBRTC_ANSWER, payload, localNodeId)

    override suspend fun sendIceCandidate(target: VoipTarget, payload: String): Boolean =
        CallManager.sendCustomSignal(context, peer, CallManager.SIGNAL_CALL_WEBRTC_ICE, payload, localNodeId)

    override suspend fun sendCallInvite(target: VoipTarget, payload: String): Boolean =
        CallManager.sendSignal(context, peer, CallManager.SIGNAL_CALL_INVITE, target.callId, localNodeId, localGlobalId, localPublicKeyHash)

    override suspend fun sendCallAccept(target: VoipTarget, payload: String): Boolean =
        CallManager.sendSignal(context, peer, CallManager.SIGNAL_CALL_ACCEPT, target.callId, localNodeId, localGlobalId, localPublicKeyHash)

    override suspend fun sendCallEnd(target: VoipTarget, payload: String): Boolean =
        CallManager.sendSignal(context, peer, CallManager.SIGNAL_CALL_END, target.callId, localNodeId, localGlobalId, localPublicKeyHash)
}
