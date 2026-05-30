package com.ghalbitnet.meshx2.call

import android.content.Context

interface VoipEngineAdapter {
    val engineName: String

    fun initialize(context: Context): Boolean

    suspend fun startAudioCall(
        context: Context,
        target: VoipTarget,
        signalingChannel: CallSignalingChannel
    ): Boolean

    suspend fun acceptAudioCall(
        context: Context,
        target: VoipTarget,
        signalingChannel: CallSignalingChannel
    ): Boolean

    suspend fun endCall(
        context: Context,
        target: VoipTarget,
        signalingChannel: CallSignalingChannel
    ): Boolean

    suspend fun startVideoCall(
        context: Context,
        target: VoipTarget,
        signalingChannel: CallSignalingChannel
    ): Boolean

    fun stopVideo()

    fun muteMic(muted: Boolean)

    fun setSpeaker(enabled: Boolean)

    fun setConnectionStateListener(listener: ((VoipConnectionState) -> Unit)?)

    fun setAudioQualityListener(listener: ((VoipAudioQuality) -> Unit)?)

    fun isVideoSupported(context: Context, target: VoipTarget): Boolean
}
