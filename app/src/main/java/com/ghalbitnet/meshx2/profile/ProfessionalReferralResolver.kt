package com.ghalbitnet.meshx2.profile

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.verified.trust.ReferralBadge
import com.ghalbitnet.meshx2.verified.trust.ReferralBadgeRenderer

object ProfessionalReferralResolver {
    data class Result(
        val active: Int,
        val rewarded: Int,
        val label: String,
        val source: String,
        val fallbackUsed: Boolean
    )

    fun resolve(context: Context?, profile: CommunityProfile): Result {
        if (context == null) {
            Log.d("GHALBIT-CARD-TRUST", "referral fallback used source=noContext")
            return Result(0, 0, "0/0", "fallback-no-context", true)
        }
        val dao = ProfileDatabase.getInstance(context.applicationContext).profileDao()
        val aliases = dao.listContactAliases()
        val markers = aliases.flatMap { alias ->
            alias.localTagsCsv.split(',').map { it.trim() }
        }
        val active = markers.count {
            it.equals("referral:${profile.globalId}", ignoreCase = true) ||
                it.equals("sponsor:${profile.globalId}", ignoreCase = true)
        }
        val rewarded = markers.count {
            it.equals("referral_rewarded:${profile.globalId}", ignoreCase = true)
        }
        val fallback = active == 0 && rewarded == 0
        val source = if (fallback) "fallback-no-referral-records" else "contact_alias_tags"
        Log.d("GHALBIT-CARD-TRUST", "referral source resolved source=$source active=$active rewarded=$rewarded")
        if (fallback) {
            Log.d("GHALBIT-CARD-TRUST", "referral fallback used")
        }
        val label = ReferralBadgeRenderer.label(ReferralBadge(activeReferrals = active, rewardedReferrals = rewarded))
            .removePrefix("Referral ")
        return Result(active, rewarded, label, source, fallback)
    }
}

