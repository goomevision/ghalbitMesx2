package com.ghalbitnet.meshx2.diagnostics.autodiag

object AutoDiagnosticReportGenerator {
    fun toMarkdown(result: AutoDiagnosticResult): String {
        return buildString {
            appendLine("# Auto Diagnostic Center Report")
            appendLine()
            appendLine("- status: ${result.status}")
            appendLine("- totalScore: ${result.totalScore}/100")
            appendLine()
            appendLine("## Steps")
            result.steps.forEach { step ->
                appendLine("- ${step.name}: ${step.status} (${step.score}/100)")
                if (step.notes.isNotEmpty()) {
                    appendLine("  - notes: ${step.notes.joinToString(" | ")}")
                }
            }
        }
    }

    fun toHumanSummary(result: AutoDiagnosticResult): String {
        val rows = result.steps.joinToString("\n") {
            "${it.name}: ${it.status} (${it.score})"
        }
        return "Status: ${result.status}\nScore: ${result.totalScore}/100\n\n$rows"
    }
}

