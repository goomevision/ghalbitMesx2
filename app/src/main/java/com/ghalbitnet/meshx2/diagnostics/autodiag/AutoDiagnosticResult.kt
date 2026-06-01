package com.ghalbitnet.meshx2.diagnostics.autodiag

data class AutoDiagnosticResult(
    val steps: List<AutoDiagnosticStep>,
    val totalScore: Int,
    val status: AutoDiagnosticStatus
)

