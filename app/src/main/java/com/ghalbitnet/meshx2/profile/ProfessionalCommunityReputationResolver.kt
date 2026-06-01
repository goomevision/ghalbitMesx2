package com.ghalbitnet.meshx2.profile

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.economy.CommunityRelayEconomy
import com.ghalbitnet.meshx2.verified.trust.CommunityFootprint
import com.ghalbitnet.meshx2.verified.trust.RealCommunityReputationCalculator

object ProfessionalCommunityReputationResolver {
    data class Result(
        val reputation: Int,
        val contributionPoints: Int,
        val summary: String,
        val source: String,
        val fallbackUsed: Boolean
    )

    fun resolve(
        context: Context?,
        profile: CommunityProfile,
        mentorCount: Int,
        referralRewarded: Int
    ): Result {
        if (context == null) {
            return Result(
                reputation = 0,
                contributionPoints = 0,
                summary = "Reputasi komunitas belum tersedia",
                source = "fallback-no-context",
                fallbackUsed = true
            )
        }
        val dao = ProfileDatabase.getInstance(context.applicationContext).profileDao()
        val contactsTotal = dao.countContactProfiles()
        val verifiedContacts = dao.countVerifiedContactProfiles()
        val savedSignals = dao.countSavedContactSignals()

        val relayContribution = CommunityRelayEconomy.getContributions(context.applicationContext)
            .firstOrNull { contribution ->
                contribution.nodeId.equals(profile.globalId, ignoreCase = true) ||
                    contribution.nodeId.equals(profile.publicKeyHash, ignoreCase = true) ||
                    contribution.nodeId.contains(profile.globalId.takeLast(6), ignoreCase = true)
            }
        val contributionPoints = ((relayContribution?.relayedPacketCount ?: 0) / 3) +
            ((relayContribution?.uptimeScore ?: 0) / 5) +
            ((relayContribution?.bandwidthScore ?: 0) / 5) +
            (savedSignals * 2) +
            (verifiedContacts * 3)

        val footprint = CommunityFootprint(
            communityId = profile.communityName.ifBlank { "GhalbitNet Community" },
            joinedAt = profile.updatedAt,
            eventsParticipated = (savedSignals + (contributionPoints / 8)).coerceAtMost(30),
            projectsParticipated = (verifiedContacts + (contributionPoints / 10)).coerceAtMost(30),
            helpProvidedCount = contactsTotal.coerceAtMost(20)
        )
        val reputation = RealCommunityReputationCalculator.calculate(
            footprint = footprint,
            mentorCount = mentorCount,
            referralCount = referralRewarded
        )
        val fallback = contactsTotal == 0 && verifiedContacts == 0 && contributionPoints == 0
        val summary = if (fallback) {
            "Reputasi komunitas belum tersedia"
        } else {
            "Kontak:$contactsTotal • Verified:$verifiedContacts • Kontribusi:$contributionPoints"
        }
        val source = if (fallback) "fallback-minimal-signals" else "profile_db+relay_economy"
        Log.d(
            "GHALBIT-CARD-TRUST",
            "reputation source resolved source=$source reputation=$reputation contacts=$contactsTotal verified=$verifiedContacts contribution=$contributionPoints"
        )
        if (fallback) {
            Log.d("GHALBIT-CARD-TRUST", "reputation fallback used")
        }
        return Result(
            reputation = reputation,
            contributionPoints = contributionPoints,
            summary = summary,
            source = source,
            fallbackUsed = fallback
        )
    }
}
