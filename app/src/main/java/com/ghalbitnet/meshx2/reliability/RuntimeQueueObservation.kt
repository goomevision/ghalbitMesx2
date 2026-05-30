package com.ghalbitnet.meshx2.reliability

object RuntimeQueueObservation {

    fun report(): String =
        buildString {
            appendLine("RUNTIME QUEUE")
            appendLine("======================")
            appendLine("Mode: passive observation only")
            appendLine("Queue processing: disabled")
            appendLine("Automatic retry: disabled")
            appendLine("Observed categories: outbound pending, inbound recovery, delayed sync, relay pending, retry waiting, expired queue, custody backlog")
        }.trimEnd()
}
