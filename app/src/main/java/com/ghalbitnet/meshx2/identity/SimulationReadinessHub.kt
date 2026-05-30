package com.ghalbitnet.meshx2.identity

import android.content.Context
import com.ghalbitnet.meshx2.economy.EconomyParticipantAggregator
import com.ghalbitnet.meshx2.economy.EconomyParticipantDiagnostics
import java.io.File

object SimulationReadinessHub {

    fun report(
        context: Context
    ): String {
        val gate =
            MigrationGateEvaluator.evaluate(context)
        val safety =
            SimulationSafetyEvaluator.evaluate(context)
        val routing =
            RoutingIdentityAggregator.summarize(
                RoutingShadowIdentityReporter.inspect(context)
            )
        val economy =
            EconomyParticipantAggregator.summarize(
                EconomyParticipantDiagnostics.inspect(context)
            )
        val dedup =
            DedupRiskReporter.summarize(
                IdentityDedupReporter.candidates(context)
            )

        val highestConflict =
            when {
                dedup.topRiskyConflicts.any {
                    IdentityConflictClassifier.fromDedupRisk(it).severity == "critical"
                } -> "critical"
                dedup.topRiskyConflicts.any {
                    IdentityConflictClassifier.fromDedupRisk(it).severity == "high"
                } -> "high"
                dedup.topRiskyConflicts.any {
                    IdentityConflictClassifier.fromDedupRisk(it).severity == "medium"
                } -> "medium"
                dedup.totalCandidates > 0 -> "low"
                else -> "informational"
            }

        val rollbackStatus =
            if (hasRollbackDocs()) "documented" else "missing"
        val runtimeState =
            SimulationRuntimeShell.currentState()
        val sessionCount =
            SimulationSessionRegistry.count()
        val control =
            SimulationControlEvaluator.evaluate(context)

        return buildString {
            appendLine("UNIFIED SIMULATION READINESS")
            appendLine("======================")
            appendLine("runtime=${runtimeState.status} | enabled=${runtimeState.enabled} | sessions=${sessionCount}")
            appendLine("gate=${gate.category} | safety=${safety.score} (${safety.label})")
            appendLine("control=${control.state.name} | decision=${control.decision.name}")
            appendLine("routingAvg=${routing.averageConfidence} | economyAvg=${economy.averageConfidence} | dedupUnsafe=${dedup.unsafeOrAmbiguousCount}")
            appendLine("rollback=${rollbackStatus} | conflict=${highestConflict}")
            appendLine("capability=identity:allowed | routing:guarded | reward:guarded | dedup:allowed | notification:guarded | rollback:future-only")
            appendLine()
            control.blockers.take(2).forEach { blocker ->
                appendLine("- blocker: $blocker")
            }
            control.requiredNextActions.take(2).forEach { action ->
                appendLine("- next: $action")
            }
            safety.notes.take(3).forEach { note ->
                appendLine("- $note")
            }
        }.trim()
    }

    private fun hasRollbackDocs(): Boolean =
        repoFile("SIMULATION_ROLLBACK_VALIDATION.md").exists() &&
            repoFile("CANONICAL_MIGRATION_RISK_REGISTER.md").exists() &&
            repoFile("CANONICAL_MIGRATION_STAGE_PLAN.md").exists()

    private fun repoFile(name: String): File =
        File(System.getProperty("user.dir"), name)
}
