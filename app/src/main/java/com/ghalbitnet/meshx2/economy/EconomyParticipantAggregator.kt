package com.ghalbitnet.meshx2.economy

object EconomyParticipantAggregator {

    fun summarize(
        participants: List<EconomyParticipantIdentity>
    ): EconomyParticipantSummary {
        if (participants.isEmpty()) {
            return EconomyParticipantSummary(
                totalParticipants = 0,
                canonicalReadyCount = 0,
                walletBasedCount = 0,
                publicKeyBasedCount = 0,
                nodeOrIpLegacyCount = 0,
                unknownCount = 0,
                averageConfidence = 0,
                lowestConfidence = 0,
                highestConfidence = 0
            )
        }

        val canonicalReadyCount =
            participants.count {
                it.source == EconomyIdentitySource.LOCAL_GLOBAL_ID ||
                    (!it.participantGlobalId.isNullOrBlank() && it.confidence >= 80)
            }
        val walletBasedCount =
            participants.count { it.source == EconomyIdentitySource.WALLET_ONLY }
        val publicKeyBasedCount =
            participants.count { it.source == EconomyIdentitySource.PUBLIC_KEY_BRIDGE }
        val nodeOrIpLegacyCount =
            participants.count {
                it.source == EconomyIdentitySource.NODE_ID ||
                    it.source == EconomyIdentitySource.PEER_NAME ||
                    it.source == EconomyIdentitySource.PEER_IP ||
                    it.source == EconomyIdentitySource.LEDGER_LEGACY
            }
        val unknownCount =
            participants.count { it.source == EconomyIdentitySource.UNKNOWN }
        val averageConfidence =
            participants.map { it.confidence }.average().toInt()
        val lowestConfidence =
            participants.minOf { it.confidence }
        val highestConfidence =
            participants.maxOf { it.confidence }

        return EconomyParticipantSummary(
            totalParticipants = participants.size,
            canonicalReadyCount = canonicalReadyCount,
            walletBasedCount = walletBasedCount,
            publicKeyBasedCount = publicKeyBasedCount,
            nodeOrIpLegacyCount = nodeOrIpLegacyCount,
            unknownCount = unknownCount,
            averageConfidence = averageConfidence,
            lowestConfidence = lowestConfidence,
            highestConfidence = highestConfidence
        )
    }
}
