package com.ghalbitnet.meshx2.call

interface CallSignalingChannel {
    suspend fun sendOffer(target: VoipTarget, payload: String): Boolean

    suspend fun sendAnswer(target: VoipTarget, payload: String): Boolean

    suspend fun sendIceCandidate(target: VoipTarget, payload: String): Boolean

    suspend fun sendCallInvite(target: VoipTarget, payload: String): Boolean

    suspend fun sendCallAccept(target: VoipTarget, payload: String): Boolean

    suspend fun sendCallEnd(target: VoipTarget, payload: String): Boolean
}
