package com.ghalbitnet.meshx2.vpn

import android.content.Context

object VpnEndToEndTestReport {

    fun dumpVpnFlows(): String = VpnPacketPlaneTestLogger.dumpVpnFlows()

    fun dumpTcpSessions(): String {
        return TcpSessionTracker.snapshot().joinToString("\n") { state ->
            "${state.sessionId} | ${state.connectionState} | ${state.clientIp}:${state.clientPort} -> ${state.remoteIp}:${state.remotePort} | cSeq=${state.clientSeq} rSeq=${state.remoteSeq} | reason=${state.closeReason ?: "-"}"
        }
    }

    fun dumpGatewaySessions(): String {
        return GatewayTcpSessionManager.snapshot().joinToString("\n") { session ->
            "${session.sessionId} | ${session.clientNodeId} | ${session.destinationAddress}:${session.destinationPort} | lastSeen=${session.lastSeen}"
        }
    }

    fun dumpNatTable(context: Context): String {
        return GatewayNatTable.snapshot(context).joinToString("\n") { entry ->
            "${entry.sessionId} | ${entry.clientNodeId} | ${entry.protocol} | ${entry.sourceAddress}:${entry.sourcePort} -> ${entry.destinationAddress}:${entry.destinationPort} | lastSeen=${entry.lastSeen}"
        }
    }
}
