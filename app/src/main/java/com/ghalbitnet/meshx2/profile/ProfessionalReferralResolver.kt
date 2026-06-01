package com.ghalbitnet.meshx2.profile

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.verified.trust.ReferralBadge
import com.ghalbitnet.meshx2.verified.trust.ReferralBadgeRenderer

object ProfessionalReferralResolver {
    data class Result(
        val seen: Int,
        val savedContact: Int,
        val verified: Int,
        val joined: Int,
        val active: Int,
        val rewarded: Int,
        val total: Int,
        val label: String,
        val source: String,
        val fallbackUsed: Boolean
    )

    fun resolve(context: Context?, profile: CommunityProfile): Result {
        if (context == null) {
            Log.d("GHALBIT-CARD-TRUST", "referral fallback used source=noContext")
            return Result(0, 0, 0, 0, 0, 0, 0, "belum tersedia", "fallback-no-context", true)
        }
        val dao = ProfileDatabase.getInstance(context.applicationContext).profileDao()
        val aliases = dao.listContactAliases()
        val markers = aliases.flatMap { alias ->
            alias.localTagsCsv.split(',').map { it.trim() }
        }.filter { it.isNotBlank() }

        val targetByType = linkedMapOf(
            "referral_seen" to mutableSetOf<String>(),
            "referral_saved_contact" to mutableSetOf<String>(),
            "referral_verified" to mutableSetOf<String>(),
            "referral_joined" to mutableSetOf<String>(),
            "referral_rewarded" to mutableSetOf<String>()
        )
        markers.forEach { tag ->
            val idx = tag.indexOf(':')
            if (idx <= 0 || idx >= tag.length - 1) return@forEach
            val key = tag.substring(0, idx).lowercase()
            val value = tag.substring(idx + 1).trim()
            if (value.isBlank()) return@forEach
            when {
                key == "referral" && value.equals(profile.globalId, true) -> targetByType["referral_seen"]?.add("legacy:${profile.globalId}")
                key == "sponsor" && value.equals(profile.globalId, true) -> targetByType["referral_joined"]?.add("legacy:${profile.globalId}")
                key.startsWith("referral_") && value.equals(profile.globalId, true) -> {
                    // ignore self-targeted malformed legacy tags
                }
                else -> {
                    if (key in targetByType.keys && tag.contains("${key}:")) {
                        // only count tags stored under the alias of this source profile
                        // source alias tags are filtered by prefix referral:* / sponsor:* markers below
                    }
                }
            }
        }

        val sourceAliasMarkers = aliases.filter { alias ->
            alias.localTagsCsv.split(',').any { t ->
                val v = t.trim()
                v.equals("referral:${profile.globalId}", true) || v.equals("sponsor:${profile.globalId}", true)
            }
        }.flatMap { it.localTagsCsv.split(',').map(String::trim) }

        sourceAliasMarkers.forEach { tag ->
            val idx = tag.indexOf(':')
            if (idx <= 0 || idx >= tag.length - 1) return@forEach
            val key = tag.substring(0, idx).lowercase()
            val value = tag.substring(idx + 1).trim()
            if (value.isBlank()) return@forEach
            targetByType[key]?.add(value)
        }

        val seen = targetByType["referral_seen"]?.size ?: 0
        val savedContact = targetByType["referral_saved_contact"]?.size ?: 0
        val verified = targetByType["referral_verified"]?.size ?: 0
        val joined = targetByType["referral_joined"]?.size ?: 0
        val rewarded = targetByType["referral_rewarded"]?.size ?: 0
        val active = (savedContact + verified + joined).coerceAtLeast(seen)
        val total = (seen + savedContact + verified + joined + rewarded)
        val fallback = total == 0
        val source = if (fallback) "fallback-no-referral-records" else "contact_alias_tags"
        Log.d("GHALBIT-CARD-TRUST", "referral source resolved source=$source seen=$seen saved=$savedContact verified=$verified joined=$joined rewarded=$rewarded total=$total")
        if (fallback) {
            Log.d("GHALBIT-CARD-TRUST", "referral fallback used")
        }
        val label = if (total == 0) {
            "belum tersedia"
        } else {
            ReferralBadgeRenderer.label(ReferralBadge(activeReferrals = active, rewardedReferrals = rewarded))
                .removePrefix("Referral ")
        }
        return Result(seen, savedContact, verified, joined, active, rewarded, total, label, source, fallback)
    }
}
