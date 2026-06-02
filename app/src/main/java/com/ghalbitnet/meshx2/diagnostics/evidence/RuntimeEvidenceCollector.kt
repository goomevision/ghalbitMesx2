package com.ghalbitnet.meshx2.diagnostics.evidence

import android.content.Context
import android.util.Log

object RuntimeEvidenceCollector {
    fun record(
        context: Context,
        event: String,
        source: String,
        messageId: String? = null,
        callId: String? = null,
        peerId: String? = null,
        status: String? = null,
        details: String? = null
    ) {
        val payload = RuntimeEvidenceEvent(
            event = event,
            source = source,
            messageId = messageId,
            callId = callId,
            peerId = peerId,
            status = status,
            details = details
        )
        runCatching { RuntimeEvidenceStore.append(context, payload) }
        Log.d(
            "GHALBIT-EVIDENCE",
            "event=$event callId=${callId ?: "-"} peerId=${peerId ?: "-"} status=${status ?: "-"}"
        )
    }

    fun clear(context: Context): Boolean = RuntimeEvidenceStore.clear(context)

    fun snapshot(context: Context): List<RuntimeEvidenceEvent> = RuntimeEvidenceStore.readAll(context)
}

