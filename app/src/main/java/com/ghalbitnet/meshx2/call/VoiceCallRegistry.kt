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

    @Volatile
    var activePeerGlobalId: String? = null
        private set

    @Volatile
    var activeState: CallState = CallState.IDLE
        private set

    @Volatile
    var activeSession: CallSession? = null
        private set

    fun start(
        callId: String,
        peerName: String,
        peerIp: String,
        peerGlobalId: String? = null,
        localNodeId: String = "",
        routeHint: String? = peerIp,
        state: CallState = CallState.OUTGOING
    ) {
        activeCallId = callId
        activePeerName = peerName
        activePeerIp = peerIp
        activePeerGlobalId = peerGlobalId
        activeState = state
        activeSession =
            CallSession(
                callId = callId,
                localNodeId = localNodeId,
                remoteNodeId = peerName,
                remoteGlobalId = peerGlobalId,
                state = state,
                routeHint = routeHint
            )
    }

    fun updateSession(session: CallSession) {
        activeSession = session
        activeCallId = session.callId
        activePeerName = session.remoteNodeId
        activePeerIp = session.routeHint
        activePeerGlobalId = session.remoteGlobalId
        activeState = session.state
    }

    fun clear() {
        activeCallId = null
        activePeerName = null
        activePeerIp = null
        activePeerGlobalId = null
        activeState = CallState.IDLE
        activeSession = null
    }

    fun isSameCall(callId: String?): Boolean {
        return !callId.isNullOrBlank() &&
            activeCallId == callId
    }

    fun isBusy(): Boolean {
        return !activeCallId.isNullOrBlank()
    }
}
