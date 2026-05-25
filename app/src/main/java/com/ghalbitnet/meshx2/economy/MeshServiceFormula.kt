package com.ghalbitnet.meshx2.economy

import android.content.Context

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object MeshServiceFormula {

    private data class FamilyRate(
        val multiplier: Double,
        val label: String
    )

    fun settle(
        context: Context,
        session: ServiceSessionRecord
    ): ServiceSettlement {
        val policy =
            MeshEconomyServerPolicyManager.current(context)

        val rateTable =
            policy.rateTableFor(session.usageMode)

        val validMegaBytes =
            max(0.25, session.totalMegaBytes)

        val familyRate =
            familyRate(session.serviceFamily, rateTable)

        val latencyFactor =
            when {
                session.averageLatencyMs <= 80 -> 1.08
                session.averageLatencyMs <= 150 -> 1.00
                session.averageLatencyMs <= 300 -> 0.92
                else -> 0.80
            }

        val averageTrust =
            if (session.relayPath.isEmpty()) {
                70.0
            } else {
                session.relayPath.map { it.trustScore.coerceIn(10, 100) }.average()
            }

        val trustFactor =
            (averageTrust / 100.0).coerceIn(0.55, 1.0)

        val successFactor =
            if (session.success) 1.0 else 0.20

        val validationScore =
            (latencyFactor * trustFactor * successFactor)
                .coerceIn(0.25, 1.10)

        val gatewayProof =
            when {
                !session.success -> 0.20
                session.localInternetProvider -> 0.98
                session.gatewayNodeId.isNotBlank() || session.gatewayNodeName.isNotBlank() -> 0.86
                else -> 0.40
            } * latencyFactor.coerceIn(0.75, 1.05)

        val relayProof =
            if (session.relayPath.isEmpty()) {
                if (session.success) 0.62 else 0.20
            } else {
                val relayTrust =
                    session.relayPath.map { it.trustScore.coerceIn(10, 100) / 100.0 }.average()
                (0.45 + (relayTrust * 0.55)) * successFactor
            }.coerceIn(0.0, 1.0)

        val validatorProof =
            ((validationScore + trustFactor) / 2.0)
                .coerceIn(0.20, 1.0)

        val meshLocalProof =
            when {
                session.usageMode == ServiceUsageMode.APP_BONUS && session.relayPath.isNotEmpty() ->
                    (0.55 + (trustFactor * 0.35) + if (session.success) 0.10 else 0.0)
                session.usageMode == ServiceUsageMode.APP_BONUS ->
                    0.52 + if (session.success) 0.12 else 0.0
                else -> 0.18 + (relayProof * 0.22)
            }.coerceIn(0.0, 1.0)

        val proofScore =
            ServiceProofScore(
                gatewayProof = roundToTwoDecimals(gatewayProof),
                relayProof = roundToTwoDecimals(relayProof),
                validatorProof = roundToTwoDecimals(validatorProof),
                meshLocalProof = roundToTwoDecimals(meshLocalProof),
                overallProof = roundToTwoDecimals(
                    (
                        gatewayProof * 0.40 +
                            relayProof * 0.25 +
                            validatorProof * 0.20 +
                            meshLocalProof * 0.15
                        ).coerceIn(0.0, 1.0)
                )
            )

        val userCharged =
            session.usageMode == ServiceUsageMode.INTERNET_BRIDGE

        val burnAmount =
            if (userCharged) {
                roundToTwoDecimals(
                    validMegaBytes *
                        rateTable.burnPerMb *
                        successFactor *
                        familyRate.multiplier
                )
            } else {
                0.0
            }

        val gatewayReward =
            roundToTwoDecimals(
                validMegaBytes *
                    rateTable.gatewayPerMb *
                    validationScore *
                    familyRate.multiplier
            )

        val gatewayRewards =
            splitGatewayReward(session, gatewayReward)

        val relayPool =
            roundToTwoDecimals(
                validMegaBytes *
                    rateTable.relayPerMb *
                    validationScore *
                    familyRate.multiplier
            )

        val relayRewards =
            splitRelayPoolAcrossSegments(session, relayPool)

        val treasuryReserve =
            roundToTwoDecimals(
                validMegaBytes *
                    rateTable.treasuryPerMb *
                    max(0.6, familyRate.multiplier)
            )

        val builderReward =
            roundToTwoDecimals(
                validMegaBytes *
                    rateTable.builderPerMb *
                    validationScore *
                    familyRate.multiplier
            )

        val validatorReward =
            roundToTwoDecimals(
                validMegaBytes *
                    rateTable.validatorPerMb *
                    validationScore *
                    familyRate.multiplier
            )

        val notes =
            buildString {
                append("Sesi ")
                append(roundToTwoDecimals(validMegaBytes))
                append(" MB. ")
                append(policy.pricingLabelFor(session.usageMode))
                append(". ")
                append("Family ")
                append(familyRate.label)
                append(" x")
                append(roundToTwoDecimals(familyRate.multiplier))
                append(". ")

                if (session.localInternetProvider) {
                    append("Gateway lokal aktif. ")
                } else if (session.gatewayNodeName.isNotBlank()) {
                    append("Gateway melalui ${session.gatewayNodeName}. ")
                } else {
                    append("Gateway belum stabil. ")
                }

                if (session.relayPath.isNotEmpty()) {
                    append("${session.relayPath.size} relay ikut dihitung. ")
                }

                if (session.routeSegments.size > 1) {
                    append("${session.routeSegments.size} segmen jalur dibagi proporsional. ")
                }

                if (!userCharged) {
                    append("Pengguna tidak dibakar saldo karena ini bonus layanan di dalam app. ")
                }

                append("Hadiah pembangun ")
                append(builderReward)
                append(" GHBT. ")
                append("Validator ")
                append(validatorReward)
                append(" GHBT. ")
                append("Skor validasi ${roundToTwoDecimals(validationScore)}. ")
                append("Proof G ${proofScore.gatewayProof} | R ${proofScore.relayProof} | V ${proofScore.validatorProof} | M ${proofScore.meshLocalProof}.")
            }

        return ServiceSettlement(
            sessionId = session.sessionId,
            validMegaBytes = roundToTwoDecimals(validMegaBytes),
            familyMultiplier = familyRate.multiplier,
            pricingLabel = policy.pricingLabelFor(session.usageMode),
            userCharged = userCharged,
            burnAmount = burnAmount,
            gatewayReward = gatewayReward,
            gatewayRewards = gatewayRewards,
            relayRewards = relayRewards,
            builderReward = builderReward,
            validatorReward = validatorReward,
            treasuryReserve = treasuryReserve,
            validationScore = roundToTwoDecimals(validationScore),
            proofScore = proofScore,
            notes = notes
        )
    }

    private fun splitGatewayReward(
        session: ServiceSessionRecord,
        gatewayReward: Double
    ): List<ParticipantReward> {
        if (gatewayReward <= 0.0) {
            return emptyList()
        }
        val segments =
            if (session.routeSegments.isNotEmpty()) {
                session.routeSegments
            } else {
                listOf(
                    ServiceRouteSegment(
                        gatewayNodeId = session.gatewayNodeId,
                        gatewayNodeName = session.gatewayNodeName,
                        gatewayNodeAddress = session.gatewayNodeAddress,
                        localGateway = session.localInternetProvider,
                        routeMode = session.usageMode.name,
                        routeScore = 0,
                        relayPath = session.relayPath,
                        startedAt = session.startedAt,
                        endedAt = session.endedAt
                    )
                )
            }
        val totalDuration =
            segments.sumOf { it.durationMs.toDouble() }.coerceAtLeast(1.0)
        val aggregated =
            linkedMapOf<String, ParticipantReward>()

        segments.forEachIndexed { index, segment ->
            val rawAmount =
                if (index == segments.lastIndex) {
                    gatewayReward - aggregated.values.sumOf { it.amount }
                } else {
                    gatewayReward * (segment.durationMs / totalDuration)
                }
            val key = "${segment.gatewayNodeId}|${segment.gatewayNodeAddress}"
            val current = aggregated[key]
            val nextAmount = roundToTwoDecimals((current?.amount ?: 0.0) + rawAmount)
            aggregated[key] =
                ParticipantReward(
                    nodeId = segment.gatewayNodeId,
                    nodeName = segment.gatewayNodeName,
                    nodeAddress = segment.gatewayNodeAddress,
                    local = segment.localGateway,
                    amount = nextAmount
                )
        }

        return aggregated.values.toList()
    }

    private fun splitRelayPool(
        relays: List<ServiceParticipant>,
        relayPool: Double
    ): List<ParticipantReward> {
        if (relays.isEmpty() || relayPool <= 0.0) {
            return emptyList()
        }

        val weightedRelays =
            relays.mapIndexed { index, relay ->
                val hopWeight = max(0.55, 1.0 - (index * 0.12))
                val trustWeight = relay.trustScore.coerceIn(10, 100) / 100.0
                relay to (hopWeight * trustWeight)
            }

        val totalWeight =
            weightedRelays.sumOf { it.second }

        return weightedRelays.mapIndexed { index, (relay, weight) ->
            val rawAmount =
                if (index == weightedRelays.lastIndex) {
                    relayPool - weightedRelays
                        .take(index)
                        .sumOf { previous ->
                            roundToTwoDecimals(relayPool * (previous.second / totalWeight))
                        }
                } else {
                    relayPool * (weight / totalWeight)
                }

            ParticipantReward(
                nodeId = relay.nodeId,
                nodeName = relay.nodeName,
                nodeAddress = relay.nodeAddress,
                local = relay.local,
                amount = roundToTwoDecimals(rawAmount)
            )
        }
    }

    private fun splitRelayPoolAcrossSegments(
        session: ServiceSessionRecord,
        relayPool: Double
    ): List<ParticipantReward> {
        if (relayPool <= 0.0) {
            return emptyList()
        }
        val segments =
            session.routeSegments.filter { it.relayPath.isNotEmpty() }
        if (segments.isEmpty()) {
            return splitRelayPool(session.relayPath, relayPool)
        }

        val totalDuration =
            segments.sumOf { it.durationMs.toDouble() }.coerceAtLeast(1.0)
        val aggregated =
            linkedMapOf<String, ParticipantReward>()

        segments.forEachIndexed { index, segment ->
            val segmentPool =
                if (index == segments.lastIndex) {
                    relayPool - aggregated.values.sumOf { it.amount }
                } else {
                    relayPool * (segment.durationMs / totalDuration)
                }

            splitRelayPool(segment.relayPath, roundToTwoDecimals(segmentPool)).forEach { reward ->
                val key = "${reward.nodeId}|${reward.nodeAddress}"
                val current = aggregated[key]
                val nextAmount = roundToTwoDecimals((current?.amount ?: 0.0) + reward.amount)
                aggregated[key] =
                    reward.copy(amount = nextAmount)
            }
        }

        return aggregated.values.toList()
    }

    private fun roundToTwoDecimals(
        value: Double
    ): Double {
        return round(value * 100.0) / 100.0
    }

    private fun familyRate(
        family: ServiceFamily,
        table: MeshEconomyRateTableManager.RateTable
    ): FamilyRate {
        return when (family) {
            ServiceFamily.INTERNET -> FamilyRate(table.otherMultiplier, "INTERNET")
            ServiceFamily.SOS -> FamilyRate(table.sosMultiplier, "SOS")
            ServiceFamily.CALL -> FamilyRate(table.callMultiplier, "CALL")
            ServiceFamily.MEDIA -> FamilyRate(table.mediaMultiplier, "MEDIA")
            ServiceFamily.CHAT -> FamilyRate(table.chatMultiplier, "CHAT")
            ServiceFamily.CONTROL -> FamilyRate(table.controlMultiplier, "CONTROL")
            ServiceFamily.OTHER -> FamilyRate(table.otherMultiplier, "OTHER")
        }
    }
}
