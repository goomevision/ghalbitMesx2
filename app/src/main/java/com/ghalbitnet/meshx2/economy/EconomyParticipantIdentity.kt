package com.ghalbitnet.meshx2.economy

enum class EconomyIdentitySource {
    LOCAL_GLOBAL_ID,
    PUBLIC_KEY_BRIDGE,
    WALLET_ONLY,
    NODE_ID,
    PEER_NAME,
    PEER_IP,
    LEDGER_LEGACY,
    UNKNOWN
}

data class EconomyParticipantIdentity(
    val participantGlobalId: String? = null,
    val participantPublicKey: String? = null,
    val walletAddress: String? = null,
    val legacyNodeId: String? = null,
    val legacyPeerName: String? = null,
    val legacyPeerIp: String? = null,
    val source: EconomyIdentitySource = EconomyIdentitySource.UNKNOWN,
    val confidence: Int = 0,
    val lastSeenAt: Long = System.currentTimeMillis()
)
