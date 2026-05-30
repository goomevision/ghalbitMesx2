package com.ghalbitnet.meshx2.identity

import com.ghalbitnet.meshx2.economy.EconomyIdentitySource
import com.ghalbitnet.meshx2.economy.EconomyParticipantIdentity

object IdentityConflictClassifier {

    fun fromDedupRisk(score: DedupRiskScore): IdentityConflictAssessment {
        val candidate = score.candidate
        val type =
            when {
                candidate.conflictingPublicKey -> "publicKey conflict"
                candidate.conflictingWalletAddress -> "wallet conflict"
                candidate.conflictingGlobalId -> "globalId conflict"
                candidate.sameIp && !candidate.samePublicKey && !candidate.sameGlobalId && !candidate.sameWalletAddress -> "IP reuse"
                candidate.sameDisplayName && !candidate.samePublicKey && !candidate.sameGlobalId && !candidate.sameWalletAddress -> "peerName drift"
                else -> "unresolved duplicate"
            }
        val severity =
            when {
                candidate.conflictingPublicKey -> "critical"
                candidate.conflictingWalletAddress || candidate.conflictingGlobalId -> "high"
                score.category == "high-confidence duplicate" -> "medium"
                score.category == "likely duplicate" -> "medium"
                score.category == "possible duplicate" -> "low"
                else -> "informational"
            }
        val action =
            when (severity) {
                "critical" -> "manual review required"
                "high" -> "block simulation"
                "medium" -> "diagnostics review"
                else -> "observe only"
            }
        return IdentityConflictAssessment(type, severity, action, score.score)
    }

    fun fromMigrationCandidate(candidate: MigrationDryRunCandidate): IdentityConflictAssessment {
        val type =
            when {
                candidate.classification == "blocked candidate" && candidate.canonicalReference == null -> "mixed ownership"
                candidate.riskLevel == "high" -> "unresolved duplicate"
                candidate.riskLevel == "unknown" -> "mixed ownership"
                else -> "peerName drift"
            }
        val severity =
            when (candidate.classification) {
                "blocked candidate" -> "high"
                "risky candidate" -> "medium"
                else -> "low"
            }
        val action =
            when (candidate.classification) {
                "blocked candidate" -> "block simulation"
                "risky candidate" -> "diagnostics review"
                else -> "observe only"
            }
        return IdentityConflictAssessment(type, severity, action, candidate.confidence)
    }

    fun fromRoutingIdentity(item: RoutingShadowIdentity): IdentityConflictAssessment {
        val type =
            when {
                item.riskLevel == "high" || item.riskLevel == "unknown" -> "routing mismatch"
                item.routeOwnerGlobalId.isNullOrBlank() && item.routeOwnerPublicKey.isNullOrBlank() -> "IP reuse"
                else -> "mixed ownership"
            }
        val severity =
            when (item.riskLevel) {
                "high" -> "high"
                "unknown" -> "medium"
                "medium" -> "medium"
                else -> "low"
            }
        val action =
            when (severity) {
                "high" -> "block simulation"
                "medium" -> "diagnostics review"
                else -> "observe only"
            }
        return IdentityConflictAssessment(type, severity, action, item.confidence)
    }

    fun fromEconomyParticipant(item: EconomyParticipantIdentity): IdentityConflictAssessment {
        val type =
            when {
                item.source == EconomyIdentitySource.UNKNOWN -> "economy mismatch"
                item.source == EconomyIdentitySource.LEDGER_LEGACY -> "mixed ownership"
                item.source == EconomyIdentitySource.PEER_IP || item.source == EconomyIdentitySource.NODE_ID -> "economy mismatch"
                else -> "peerName drift"
            }
        val severity =
            when {
                item.confidence < 20 -> "high"
                item.confidence < 40 -> "medium"
                item.confidence < 60 -> "low"
                else -> "informational"
            }
        val action =
            when (severity) {
                "high" -> "manual review required"
                "medium" -> "diagnostics review"
                else -> "observe only"
            }
        return IdentityConflictAssessment(type, severity, action, item.confidence)
    }
}
