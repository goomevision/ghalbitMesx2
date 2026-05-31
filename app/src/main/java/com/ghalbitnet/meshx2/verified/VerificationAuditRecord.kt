package com.ghalbitnet.meshx2.verified

data class VerificationAuditRecord(
    val documentId: String,
    val verifiedAt: Long,
    val verificationMethod: String,
    val result: String
)
