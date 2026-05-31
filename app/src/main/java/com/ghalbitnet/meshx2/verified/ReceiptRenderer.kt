package com.ghalbitnet.meshx2.verified

object ReceiptRenderer {
 fun render(r:ReceiptPayload)=buildString{
 appendLine("GHALBIT VERIFIED RECEIPT")
 appendLine("TX: ${r.transactionId}")
 appendLine("FROM: ${r.senderId}")
 appendLine("TO: ${r.receiverId}")
 appendLine("AMOUNT: ${r.amount}")
 }
}