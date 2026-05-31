package com.ghalbitnet.meshx2.call

import com.ghalbitnet.meshx2.activityfeed.ActivityFeedManager
import com.ghalbitnet.meshx2.activityfeed.ActivityFeedType

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
        publishCallEvent(
            type = ActivityFeedType.CALL_STARTED,
            title = if (state == CallState.INCOMING) "Panggilan masuk" else "Panggilan dimulai",
            message = "Call $callId dengan $peerName melalui ${routeHint ?: peerIp} berada pada state $state.",
            callId = callId,
            peerName = peerName,
            state = state,
            routeHint = routeHint ?: peerIp
        )
    }

    fun updateSession(session: CallSession) {
        val previousState = activeState
        activeSession = session
        activeCallId = session.callId
        activePeerName = session.remoteNodeId
        activePeerIp = session.routeHint
        activePeerGlobalId = session.remoteGlobalId
        activeState = session.state
        if (previousState != session.state) {
            publishCallEvent(
                type = when (session.state) {
                    CallState.ENDED -> ActivityFeedType.CALL_ENDED
                    CallState.MISSED -> ActivityFeedType.CALL_MISSED
                    else -> ActivityFeedType.CALL_STARTED
                },
                title = callTitleFor(session.state),
                message = "Call ${session.callId} dengan ${session.remoteNodeId} berubah dari $previousState ke ${session.state}.",
                callId = session.callId,
                peerName = session.remoteNodeId,
                state = session.state,
                routeHint = session.routeHint
            )
        }
    }

    fun clear() {
        val callId = activeCallId
        val peerName = activePeerName
        val state = activeState
        val routeHint = activePeerIp
        activeCallId = null
        activePeerName = null
        activePeerIp = null
        activePeerGlobalId = null
        activeState = CallState.IDLE
        activeSession = null
        if (!callId.isNullOrBlank()) {
            publishCallEvent(
                type = if (state == CallState.MISSED) ActivityFeedType.CALL_MISSED else ActivityFeedType.CALL_ENDED,
                title = if (state == CallState.MISSED) "Panggilan tidak terjawab" else "Panggilan selesai",
                message = "Call $callId dengan ${peerName ?: "peer"} selesai dari state $state.",
                callId = callId,
                peerName = peerName ?: "peer",
                state = state,
                routeHint = routeHint
            )
        }
    }

    fun isSameCall(callId: String?): Boolean {
        return !callId.isNullOrBlank() &&
            activeCallId == callId
    }

    fun isBusy(): Boolean {
        return !activeCallId.isNullOrBlank()
    }

    private fun callTitleFor(state: CallState): String {
        return when (state) {
            CallState.IDLE -> "Panggilan idle"
            CallState.INCOMING -> "Panggilan masuk"
            CallState.OUTGOING -> "Panggilan keluar"
            CallState.RINGING -> "Panggilan berdering"
            CallState.CONNECTING -> "Panggilan menghubungkan"
            CallState.CONNECTED -> "Panggilan tersambung"
            CallState.ENDED -> "Panggilan berakhir"
            CallState.MISSED -> "Panggilan tidak terjawab"
        }
    }

    private fun publishCallEvent(
        type: ActivityFeedType,
        title: String,
        message: String,
        callId: String,
        peerName: String,
        state: CallState,
        routeHint: String?
    ) {
        ActivityFeedManager.publish(
            type = type,
            title = title,
            message = message,
            peerId = peerName,
            source = "VoiceCallRegistry",
            metadata = "{\"callId\":\"$callId\",\"peer\":\"$peerName\",\"state\":\"$state\",\"routeHint\":\"${routeHint ?: ""}\"}"
        )
    }
}
