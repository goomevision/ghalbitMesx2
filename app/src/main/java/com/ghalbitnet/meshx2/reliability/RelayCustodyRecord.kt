package com.ghalbitnet.meshx2.reliability

data class RelayCustodyRecord(
    val messageId: String,
    val legacyChatId: String,
    val relayNodeId: String? = null,
    val relayPeerIp: String? = null,
    val relayGlobalId: String? = null,
    val custodyDepth: Int = 0,
    val custodyStartAt: Long? = null,
    val custodyExpireAt: Long? = null,
    val status: RelayCustodyStatus = RelayCustodyStatus.UNKNOWN
)
