package com.ghalbitnet.meshx2.call

object VoiceCallRegistry {

    @Volatile
    var activeCallId: String? = null
        private set

    @Volatile
    var activePeerName: String? = null
        private set

    @Volatile
    var activePeerIp: String? = null
        private set

    fun start(
        callId: String,
        peerName: String,
        peerIp: String
    ) {
        activeCallId = callId
        activePeerName = peerName
        activePeerIp = peerIp
    }

    fun clear() {
        activeCallId = null
        activePeerName = null
        activePeerIp = null
    }

    fun isSameCall(callId: String?): Boolean {
        return !callId.isNullOrBlank() &&
            activeCallId == callId
    }

    fun isBusy(): Boolean {
        return !activeCallId.isNullOrBlank()
    }
}
