package com.ghalbitnet.meshx2.reliability

object DelayedSyncDiagnostics {

    fun report(): String =
        buildString {
            appendLine("DELAYED SYNC")
            appendLine("======================")
            appendLine("Mode: passive")
            appendLine("State baseline: idle/deferred/reconnecting/partiallyRecovered/expired")
            appendLine("Background sync: disabled")
        }.trimEnd()
}
