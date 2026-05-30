package com.ghalbitnet.meshx2.profile

data class ProfileQrPayload(
    val globalId: String,
    val publicKey: String,
    val publicKeyHash: String,
    val displayName: String,
    val nickname: String,
    val roleTitle: String,
    val profileVersion: Int,
    val relayHint: String?,
    val signature: String
)
