package com.ghalbitnet.meshx2.vpn

import java.util.concurrent.CopyOnWriteArrayList

object VpnPacketPlaneTestLogger {

    private const val MAX_LOGS = 400
    private val flows = CopyOnWriteArrayList<Pair<String, PacketFlowSnapshot>>()

    fun record(
        stage: String,
        snapshot: PacketFlowSnapshot
    ) {
        if (flows.size >= MAX_LOGS) {
            flows.removeAt(0)
        }
        flows += stage to snapshot
        VpnLogManager.info(
            stage,
            "flowId=${snapshot.flowId} srcNode=${snapshot.sourceNodeId} gateway=${snapshot.gatewayNodeId} session=${snapshot.sessionId} packet=${snapshot.packetId} protocol=${snapshot.protocolName} ipVersion=${snapshot.ipVersion} route=${snapshot.routeMode} state=${snapshot.tcpState ?: "-"} ${snapshot.sourceIp ?: "-"}:${snapshot.sourcePort ?: 0} -> ${snapshot.destinationIp ?: "-"}:${snapshot.destinationPort ?: 0} payload=${snapshot.packetLength} parse=${snapshot.parseStatus} decision=${snapshot.decision}"
        )
    }

    fun dumpVpnFlows(): String {
        return flows.joinToString("\n") { (stage, snapshot) ->
            "${snapshot.timestamp} | $stage | flowId=${snapshot.flowId} | ${snapshot.protocolName} | ipv=${snapshot.ipVersion} | ${snapshot.routeMode} | ${snapshot.tcpState ?: "-"} | ${snapshot.sourceIp ?: "-"}:${snapshot.sourcePort ?: 0} -> ${snapshot.destinationIp ?: "-"}:${snapshot.destinationPort ?: 0} | payload=${snapshot.packetLength} | parse=${snapshot.parseStatus} | decision=${snapshot.decision}"
        }
    }
}
