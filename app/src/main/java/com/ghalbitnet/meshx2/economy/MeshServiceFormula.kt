package com.ghalbitnet.meshx2.economy

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object MeshServiceFormula {

    private const val USER_BURN_PER_MB = 0.08
    private const val GATEWAY_REWARD_PER_MB = 0.045
    private const val RELAY_POOL_PER_MB = 0.022
    private const val TREASURY_PER_MB = 0.006
    private const val BUILDER_SHARE_RATE = 0.10

    fun settle(
        session: ServiceSessionRecord
    ): ServiceSettlement {
        val validMegaBytes =
            max(0.25, session.totalMegaBytes)

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

        val burnAmount =
            roundToTwoDecimals(validMegaBytes * USER_BURN_PER_MB * successFactor)

        val gatewayReward =
            roundToTwoDecimals(validMegaBytes * GATEWAY_REWARD_PER_MB * validationScore)

        val relayPool =
            roundToTwoDecimals(validMegaBytes * RELAY_POOL_PER_MB * validationScore)

        val relayRewards =
            splitRelayPool(session.relayPath, relayPool)

        val treasuryReserve =
            roundToTwoDecimals(validMegaBytes * TREASURY_PER_MB)

        val builderReward =
            roundToTwoDecimals(
                (gatewayReward + relayPool + treasuryReserve) * BUILDER_SHARE_RATE
            )

        val notes =
            buildString {
                append("Sesi ")
                append(roundToTwoDecimals(validMegaBytes))
                append(" MB. ")

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

                append("Hadiah pembangun ")
                append(builderReward)
                append(" GHBT. ")
                append("Skor validasi ${roundToTwoDecimals(validationScore)}.")
            }

        return ServiceSettlement(
            sessionId = session.sessionId,
            validMegaBytes = roundToTwoDecimals(validMegaBytes),
            burnAmount = burnAmount,
            gatewayReward = gatewayReward,
            relayRewards = relayRewards,
            builderReward = builderReward,
            treasuryReserve = treasuryReserve,
            validationScore = roundToTwoDecimals(validationScore),
            notes = notes
        )
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

    private fun roundToTwoDecimals(
        value: Double
    ): Double {
        return round(value * 100.0) / 100.0
    }
}
