package com.ghalbitnet.meshx2.verified

/**
 * PHASE 251H
 * Converts verified card payloads into QR verification payloads.
 */
object VerifiedQrBridge {

    fun build(payload: VerifiedNameCardPayload): QrVerificationPayload {
        return QrVerificationPayload(
            type = VerifiedDocumentType.NAME_CARD.wireName,
            documentId = payload.globalId,
            hash = payload.cardHash ?: payload.profileHash,
            signature = payload.signature ?: "pending",
            centralUrl = payload.centralVerifyUrl,
            localUrl = payload.localVerifyUrl,
            issuerNode = payload.issuerNodeId
        )
    }
}
