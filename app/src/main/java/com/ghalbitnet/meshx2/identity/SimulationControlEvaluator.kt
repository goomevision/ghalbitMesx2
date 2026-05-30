package com.ghalbitnet.meshx2.identity

import android.content.Context

object SimulationControlEvaluator {

    data class Result(
        val state: SimulationControlState,
        val decision: SimulationControlDecision,
        val reasons: List<SimulationControlReason>,
        val blockers: List<String>,
        val requiredNextActions: List<String>
    )

    fun evaluate(context: Context): Result {
        val gate =
            MigrationGateEvaluator.evaluate(context)
        val runtimeState =
            SimulationRuntimeShell.currentState()
        val sessionCount =
            SimulationSessionRegistry.count()
        val safety =
            SimulationSafetyEvaluator.evaluate(context)

        val reasons = mutableListOf<SimulationControlReason>()
        val blockers = mutableListOf<String>()
        val nextActions = mutableListOf<String>()

        if (runtimeState.enabled) {
            blockers += "Runtime shell tidak boleh enabled pada fase dormant."
        }
        if (gate.rollbackReadiness != "documented") {
            blockers += "Rollback readiness belum lengkap."
        }
        if (gate.diagnosticsCompleteness != "complete") {
            blockers += "Diagnostics belum lengkap."
        }
        if (gate.category == "blocked") {
            blockers += "Migration gate masih blocked."
        }
        if (safety.label == "blocked" || safety.label == "unsafe") {
            blockers += "Simulation safety belum cukup aman."
        }

        reasons += SimulationControlReason(
            label = "gate",
            detail = "Migration gate saat ini: ${gate.category}"
        )
        reasons += SimulationControlReason(
            label = "runtime",
            detail = "Runtime shell: ${runtimeState.status}, sessions=$sessionCount"
        )
        reasons += SimulationControlReason(
            label = "safety",
            detail = "Simulation safety: ${safety.score} (${safety.label})"
        )

        val state: SimulationControlState
        val decision: SimulationControlDecision

        if (blockers.isNotEmpty()) {
            state = SimulationControlState.BLOCKED
            decision = if (gate.rollbackReadiness != "documented") {
                SimulationControlDecision.REQUIRE_ROLLBACK_READINESS
            } else if (gate.diagnosticsCompleteness != "complete") {
                SimulationControlDecision.REQUIRE_FRESH_DIAGNOSTICS
            } else {
                SimulationControlDecision.BLOCK_ACTIVATION
            }
        } else if (gate.category == "guarded" || safety.label == "guarded") {
            state = SimulationControlState.GUARDED
            decision = SimulationControlDecision.REQUIRE_REVIEW
            nextActions += "Lakukan review operator sebelum mempertimbangkan aktivasi."
        } else {
            state = SimulationControlState.READY_BUT_NOT_ACTIVATED
            decision = SimulationControlDecision.ALLOW_DIAGNOSTICS_ONLY
            nextActions += "Tetap di mode diagnostics-only; aktivasi belum diimplementasikan."
        }

        if (nextActions.isEmpty()) {
            nextActions += "Pertahankan mode dormant dan observasi diagnostics."
        }

        return Result(
            state = state,
            decision = decision,
            reasons = reasons,
            blockers = blockers,
            requiredNextActions = nextActions
        )
    }

    fun report(context: Context): String {
        val result = evaluate(context)
        return buildString {
            appendLine("SIMULATION CONTROL PLANE")
            appendLine("======================")
            appendLine("state=${result.state.name} | decision=${result.decision.name}")
            appendLine()
            result.reasons.forEach { reason ->
                appendLine("- ${reason.label}: ${reason.detail}")
            }
            if (result.blockers.isNotEmpty()) {
                appendLine()
                appendLine("BLOCKERS")
                result.blockers.forEach { blocker ->
                    appendLine("- $blocker")
                }
            }
            appendLine()
            appendLine("NEXT ACTIONS")
            result.requiredNextActions.forEach { action ->
                appendLine("- $action")
            }
        }.trim()
    }
}
