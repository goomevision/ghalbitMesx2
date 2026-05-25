package com.ghalbitnet.meshx2.vpn

import android.content.Context
import com.ghalbitnet.meshx2.economy.InternetBridgeUsageMonitor

object PacketRouter {

    fun isForwarderReady(): Boolean = true

    fun forwardPacket(
        context: Context,
        packet: ByteArray,
        decision: PacketDecisionEngine.PacketDecision
    ): Boolean {
        if (VpnOperatingMode.current(context) == VpnOperatingMode.MONITORING_LIGHT) {
            monitorPacketLight(context, packet)
            return true
        }
        val flowSnapshot = buildFlowSnapshot(context, packet, decision)
        countUsage(flowSnapshot, UsageMeter.Direction.UPLOAD)
        if (VpnOperatingMode.current(context) == VpnOperatingMode.MONITORING_ONLY) {
            VpnLogManager.info(
                "VPN_MONITORING_ONLY_PACKET_COUNTED",
                "flowId=${flowSnapshot.flowId} protocol=${flowSnapshot.protocolName} bytes=${flowSnapshot.packetLength}"
            )
            VpnLogManager.info(
                "VPN_MONITORING_ONLY_NO_DROP",
                "route=${decision.accessDecision.forwardMode.name} parse=${flowSnapshot.parseStatus}"
            )
            return true
        }
        return when (decision.accessDecision.forwardMode) {
            MeshForwardMode.BLOCKED -> {
                VpnPacketPlaneTestLogger.record(
                    if (!decision.accessDecision.allowed) "TEST_FLOW_BLOCKED_BY_POLICY" else "TEST_FLOW_NO_GATEWAY",
                    flowSnapshot.copy(decision = "BLOCKED")
                )
                VpnLogManager.info(
                    "PACKET_ROUTER_BLOCKED",
                    "${decision.detail} | bytes=${packet.size}"
                )
                false
            }
            MeshForwardMode.LOCAL_DIRECT -> {
                VpnPacketPlaneTestLogger.record(
                    "TEST_FLOW_PACKET_ROUTED_MESH_GATEWAY",
                    flowSnapshot.copy(decision = "LOCAL_DIRECT_PENDING")
                )
                LocalDirectForwarder.forward(context, packet, decision)
            }
            MeshForwardMode.MESH_GATEWAY -> {
                MeshGatewayForwarder.forwardRawPacket(context, packet, decision, flowSnapshot)
            }
        }
    }

    fun dropPacket(
        context: Context,
        packet: ByteArray,
        decision: PacketDecisionEngine.PacketDecision
    ) {
        if (VpnOperatingMode.current(context) == VpnOperatingMode.MONITORING_LIGHT) {
            monitorPacketLight(context, packet)
            return
        }
        val flowSnapshot = buildFlowSnapshot(context, packet, decision)
        countUsage(flowSnapshot, UsageMeter.Direction.UPLOAD)
        if (VpnOperatingMode.current(context) == VpnOperatingMode.MONITORING_ONLY) {
            VpnLogManager.info(
                "VPN_MONITORING_ONLY_PACKET_COUNTED",
                "flowId=${flowSnapshot.flowId} protocol=${flowSnapshot.protocolName} bytes=${flowSnapshot.packetLength}"
            )
            VpnLogManager.info(
                "VPN_MONITORING_ONLY_NO_DROP",
                "dropPacket diabaikan karena mode monitoring."
            )
            return
        }
        VpnLogManager.info(
            "PACKET_DROPPED",
            "${decision.detail} | bytes=${packet.size}"
        )
    }

    private fun monitorPacketLight(
        context: Context,
        packet: ByteArray
    ) {
        val nodeId =
            com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager.buildGlobalId(
                com.ghalbitnet.meshx2.security.KeyStoreManager(context).publicKeyBase64
            )
        val sessionId =
            InternetBridgeUsageMonitor.activeSessionSnapshot(context)?.sessionId
                ?: "vpn-$nodeId"
        val packetId = "pkt-${System.currentTimeMillis()}-${packet.size}"
        val parsed = PacketUsageParser.parse(packet)
        UsageMeter.ensureSession(nodeId, sessionId)
        UsageMeter.maybeLogUserIdentified(nodeId, sessionId)
        UsageMeter.record(
            UsageMeter.UsageRecord(
                nodeId = nodeId,
                sessionId = sessionId,
                packetId = packetId,
                timestamp = System.currentTimeMillis(),
                ipVersion = parsed.ipVersion,
                protocol = parsed.protocol,
                sourceIp = parsed.sourceIp,
                sourcePort = parsed.sourcePort,
                destinationIp = parsed.destinationIp,
                destinationPort = parsed.destinationPort,
                packetBytes = parsed.packetBytes,
                direction = UsageMeter.Direction.UPLOAD,
                routeMode = "MONITORING_LIGHT"
            )
        )
        val counter = UsageMeter.snapshotCounter()
        if (counter != null && (counter.totalPackets % 100L == 0L || counter.totalPackets == 1L)) {
            VpnLogManager.info(
                "VPN_MONITORING_LIGHT_PACKET_COUNTED",
                "session=$sessionId packets=${counter.totalPackets} bytesUp=${counter.totalUploadBytes}"
            )
            VpnLogManager.info(
                "VPN_MONITORING_LIGHT_SUMMARY",
                "nodeId=$nodeId session=$sessionId tcp=${counter.tcpPackets} udp=${counter.udpPackets} icmp=${counter.icmpPackets} ipv6=${counter.ipv6Packets} unknown=${counter.unknownPackets}"
            )
        }
    }

    private fun countUsage(
        snapshot: PacketFlowSnapshot,
        direction: UsageMeter.Direction
    ) {
        UsageMeter.ensureSession(snapshot.sourceNodeId, snapshot.sessionId)
        UsageMeter.maybeLogUserIdentified(snapshot.sourceNodeId, snapshot.sessionId)
        UsageMeter.record(
            UsageMeter.UsageRecord(
                nodeId = snapshot.sourceNodeId,
                sessionId = snapshot.sessionId,
                packetId = snapshot.packetId,
                timestamp = snapshot.timestamp,
                ipVersion = snapshot.ipVersion,
                protocol = snapshot.protocolName,
                sourceIp = snapshot.sourceIp,
                sourcePort = snapshot.sourcePort,
                destinationIp = snapshot.destinationIp,
                destinationPort = snapshot.destinationPort,
                packetBytes = snapshot.packetLength,
                direction = direction,
                routeMode = snapshot.routeMode
            )
        )
    }

    private fun buildFlowSnapshot(
        context: Context,
        packet: ByteArray,
        decision: PacketDecisionEngine.PacketDecision
    ): PacketFlowSnapshot {
        val sourceNodeId =
            com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager.buildGlobalId(
                com.ghalbitnet.meshx2.security.KeyStoreManager(context).publicKeyBase64
            )
        val sessionId =
            InternetBridgeUsageMonitor.activeSessionSnapshot(context)?.sessionId
                ?: "vpn-$sourceNodeId"
        val packetId = "pkt-${System.currentTimeMillis()}-${packet.size}"
        val now = System.currentTimeMillis()
        val generic = parseGenericPacket(packet)
        val parsedTcp = parseOutgoingTcp(packet, sessionId)
        val snapshot =
            if (parsedTcp != null) {
                TcpSessionTracker.createOrUpdate(
                    TcpSessionState(
                        sessionId = sessionId,
                        clientIp = parsedTcp.sourceIp,
                        clientPort = parsedTcp.sourcePort,
                        remoteIp = parsedTcp.destIp,
                        remotePort = parsedTcp.destPort,
                        clientSeq = parsedTcp.nextClientSeq,
                        clientAck = parsedTcp.ack,
                        remoteSeq = parsedTcp.remoteSeqSeed,
                        remoteAck = parsedTcp.nextClientSeq,
                        windowSize = parsedTcp.windowSize,
                        gatewayId = decision.accessDecision.gatewayId,
                        connectionState = parsedTcp.connectionState,
                        lastClientSeq = parsedTcp.nextClientSeq,
                        lastClientAck = parsedTcp.ack,
                        lastRemoteSeq = parsedTcp.remoteSeqSeed,
                        lastRemoteAck = parsedTcp.nextClientSeq,
                        clientWindow = parsedTcp.windowSize,
                        remoteWindow = parsedTcp.windowSize,
                        createdAt = now,
                        lastSeen = now,
                        closeReason = parsedTcp.closeReason
                    )
                )
                PacketFlowSnapshot(
                    flowId = "$sessionId+$packetId",
                    sessionId = sessionId,
                    packetId = packetId,
                    protocolName = "TCP",
                    ipVersion = generic.ipVersion,
                    packetLength = packet.size,
                    routeMode = decision.accessDecision.forwardMode.name,
                    sourceNodeId = sourceNodeId,
                    gatewayNodeId = decision.accessDecision.gatewayId,
                    timestamp = now,
                    parseStatus = "TCP_IPV4_VALID",
                    sourceIp = parsedTcp.sourceIp,
                    sourcePort = parsedTcp.sourcePort,
                    destinationIp = parsedTcp.destIp,
                    destinationPort = parsedTcp.destPort,
                    tcpState = parsedTcp.connectionState.name,
                    decision = decision.detail
                ).also {
                    VpnPacketPlaneTestLogger.record("TEST_FLOW_TCP_SNAPSHOT_CREATED", it)
                }
            } else {
                val minimal =
                    PacketFlowSnapshot(
                        flowId = "$sessionId+$packetId",
                        sessionId = sessionId,
                        packetId = packetId,
                        protocolName = generic.protocolName,
                        ipVersion = generic.ipVersion,
                        packetLength = packet.size,
                        routeMode = decision.accessDecision.forwardMode.name,
                        sourceNodeId = sourceNodeId,
                        gatewayNodeId = decision.accessDecision.gatewayId,
                        timestamp = now,
                        parseStatus = generic.parseStatus,
                        sourceIp = generic.sourceIp,
                        sourcePort = generic.sourcePort,
                        destinationIp = generic.destinationIp,
                        destinationPort = generic.destinationPort,
                        tcpState = null,
                        decision = decision.detail
                    )
                VpnPacketPlaneTestLogger.record("TEST_FLOW_MINIMAL_SNAPSHOT_USED", minimal)
                when (generic.protocolName) {
                    "IPV6" -> VpnPacketPlaneTestLogger.record("TEST_FLOW_IPV6_FORWARD_PENDING", minimal)
                    "UDP" -> VpnPacketPlaneTestLogger.record("TEST_FLOW_UDP_FORWARD_PENDING", minimal)
                    "ICMP" -> VpnPacketPlaneTestLogger.record("TEST_FLOW_ICMP_FORWARD_PENDING", minimal)
                    "TCP" -> VpnPacketPlaneTestLogger.record("TEST_FLOW_TCP_PARTIAL_FORWARD_PENDING", minimal)
                    else -> VpnPacketPlaneTestLogger.record("TEST_FLOW_UNKNOWN_PROTOCOL_FORWARD_PENDING", minimal)
                }
                if (generic.protocolName != "TCP") {
                    VpnPacketPlaneTestLogger.record("TEST_FLOW_NON_TCP_FORWARD_PENDING", minimal)
                }
                minimal
            }
        VpnPacketPlaneTestLogger.record("TEST_FLOW_TUN_PACKET_READ", snapshot)
        return snapshot
    }

    private fun parseGenericPacket(packet: ByteArray): GenericPacketMeta {
        if (packet.isEmpty()) {
            return GenericPacketMeta(
                ipVersion = 0,
                protocolName = "UNKNOWN",
                parseStatus = "EMPTY_PACKET"
            )
        }
        val version = (packet[0].toInt() ushr 4) and 0x0F
        return when (version) {
            4 -> parseIpv4Meta(packet)
            6 -> parseIpv6Meta(packet)
            else -> GenericPacketMeta(
                ipVersion = version,
                protocolName = "UNKNOWN",
                parseStatus = "UNKNOWN_IP_VERSION"
            )
        }
    }

    private fun parseIpv4Meta(packet: ByteArray): GenericPacketMeta {
        if (packet.size < 20) {
            return GenericPacketMeta(ipVersion = 4, protocolName = "UNKNOWN", parseStatus = "IPV4_TOO_SHORT")
        }
        val ihlBytes = (packet[0].toInt() and 0x0F) * 4
        val protocol = packet[9].toInt() and 0xFF
        val protocolName =
            when (protocol) {
                6 -> "TCP"
                17 -> "UDP"
                1 -> "ICMP"
                else -> "UNKNOWN"
            }
        val sourceIp =
            "${packet[12].toInt() and 0xFF}.${packet[13].toInt() and 0xFF}.${packet[14].toInt() and 0xFF}.${packet[15].toInt() and 0xFF}"
        val destinationIp =
            "${packet[16].toInt() and 0xFF}.${packet[17].toInt() and 0xFF}.${packet[18].toInt() and 0xFF}.${packet[19].toInt() and 0xFF}"
        val hasPorts = (protocol == 6 || protocol == 17) && packet.size >= ihlBytes + 4
        val sourcePort =
            if (hasPorts) {
                ((packet[ihlBytes].toInt() and 0xFF) shl 8) or
                    (packet[ihlBytes + 1].toInt() and 0xFF)
            } else {
                null
            }
        val destinationPort =
            if (hasPorts) {
                ((packet[ihlBytes + 2].toInt() and 0xFF) shl 8) or
                    (packet[ihlBytes + 3].toInt() and 0xFF)
            } else {
                null
            }
        return GenericPacketMeta(
            ipVersion = 4,
            protocolName = protocolName,
            parseStatus = "IPV4_${protocolName}_PARTIAL",
            sourceIp = sourceIp,
            sourcePort = sourcePort,
            destinationIp = destinationIp,
            destinationPort = destinationPort
        )
    }

    private fun parseIpv6Meta(packet: ByteArray): GenericPacketMeta {
        if (packet.size < 40) {
            return GenericPacketMeta(ipVersion = 6, protocolName = "IPV6", parseStatus = "IPV6_TOO_SHORT")
        }
        val nextHeader = packet[6].toInt() and 0xFF
        val protocolName =
            when (nextHeader) {
                6 -> "TCP"
                17 -> "UDP"
                58 -> "ICMP"
                else -> "IPV6"
            }
        return GenericPacketMeta(
            ipVersion = 6,
            protocolName = protocolName,
            parseStatus = "IPV6_FORWARD_PENDING"
        )
    }

    private fun parseOutgoingTcp(
        packet: ByteArray,
        sessionId: String
    ): OutgoingTcpMeta? {
        if (packet.isEmpty()) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != 4 || packet.size < 40) return null
        val ihlBytes = (packet[0].toInt() and 0x0F) * 4
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 6 || packet.size < ihlBytes + 20) return null
        val sourceIp =
            "${packet[12].toInt() and 0xFF}.${packet[13].toInt() and 0xFF}.${packet[14].toInt() and 0xFF}.${packet[15].toInt() and 0xFF}"
        val destIp =
            "${packet[16].toInt() and 0xFF}.${packet[17].toInt() and 0xFF}.${packet[18].toInt() and 0xFF}.${packet[19].toInt() and 0xFF}"
        val sourcePort =
            ((packet[ihlBytes].toInt() and 0xFF) shl 8) or
                (packet[ihlBytes + 1].toInt() and 0xFF)
        val destPort =
            ((packet[ihlBytes + 2].toInt() and 0xFF) shl 8) or
                (packet[ihlBytes + 3].toInt() and 0xFF)
        val seq =
            ((packet[ihlBytes + 4].toLong() and 0xFF) shl 24) or
                ((packet[ihlBytes + 5].toLong() and 0xFF) shl 16) or
                ((packet[ihlBytes + 6].toLong() and 0xFF) shl 8) or
                (packet[ihlBytes + 7].toLong() and 0xFF)
        val ack =
            ((packet[ihlBytes + 8].toLong() and 0xFF) shl 24) or
                ((packet[ihlBytes + 9].toLong() and 0xFF) shl 16) or
                ((packet[ihlBytes + 10].toLong() and 0xFF) shl 8) or
                (packet[ihlBytes + 11].toLong() and 0xFF)
        val tcpHeaderLength = ((packet[ihlBytes + 12].toInt() ushr 4) and 0x0F) * 4
        val flags = packet[ihlBytes + 13].toInt() and 0x3F
        val parsedFlags = TcpFlagParser.parse(flags)
        val windowSize =
            ((packet[ihlBytes + 14].toInt() and 0xFF) shl 8) or
                (packet[ihlBytes + 15].toInt() and 0xFF)
        val payloadLength = (packet.size - ihlBytes - tcpHeaderLength).coerceAtLeast(0)
        val synInc = if ((flags and TcpHeaderBuilder.FLAG_SYN) != 0) 1 else 0
        val finInc = if ((flags and TcpHeaderBuilder.FLAG_FIN) != 0) 1 else 0
        val currentState = TcpSessionTracker.get(sessionId)?.connectionState
        val transition = TcpStateMachine.onClientPacket(currentState, parsedFlags, payloadLength)
        if (!transition.valid) {
            VpnLogManager.warn(
                "TCP_INVALID_STATE_TRANSITION",
                "session=$sessionId state=${currentState ?: TcpConnectionState.CLOSED} flags=${parsedFlags.label()} payload=$payloadLength TCP_TRANSLATION_STAGE_2_STATE_MACHINE"
            )
            return null
        }
        return OutgoingTcpMeta(
            sourceIp = sourceIp,
            destIp = destIp,
            sourcePort = sourcePort,
            destPort = destPort,
            nextClientSeq = seq + payloadLength + synInc + finInc,
            ack = ack,
            remoteSeqSeed = ack
                .takeIf { it > 0L }
                ?: 1L,
            windowSize = windowSize,
            connectionState = transition.nextState,
            closeReason = transition.closeReason,
            payloadLength = payloadLength
        )
    }

    private data class OutgoingTcpMeta(
        val sourceIp: String,
        val destIp: String,
        val sourcePort: Int,
        val destPort: Int,
        val nextClientSeq: Long,
        val ack: Long,
        val remoteSeqSeed: Long,
        val windowSize: Int,
        val connectionState: TcpConnectionState,
        val closeReason: String?,
        val payloadLength: Int
    )

    private data class GenericPacketMeta(
        val ipVersion: Int,
        val protocolName: String,
        val parseStatus: String,
        val sourceIp: String? = null,
        val sourcePort: Int? = null,
        val destinationIp: String? = null,
        val destinationPort: Int? = null
    )
}
