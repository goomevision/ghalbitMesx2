package com.ghalbitnet.meshx2.call

import android.content.Context
import android.util.Log
import org.json.JSONObject

class WebRtcVoipEngineAdapter : VoipEngineAdapter {
    override val engineName: String = "WebRTC"

    private var connectionStateListener: ((VoipConnectionState) -> Unit)? = null
    private var audioQualityListener: ((VoipAudioQuality) -> Unit)? = null

    override fun initialize(context: Context): Boolean {
        Log.d("GHALBIT-VOIP-SDK", "engine=$engineName initialized=false adapter=scaffold")
        return false
    }

    override suspend fun startAudioCall(context: Context, target: VoipTarget, signalingChannel: CallSignalingChannel): Boolean {
        Log.d("GHALBIT-VOIP", "engine=$engineName startAudio deferred target=${target.globalId ?: target.nodeId}")
        return false
    }

    override suspend fun acceptAudioCall(context: Context, target: VoipTarget, signalingChannel: CallSignalingChannel): Boolean {
        Log.d("GHALBIT-VOIP-SIGNAL", "engine=$engineName accept deferred target=${target.globalId ?: target.nodeId}")
        return false
    }

    override suspend fun endCall(context: Context, target: VoipTarget, signalingChannel: CallSignalingChannel): Boolean {
        connectionStateListener?.invoke(VoipConnectionState.ENDED)
        Log.d("GHALBIT-VOIP-SIGNAL", "engine=$engineName end deferred target=${target.globalId ?: target.nodeId}")
        return false
    }

    override suspend fun startVideoCall(context: Context, target: VoipTarget, signalingChannel: CallSignalingChannel): Boolean {
        Log.d("GHALBIT-VOIP-VIDEO", "engine=$engineName startVideo deferred target=${target.globalId ?: target.nodeId}")
        return false
    }

    override fun stopVideo() {
        Log.d("GHALBIT-VOIP-VIDEO", "engine=$engineName stopVideo")
    }

    override fun muteMic(muted: Boolean) {
        Log.d("GHALBIT-VOIP-AUDIO", "engine=$engineName muted=$muted")
    }

    override fun setSpeaker(enabled: Boolean) {
        Log.d("GHALBIT-VOIP-AUDIO", "engine=$engineName speaker=$enabled")
    }

    override fun setConnectionStateListener(listener: ((VoipConnectionState) -> Unit)?) {
        connectionStateListener = listener
    }

    override fun setAudioQualityListener(listener: ((VoipAudioQuality) -> Unit)?) {
        audioQualityListener = listener
    }

    override fun isVideoSupported(context: Context, target: VoipTarget): Boolean {
        return MediaCapabilityChecker.evaluate(context, target.routeType).videoRecommended
    }
}
