package com.ghalbitnet.meshx2.call

import android.content.Context
import android.util.Log

class SipVoipEngineAdapter : VoipEngineAdapter {
    override val engineName: String = "SIP_PLACEHOLDER"

    override fun initialize(context: Context): Boolean {
        Log.d("GHALBIT-VOIP-SDK", "engine=$engineName initialized=false")
        return false
    }

    override suspend fun startAudioCall(context: Context, target: VoipTarget, signalingChannel: CallSignalingChannel): Boolean = false

    override suspend fun acceptAudioCall(context: Context, target: VoipTarget, signalingChannel: CallSignalingChannel): Boolean = false

    override suspend fun endCall(context: Context, target: VoipTarget, signalingChannel: CallSignalingChannel): Boolean = false

    override suspend fun startVideoCall(context: Context, target: VoipTarget, signalingChannel: CallSignalingChannel): Boolean = false

    override fun stopVideo() = Unit

    override fun muteMic(muted: Boolean) = Unit

    override fun setSpeaker(enabled: Boolean) = Unit

    override fun setConnectionStateListener(listener: ((VoipConnectionState) -> Unit)?) = Unit

    override fun setAudioQualityListener(listener: ((VoipAudioQuality) -> Unit)?) = Unit

    override fun isVideoSupported(context: Context, target: VoipTarget): Boolean = false
}
