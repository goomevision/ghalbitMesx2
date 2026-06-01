package com.ghalbitnet.meshx2.diagnostics.autodiag

enum class AutoDiagnosticStatus {
    PASS,
    PARTIAL,
    FAIL
}

data class AutoDiagnosticStep(
    val name: String,
    val status: AutoDiagnosticStatus,
    val score: Int,
    val notes: List<String> = emptyList()
)

