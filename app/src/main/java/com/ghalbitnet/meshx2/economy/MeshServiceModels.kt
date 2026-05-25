package com.ghalbitnet.meshx2.economy

enum class ServiceFamily {
    INTERNET,
    CHAT,
    MEDIA,
    CALL,
    SOS,
    CONTROL,
    OTHER
}

enum class ServiceUsageMode {
    APP_BONUS,
    INTERNET_BRIDGE
}

enum class ServiceRole {
    USER,
    GATEWAY,
    RELAY,
    TREASURY
}

data class ServiceParticipant(
    val nodeId: String,
    val nodeName: String,
    val nodeAddress: String = "",
    val role: ServiceRole,
    val local: Boolean = false,
    val trustScore: Int = 50
)

data class ServiceRouteSegment(
    val gatewayNodeId: String,
    val gatewayNodeName: String,
    val gatewayNodeAddress: String,
    val localGateway: Boolean,
    val routeMode: String,
    val routeScore: Int,
    val relayPath: List<ServiceParticipant>,
    val startedAt: Long,
    val endedAt: Long
) {
    val durationMs: Long
        get() = (endedAt - startedAt).coerceAtLeast(1L)
}

data class ServiceSessionRecord(
    val sessionId: String,
    val serviceFamily: ServiceFamily,
    val usageMode: ServiceUsageMode,
    val userGlobalId: String,
    val bytesUp: Long,
    val bytesDown: Long,
    val durationMs: Long,
    val startedAt: Long,
    val endedAt: Long,
    val success: Boolean,
    val averageLatencyMs: Int,
    val localInternetProvider: Boolean,
    val gatewayNodeId: String,
    val gatewayNodeName: String,
    val gatewayNodeAddress: String,
    val stopReason: String,
    val relayPath: List<ServiceParticipant>,
    val routeSegments: List<ServiceRouteSegment> = emptyList()
) {
    val totalBytes: Long
        get() = bytesUp + bytesDown

    val totalMegaBytes: Double
        get() = totalBytes / 1024.0 / 1024.0
}

data class ParticipantReward(
    val nodeId: String,
    val nodeName: String,
    val nodeAddress: String,
    val local: Boolean,
    val amount: Double
)

data class ServiceProofScore(
    val gatewayProof: Double,
    val relayProof: Double,
    val validatorProof: Double,
    val meshLocalProof: Double,
    val overallProof: Double
)

data class ServiceSettlement(
    val sessionId: String,
    val validMegaBytes: Double,
    val familyMultiplier: Double,
    val pricingLabel: String,
    val userCharged: Boolean,
    val burnAmount: Double,
    val gatewayReward: Double,
    val gatewayRewards: List<ParticipantReward>,
    val relayRewards: List<ParticipantReward>,
    val builderReward: Double,
    val validatorReward: Double,
    val treasuryReserve: Double,
    val validationScore: Double,
    val proofScore: ServiceProofScore,
    val notes: String
) {
    val totalRelayReward: Double
        get() = relayRewards.sumOf { it.amount }

    val mintedContribution: Double
        get() = gatewayReward + totalRelayReward
}

data class ServiceLedgerEntry(
    val session: ServiceSessionRecord,
    val settlement: ServiceSettlement
)

data class ServiceEconomySnapshot(
    val sessionCount: Int,
    val totalBytes: Long,
    val totalBurned: Double,
    val totalGatewayReward: Double,
    val totalRelayReward: Double,
    val totalBuilderReward: Double,
    val totalValidatorReward: Double,
    val totalTreasury: Double,
    val lastUpdatedAt: Long,
    val latestSummary: String
)

data class PeerServiceSnapshot(
    val globalId: String,
    val sessionCount: Int,
    val totalBytes: Long,
    val totalBurned: Double,
    val totalGatewayReward: Double,
    val totalRelayReward: Double,
    val totalBuilderReward: Double,
    val totalValidatorReward: Double,
    val totalTreasury: Double,
    val lastUpdatedAt: Long
) {
    val totalMegaBytes: Double
        get() = totalBytes / 1024.0 / 1024.0
}
