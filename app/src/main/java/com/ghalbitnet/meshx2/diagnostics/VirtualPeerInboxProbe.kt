package com.ghalbitnet.meshx2.diagnostics

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VirtualPeerInboxProbeResult(
    val globalId: String,
    val messages: Int,
    val receipts: Int,
    val callSignals: Int,
    val summary: String
)

object VirtualPeerInboxProbe {
    suspend fun run(
        context: Context,
        targetGlobalId: String = "GX-VIRTUAL-HP-B"
    ): VirtualPeerInboxProbeResult = withContext(Dispatchers.IO) {
        val inbox = OnlineFallbackTransport.fetchInbox(context.applicationContext, targetGlobalId)
        val callSignals = inbox.messages.count { it.contentType.uppercase().startsWith("CALL_") }
        val summary = buildString {
            append("messages=${inbox.messages.size}")
            append(" receipts=${inbox.receipts.size}")
            append(" callSignals=$callSignals")
            inbox.messages.take(3).forEach {
                append(" msg[")
                append(it.packetId)
                append(":")
                append(it.contentType)
                append("]")
            }
            inbox.receipts.take(3).forEach {
                append(" receipt[")
                append(it.messageId)
                append(":")
                append(it.type)
                append("]")
            }
        }
        Log.i("GHALBIT-VIRTUAL-PEER", "INBOX_CHECK globalId=$targetGlobalId $summary")
        VirtualPeerInboxProbeResult(
            globalId = targetGlobalId,
            messages = inbox.messages.size,
            receipts = inbox.receipts.size,
            callSignals = callSignals,
            summary = summary
        )
    }
}
