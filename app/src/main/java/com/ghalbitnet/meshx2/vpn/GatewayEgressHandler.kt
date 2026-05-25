package com.ghalbitnet.meshx2.vpn

import android.content.Context
import android.util.Base64
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
import org.json.JSONObject

object GatewayEgressHandler {

    data class ParsedPacket(
        val ipVersion: Int,
        val protocol: String,
        val sourceAddress: String,
        val sourcePort: Int,
        val destinationAddress: String,
        val destinationPort: Int,
        val ipHeaderLength: Int,
        val transportHeaderLength: Int,
        val tcpFlags: Int,
        val payload: ByteArray
    )

    fun handle(
        context: Context,
        clientNodeId: String,
        sessionId: String,
        packetBytes: ByteArray,
        remoteHost: String
    ): GatewayEgressResult {
        val parsed = parsePacket(packetBytes)
        if (parsed == null) {
            VpnLogManager.warn(
                "GATEWAY_EGRESS_UNSUPPORTED",
                "Packet dari $clientNodeId tidak didukung."
            )
            return GatewayEgressResult(
                success = false,
                status = MeshForwardProtocol.STATUS_UNSUPPORTED_PROTOCOL,
                detail = "GATEWAY_EGRESS_UNSUPPORTED"
            )
        }

        GatewayNatTable.upsert(
            context,
            GatewayNatTable.Entry(
                clientNodeId = clientNodeId,
                sessionId = sessionId,
                sourceAddress = parsed.sourceAddress,
                sourcePort = parsed.sourcePort,
                destinationAddress = parsed.destinationAddress,
                destinationPort = parsed.destinationPort,
                protocol = parsed.protocol,
                lastSeen = System.currentTimeMillis()
            )
        )

        VpnLogManager.info(
            "GATEWAY_EGRESS_RECEIVED",
            "client=$clientNodeId ipv=${parsed.ipVersion} protocol=${parsed.protocol} ${parsed.sourceAddress}:${parsed.sourcePort} -> ${parsed.destinationAddress}:${parsed.destinationPort}"
        )

        return when (parsed.protocol) {
            "TCP" -> {
                val existing =
                    GatewayNatTable.find(
                        context = context,
                        clientNodeId = clientNodeId,
                        sessionId = sessionId,
                        sourceAddress = parsed.sourceAddress,
                        sourcePort = parsed.sourcePort,
                        destinationAddress = parsed.destinationAddress,
                        destinationPort = parsed.destinationPort,
                        protocol = parsed.protocol
                    )
                val natEntry =
                    existing ?: GatewayNatTable.Entry(
                        clientNodeId = clientNodeId,
                        sessionId = sessionId,
                        sourceAddress = parsed.sourceAddress,
                        sourcePort = parsed.sourcePort,
                        destinationAddress = parsed.destinationAddress,
                        destinationPort = parsed.destinationPort,
                        protocol = parsed.protocol,
                        lastSeen = System.currentTimeMillis()
                    )

                if (parsed.payload.isEmpty()) {
                    VpnLogManager.warn(
                        "TCP_RAW_PACKET_NEEDS_TRANSLATION",
                        "TCP raw packet belum bisa diterjemahkan ke stream. flags=${parsed.tcpFlags} ${parsed.sourceAddress}:${parsed.sourcePort} -> ${parsed.destinationAddress}:${parsed.destinationPort}"
                    )
                    return GatewayEgressResult(
                        success = false,
                        status = MeshForwardProtocol.STATUS_TCP_RAW_PACKET_NEEDS_TRANSLATION,
                        detail = "TCP_RAW_PACKET_NEEDS_TRANSLATION"
                    )
                }

                GatewayTcpBridge.handle(
                    context = context,
                    request =
                        GatewayTcpBridge.ForwardRequest(
                            clientNodeId = clientNodeId,
                            sessionId = sessionId,
                            packetId = "pkt-${System.currentTimeMillis()}",
                            gatewayNodeId =
                                GlobalMeshIdentityManager.buildGlobalId(
                                    KeyStoreManager(context).publicKeyBase64
                                ),
                            sourceAddress = natEntry.sourceAddress,
                            sourcePort = natEntry.sourcePort,
                            destinationAddress = natEntry.destinationAddress,
                            destinationPort = natEntry.destinationPort,
                            tcpPayload = parsed.payload,
                            remoteHost = remoteHost
                        )
                )
            }
            "UDP" -> {
                VpnLogManager.warn(
                    "GATEWAY_EGRESS_UDP_PENDING",
                    "UDP bridge belum aktif untuk ${parsed.destinationAddress}:${parsed.destinationPort}"
                )
                GatewayEgressResult(
                    success = false,
                    status = MeshForwardProtocol.STATUS_EGRESS_FAILED,
                    detail = "GATEWAY_EGRESS_UDP_PENDING"
                )
            }
            "IPV6" -> {
                VpnLogManager.warn(
                    "GATEWAY_EGRESS_IPV6_PENDING",
                    "IPv6 bridge belum aktif untuk client=$clientNodeId"
                )
                GatewayEgressResult(
                    success = false,
                    status = MeshForwardProtocol.STATUS_EGRESS_FAILED,
                    detail = "GATEWAY_EGRESS_IPV6_PENDING"
                )
            }
            "ICMP" -> {
                VpnLogManager.warn(
                    "GATEWAY_EGRESS_ICMP_PENDING",
                    "ICMP bridge belum aktif untuk ${parsed.destinationAddress}"
                )
                GatewayEgressResult(
                    success = false,
                    status = MeshForwardProtocol.STATUS_EGRESS_FAILED,
                    detail = "GATEWAY_EGRESS_ICMP_PENDING"
                )
            }
            else -> {
                VpnLogManager.warn(
                    "GATEWAY_EGRESS_UNSUPPORTED",
                    "Protokol ${parsed.protocol} belum didukung."
                )
                GatewayEgressResult(
                    success = false,
                    status = MeshForwardProtocol.STATUS_UNSUPPORTED_PROTOCOL,
                    detail = "GATEWAY_EGRESS_UNSUPPORTED"
                )
            }
        }
    }

    fun decodePacketBase64(packetBase64: String): ByteArray {
        return runCatching {
            Base64.decode(packetBase64, Base64.NO_WRAP)
        }.getOrElse { ByteArray(0) }
    }

    private fun parsePacket(packetBytes: ByteArray): ParsedPacket? {
        if (packetBytes.isEmpty()) return null
        val version = (packetBytes[0].toInt() ushr 4) and 0x0F
        if (version == 6) {
            return ParsedPacket(
                ipVersion = 6,
                protocol = "IPV6",
                sourceAddress = "::",
                sourcePort = 0,
                destinationAddress = "::",
                destinationPort = 0,
                ipHeaderLength = 40,
                transportHeaderLength = 0,
                tcpFlags = 0,
                payload = if (packetBytes.size > 40) packetBytes.copyOfRange(40, packetBytes.size) else ByteArray(0)
            )
        }
        if (version != 4 || packetBytes.size < 20) return null

        val ihlBytes = (packetBytes[0].toInt() and 0x0F) * 4
        if (packetBytes.size < ihlBytes + 4) return null

        val protocolNumber = packetBytes[9].toInt() and 0xFF
        val protocol =
            when (protocolNumber) {
                6 -> "TCP"
                17 -> "UDP"
                1 -> "ICMP"
                else -> "UNSUPPORTED"
            }
        val sourceAddress =
            "${packetBytes[12].toInt() and 0xFF}.${packetBytes[13].toInt() and 0xFF}.${packetBytes[14].toInt() and 0xFF}.${packetBytes[15].toInt() and 0xFF}"
        val destinationAddress =
            "${packetBytes[16].toInt() and 0xFF}.${packetBytes[17].toInt() and 0xFF}.${packetBytes[18].toInt() and 0xFF}.${packetBytes[19].toInt() and 0xFF}"
        val hasPorts = (protocol == "TCP" || protocol == "UDP") && packetBytes.size >= ihlBytes + 4
        val sourcePort =
            if (hasPorts) {
                ((packetBytes[ihlBytes].toInt() and 0xFF) shl 8) or
                    (packetBytes[ihlBytes + 1].toInt() and 0xFF)
            } else {
                0
            }
        val destinationPort =
            if (hasPorts) {
                ((packetBytes[ihlBytes + 2].toInt() and 0xFF) shl 8) or
                    (packetBytes[ihlBytes + 3].toInt() and 0xFF)
            } else {
                0
            }
        val transportHeaderLength =
            if (protocol == "TCP" && packetBytes.size >= ihlBytes + 13) {
                ((packetBytes[ihlBytes + 12].toInt() ushr 4) and 0x0F) * 4
            } else if (protocol == "UDP") {
                8
            } else {
                0
            }
        val tcpFlags =
            if (protocol == "TCP" && packetBytes.size >= ihlBytes + 14) {
                packetBytes[ihlBytes + 13].toInt() and 0x3F
            } else {
                0
            }
        val payloadOffset = ihlBytes + transportHeaderLength
        val payload =
            if (payloadOffset in 0 until packetBytes.size) {
                packetBytes.copyOfRange(payloadOffset, packetBytes.size)
            } else {
                ByteArray(0)
            }

        return ParsedPacket(
            ipVersion = 4,
            protocol = protocol,
            sourceAddress = sourceAddress,
            sourcePort = sourcePort,
            destinationAddress = destinationAddress,
            destinationPort = destinationPort,
            ipHeaderLength = ihlBytes,
            transportHeaderLength = transportHeaderLength,
            tcpFlags = tcpFlags,
            payload = payload
        )
    }
}
