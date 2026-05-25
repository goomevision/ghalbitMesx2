package com.ghalbitnet.meshx2.vpn

object TcpStateMachine {

    data class TransitionResult(
        val nextState: TcpConnectionState,
        val valid: Boolean,
        val closeReason: String?
    )

    fun onClientPacket(
        current: TcpConnectionState?,
        flags: TcpFlagParser.Flags,
        payloadLength: Int
    ): TransitionResult {
        val state = current ?: TcpConnectionState.CLOSED
        return when {
            flags.rst -> TransitionResult(TcpConnectionState.RESET, true, "RST_FROM_CLIENT")
            flags.syn && !flags.ack && state == TcpConnectionState.CLOSED ->
                TransitionResult(TcpConnectionState.SYN_SENT, true, null)
            flags.ack && (state == TcpConnectionState.SYN_SENT || state == TcpConnectionState.SYN_RECEIVED) ->
                TransitionResult(TcpConnectionState.ESTABLISHED, true, null)
            flags.fin && state == TcpConnectionState.ESTABLISHED ->
                TransitionResult(TcpConnectionState.FIN_WAIT, true, "FIN_FROM_CLIENT")
            flags.fin && state == TcpConnectionState.CLOSE_WAIT ->
                TransitionResult(TcpConnectionState.LAST_ACK, true, "FIN_AFTER_CLOSE_WAIT")
            (flags.psh && flags.ack && state == TcpConnectionState.ESTABLISHED) || (payloadLength > 0 && state == TcpConnectionState.ESTABLISHED) ->
                TransitionResult(TcpConnectionState.ESTABLISHED, true, null)
            flags.ack && state == TcpConnectionState.FIN_WAIT ->
                TransitionResult(TcpConnectionState.TIME_WAIT, true, "ACK_AFTER_FIN")
            flags.ack && state == TcpConnectionState.ESTABLISHED ->
                TransitionResult(TcpConnectionState.ESTABLISHED, true, null)
            else ->
                TransitionResult(state, false, "CLIENT_FLAGS_${flags.label()}_FROM_$state")
        }
    }

    fun onGatewayPayload(
        current: TcpConnectionState?,
        payloadLength: Int
    ): TransitionResult {
        val state = current ?: TcpConnectionState.CLOSED
        return when (state) {
            TcpConnectionState.SYN_SENT,
            TcpConnectionState.SYN_RECEIVED,
            TcpConnectionState.ESTABLISHED -> TransitionResult(
                TcpConnectionState.ESTABLISHED,
                true,
                null
            )
            TcpConnectionState.FIN_WAIT -> TransitionResult(
                TcpConnectionState.CLOSE_WAIT,
                true,
                "REMOTE_AFTER_FIN"
            )
            TcpConnectionState.CLOSE_WAIT,
            TcpConnectionState.LAST_ACK,
            TcpConnectionState.TIME_WAIT,
            TcpConnectionState.RESET,
            TcpConnectionState.CLOSED -> TransitionResult(
                state,
                payloadLength == 0,
                if (payloadLength == 0) null else "GATEWAY_PAYLOAD_IN_$state"
            )
        }
    }

    fun onGatewayClose(
        current: TcpConnectionState?,
        hardReset: Boolean
    ): TransitionResult {
        val state = current ?: TcpConnectionState.CLOSED
        return if (hardReset) {
            TransitionResult(TcpConnectionState.RESET, true, "RST_FROM_GATEWAY")
        } else {
            when (state) {
                TcpConnectionState.ESTABLISHED -> TransitionResult(TcpConnectionState.CLOSE_WAIT, true, "FIN_FROM_GATEWAY")
                TcpConnectionState.FIN_WAIT -> TransitionResult(TcpConnectionState.TIME_WAIT, true, "FIN_BOTH_SIDES")
                TcpConnectionState.CLOSE_WAIT,
                TcpConnectionState.LAST_ACK,
                TcpConnectionState.TIME_WAIT,
                TcpConnectionState.RESET,
                TcpConnectionState.CLOSED,
                TcpConnectionState.SYN_SENT,
                TcpConnectionState.SYN_RECEIVED -> TransitionResult(TcpConnectionState.CLOSED, true, "GATEWAY_CLOSED")
            }
        }
    }
}
