package com.ghalbitnet.meshx2.economy

enum class ServiceFamily {
    CHAT,
    MEDIA,
    CALL,
    SOS,
    CONTROL,
    OTHER
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

data class ServiceSessionRecord(
    val sessionId: String,
    val serviceFamily: ServiceFamily,
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
    val relayPath: List<ServiceParticipant>
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

data class ServiceSettlement(
    val sessionId: String,
    val validMegaBytes: Double,
    val burnAmount: Double,
    val gatewayReward: Double,
    val relayRewards: List<ParticipantReward>,
    val builderReward: Double,
    val treasuryReserve: Double,
    val validationScore: Double,
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
    val totalTreasury: Double,
    val lastUpdatedAt: Long,
    val latestSummary: String
)
