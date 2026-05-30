package com.ghalbitnet.meshx2.call

import android.content.Context
import android.util.Log
import org.json.JSONObject

class LinphoneVoipEngineAdapter : VoipEngineAdapter {
    override val engineName: String = "Linphone"

    private var initialized = false
    private var connectionStateListener: ((VoipConnectionState) -> Unit)? = null
    private var audioQualityListener: ((VoipAudioQuality) -> Unit)? = null

    override fun initialize(context: Context): Boolean {
        if (initialized) return true
        initialized = true
        connectionStateListener?.invoke(VoipConnectionState.IDLE)
        Log.w("GHALBIT-VOIP-SDK", "engine=$engineName stub active: Linphone engine belum aktif pada build ini")
        return true
    }

    override suspend fun startAudioCall(context: Context, target: VoipTarget, signalingChannel: CallSignalingChannel): Boolean {
        if (!initialize(context)) return false
        connectionStateListener?.invoke(VoipConnectionState.CONNECTING)
        audioQualityListener?.invoke(VoipAudioQuality.UNKNOWN)
        val payload =
            JSONObject()
                .put("callId", target.callId)
                .put("targetNodeId", target.nodeId)
                .put("targetGlobalId", target.globalId)
                .put("mode", "audio")
                .put("engine", engineName)
                .put("route", target.routeType.name)
                .toString()
        val sent = signalingChannel.sendCallInvite(target, payload)
        Log.d("GHALBIT-VOIP", "engine=$engineName startAudio target=${target.globalId ?: target.nodeId} sent=$sent")
        return sent
    }

    override suspend fun acceptAudioCall(context: Context, target: VoipTarget, signalingChannel: CallSignalingChannel): Boolean {
        if (!initialize(context)) return false
        connectionStateListener?.invoke(VoipConnectionState.CONNECTING)
        val payload =
            JSONObject()
                .put("callId", target.callId)
                .put("mode", "audio")
                .put("engine", engineName)
                .put("route", target.routeType.name)
                .toString()
        val sent = signalingChannel.sendCallAccept(target, payload)
        Log.d("GHALBIT-VOIP-SIGNAL", "engine=$engineName accept target=${target.globalId ?: target.nodeId} sent=$sent")
        return sent
    }

    override suspend fun endCall(context: Context, target: VoipTarget, signalingChannel: CallSignalingChannel): Boolean {
        connectionStateListener?.invoke(VoipConnectionState.ENDED)
        val payload = JSONObject().put("callId", target.callId).put("engine", engineName).toString()
        val sent = signalingChannel.sendCallEnd(target, payload)
        Log.d("GHALBIT-VOIP-SIGNAL", "engine=$engineName end target=${target.globalId ?: target.nodeId} sent=$sent")
        return sent
    }

    override suspend fun startVideoCall(context: Context, target: VoipTarget, signalingChannel: CallSignalingChannel): Boolean {
        if (!isVideoSupported(context, target)) {
            Log.w("GHALBIT-VOIP-VIDEO", "engine=$engineName video not recommended target=${target.globalId ?: target.nodeId}")
            return false
        }
        if (!initialize(context)) return false
        val payload =
            JSONObject()
                .put("callId", target.callId)
                .put("targetNodeId", target.nodeId)
                .put("targetGlobalId", target.globalId)
                .put("mode", "video")
                .put("engine", engineName)
                .put("route", target.routeType.name)
                .toString()
        val sent = signalingChannel.sendCallInvite(target, payload)
        Log.d("GHALBIT-VOIP-VIDEO", "engine=$engineName startVideo target=${target.globalId ?: target.nodeId} sent=$sent")
        return sent
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

    fun isInitialized(): Boolean = initialized
}
