package com.ghalbitnet.meshx2.diagnostics.evidence

data class RuntimeEvidenceEvent(
    val ts: Long = System.currentTimeMillis(),
    val event: String,
    val source: String,
    val messageId: String? = null,
    val callId: String? = null,
    val peerId: String? = null,
    val status: String? = null,
    val details: String? = null
)

