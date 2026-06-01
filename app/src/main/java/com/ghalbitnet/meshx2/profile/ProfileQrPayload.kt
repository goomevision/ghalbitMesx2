package com.ghalbitnet.meshx2.profile

data class ProfileQrPayload(
    val globalId: String,
    val publicKey: String,
    val publicKeyHash: String,
    val displayName: String,
    val nickname: String,
    val roleTitle: String,
    val bio: String = "",
    val community: String = "GhalbitNet Community",
    val region: String = "Wilayah belum diisi",
    val tier: String = "BASIC",
    val trustScore: Int = 0,
    val trustRank: String = "Baru",
    val badges: List<String> = emptyList(),
    val mentorStatus: String = "Belum Menjadi Mentor",
    val referralLabel: String = "0/0",
    val communityReputation: Int = 0,
    val profileVersion: Int,
    val relayHint: String?,
    val timestamp: Long = 0L,
    val signature: String
)
