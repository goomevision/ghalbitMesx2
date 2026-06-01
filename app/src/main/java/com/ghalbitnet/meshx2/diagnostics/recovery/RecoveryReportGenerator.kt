package com.ghalbitnet.meshx2.diagnostics.recovery

object RecoveryReportGenerator {
    fun toMarkdown(result: RecoveryRunResult): String {
        return buildString {
            appendLine("# Smart Recovery Report")
            appendLine()
            appendLine("- recovered: ${result.recovered}")
            appendLine("- pending: ${result.pending}")
            appendLine("- failed: ${result.failed}")
            appendLine()
            appendLine("## Issues")
            result.issues.forEach {
                appendLine("- ${it.type} [${it.severity}] source=${it.source} detail=${it.detail}")
            }
            appendLine()
            appendLine("## Actions")
            result.actions.forEach {
                appendLine("- ${it.name}: applied=${it.applied} result=${it.result} humanApproval=${it.requiresHumanApproval}")
            }
            appendLine()
            appendLine("## Auto Fix Suggestions")
            result.suggestions.forEach {
                appendLine("- error=${it.error} file=${it.file} patch=${it.safePatch} risk=${it.risk} humanApproval=${it.humanApprovalRequired}")
            }
        }
    }
}

