package com.ghalbitnet.meshx2.economy

import android.content.Context
import com.ghalbitnet.meshx2.token.TokenManager

object MeshEconomySettlementEngine {

    suspend fun applySettlement(
        context: Context,
        ownerGlobalId: String,
        session: ServiceSessionRecord,
        settlement: ServiceSettlement
    ) {
        TokenManager.ensureWalletBootstrap(ownerGlobalId)
        TokenManager.ensureWalletBootstrap(session.userGlobalId)

        if (settlement.burnAmount > 0.0) {
            TokenManager.recordWalletDebit(
                session.userGlobalId,
                settlement.burnAmount,
                "USAGE_BURN:${session.sessionId}"
            )
        }

        val gatewayRewards =
            if (settlement.gatewayRewards.isNotEmpty()) {
                settlement.gatewayRewards
            } else {
                listOf(
                    ParticipantReward(
                        nodeId = session.gatewayNodeId,
                        nodeName = session.gatewayNodeName,
                        nodeAddress = session.gatewayNodeAddress,
                        local = session.localInternetProvider,
                        amount = settlement.gatewayReward
                    )
                )
            }

        gatewayRewards.forEach { gateway ->
            if (gateway.amount <= 0.0) {
                return@forEach
            }
            if (gateway.local) {
                TokenManager.recordWalletCredit(
                    ownerGlobalId,
                    gateway.amount,
                    "GATEWAY_REWARD:${session.sessionId}:${gateway.nodeName}"
                )
            } else {
                TokenManager.recordPeerReward(
                    peerIp = gateway.nodeAddress.ifBlank { gateway.nodeId },
                    peerName = gateway.nodeName.ifBlank { gateway.nodeId },
                    amount = gateway.amount,
                    reason = "GATEWAY_REWARD:${session.sessionId}:${gateway.nodeName}"
                )
            }
        }

        settlement.relayRewards.forEach { relay ->
            if (relay.local) {
                TokenManager.recordWalletCredit(
                    ownerGlobalId,
                    relay.amount,
                    "RELAY_REWARD:${session.sessionId}:${relay.nodeName}"
                )
            } else {
                TokenManager.recordPeerReward(
                    peerIp = relay.nodeAddress.ifBlank { relay.nodeId },
                    peerName = relay.nodeName,
                    amount = relay.amount,
                    reason = "RELAY_REWARD:${session.sessionId}"
                )
            }
        }

        TokenManager.recordTreasury(
            amount = settlement.treasuryReserve,
            reason = "TREASURY:${session.sessionId}"
        )

        TokenManager.recordBuilderReward(
            amount = settlement.builderReward,
            reason = "BUILDER_REWARD:${session.sessionId}"
        )

        TokenManager.recordValidatorReward(
            amount = settlement.validatorReward,
            reason = "VALIDATOR_REWARD:${session.sessionId}"
        )
    }
}
