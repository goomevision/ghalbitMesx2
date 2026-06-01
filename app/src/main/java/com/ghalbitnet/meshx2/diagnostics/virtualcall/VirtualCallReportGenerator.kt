package com.ghalbitnet.meshx2.diagnostics.virtualcall

object VirtualCallReportGenerator {
    fun toMarkdown(result: VirtualCallResult): String =
        buildString {
            appendLine("# Virtual Caller One Device Report")
            appendLine()
            appendLine("- callId: `${result.callId}`")
            appendLine("- incomingShown: `${result.incomingShown}`")
            appendLine("- accepted: `${result.accepted}`")
            appendLine("- connected: `${result.connected}`")
            appendLine("- ringtoneStopped: `${result.ringtoneStopped}`")
            appendLine("- ended: `${result.ended}`")
            appendLine("- audioRms: `${"%.2f".format(result.audioRms)}`")
            appendLine("- audioPeak: `${result.audioPeak}`")
            appendLine("- speechDetected: `${result.speechDetected}`")
            appendLine("- status: `${result.status}`")
            if (result.notes.isNotEmpty()) {
                appendLine()
                appendLine("## Notes")
                result.notes.forEach { appendLine("- $it") }
            }
        }
}

