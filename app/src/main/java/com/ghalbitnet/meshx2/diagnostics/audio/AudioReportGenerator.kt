package com.ghalbitnet.meshx2.diagnostics.audio

object AudioReportGenerator {
    fun generateMarkdown(report: AudioTruthReport): String {
        return buildString {
            appendLine("# Audio Truth Lab Report")
            appendLine()
            appendLine("- healthScore: ${report.healthScore}/100")
            appendLine("- micFrames: ${report.micFrames}")
            appendLine("- rms: ${"%.2f".format(report.rms)}")
            appendLine("- peak: ${report.peak}")
            appendLine("- noiseFloor: ${"%.2f".format(report.noiseFloor)}")
            appendLine("- speechDetected: ${report.speechDetected}")
            appendLine("- clippingDetected: ${report.clippingDetected}")
            appendLine("- tone440Played: ${report.tone440Played}")
            appendLine("- tone1000Played: ${report.tone1000Played}")
            appendLine("- outputUnderrun: ${report.outputUnderrun}")
            appendLine("- outputStall: ${report.outputStall}")
            appendLine("- loopbackOk: ${report.loopbackOk}")
            appendLine("- loopbackLatencyMs: ${report.loopbackLatencyMs}")
            appendLine("- notes: ${report.notes.joinToString(" | ")}")
        }
    }
}

