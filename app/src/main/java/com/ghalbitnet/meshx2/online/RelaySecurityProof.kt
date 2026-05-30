package com.ghalbitnet.meshx2.online

import java.security.MessageDigest

object RelaySecurityProof {
    data class Payload(
        val algorithm: String = "Ed25519",
        val senderGlobalId: String,
        val targetGlobalId: String,
        val messageId: String,
        val packetId: String,
        val createdAt: Long,
        val expiresAt: Long,
        val nonce: String,
        val contentType: String,
        val payload: String,
        val senderPublicKey: String
    )

    fun nonce(): String = "N-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(12)}"

    fun canonical(payload: Payload): String {
        return listOf(
            payload.algorithm,
            payload.senderGlobalId,
            payload.targetGlobalId,
            payload.messageId,
            payload.packetId,
            payload.createdAt.toString(),
            payload.expiresAt.toString(),
            payload.nonce,
            payload.contentType,
            payload.payload
        ).joinToString(separator = "|")
    }

    fun sha256(text: String): String {
        return MessageDigest
            .getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString(separator = "") { "%02x".format(it) }
    }
}
