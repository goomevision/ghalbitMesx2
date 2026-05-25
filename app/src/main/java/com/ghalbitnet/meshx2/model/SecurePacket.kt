package com.ghalbitnet.meshx2.model
data class SecurePacket(
    val sourcePublicKey: String, val destinationPublicKey: String,
    val encryptedPayload: String, val packetId: String,
    val hopCount: Int = 0, val maxHop: Int = 5,
    val timestamp: Long = System.currentTimeMillis()
)