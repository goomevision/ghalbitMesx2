package com.ghalbitnet.meshx2.verified

data class QrVerificationPayload(
    val type: String,
    val documentId: String,
    val hash: String,
    val signature: String,
    val centralUrl: String? = null,
    val localUrl: String? = null,
    val issuerNode: String? = null
)
