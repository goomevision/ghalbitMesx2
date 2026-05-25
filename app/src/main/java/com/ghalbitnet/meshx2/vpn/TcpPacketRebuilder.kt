package com.ghalbitnet.meshx2.vpn

object TcpPacketRebuilder {

    fun rebuildReturnPacket(
        sessionId: String,
        payload: ByteArray
    ): ByteArray? {
        VpnLogManager.info("TCP_RETURN_REBUILD_STARTED", "session=$sessionId bytes=${payload.size} TCP_TRANSLATION_STAGE_1")
        val state = TcpSessionTracker.get(sessionId)
        if (state == null) {
            VpnLogManager.warn("TCP_RETURN_SESSION_NOT_FOUND", "session=$sessionId tidak ditemukan")
            return null
        }
        if (payload.isEmpty()) {
            VpnLogManager.warn("TCP_RETURN_INVALID_PAYLOAD", "payload kosong untuk session=$sessionId")
            return null
        }

        return runCatching {
            VpnLogManager.info(
                "TCP_REBUILD_WITH_STATE",
                "session=$sessionId state=${state.connectionState} clientSeq=${state.clientSeq} remoteSeq=${state.remoteSeq} TCP_TRANSLATION_STAGE_2_STATE_MACHINE"
            )
            val sourceIp = Ipv4PacketBuilder.ipToBytes(state.remoteIp)
            val destIp = Ipv4PacketBuilder.ipToBytes(state.clientIp)
            val transition =
                TcpStateMachine.onGatewayPayload(
                    current = state.connectionState,
                    payloadLength = payload.size
                )
            if (!transition.valid) {
                VpnLogManager.warn(
                    "TCP_INVALID_STATE_TRANSITION",
                    "rebuild return session=$sessionId state=${state.connectionState} reason=${transition.closeReason}"
                )
                return null
            }
            val ackFlags =
                if (payload.isNotEmpty()) {
                    TcpHeaderBuilder.FLAG_PSH or TcpHeaderBuilder.FLAG_ACK
                } else {
                    TcpHeaderBuilder.FLAG_ACK
                }
            val tcpHeader =
                TcpHeaderBuilder.buildTcpHeader(
                    sourcePort = state.remotePort,
                    destPort = state.clientPort,
                    seq = state.lastRemoteSeq,
                    ack = state.lastClientSeq,
                    flags = ackFlags,
                    window = state.remoteWindow.coerceAtLeast(1024),
                    payload = payload,
                    sourceIp = sourceIp,
                    destIp = destIp
                )
            val packet =
                Ipv4PacketBuilder.buildIpv4Packet(
                    sourceIp = state.remoteIp,
                    destIp = state.clientIp,
                    protocol = 6,
                    payload = tcpHeader + payload
                )
            TcpSessionTracker.updateAfterReturn(sessionId, payload.size)
            VpnLogManager.info("TCP_RETURN_REBUILD_SUCCESS", "session=$sessionId packet=${packet.size} byte")
            packet
        }.onFailure {
            VpnLogManager.error("TCP_RETURN_REBUILD_FAILED", "session=$sessionId gagal rebuild", it)
        }.getOrNull()
    }
}
