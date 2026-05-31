package com.ghalbitnet.meshx2.profile

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.verified.trust.CommunityFootprint
import com.ghalbitnet.meshx2.verified.trust.CommunityReputationEngine
import com.ghalbitnet.meshx2.verified.trust.IdentityLevel
import com.ghalbitnet.meshx2.verified.trust.MentorBadgeRenderer
import com.ghalbitnet.meshx2.verified.trust.ProfessionalCardSummaryFactory
import com.ghalbitnet.meshx2.verified.trust.RealTrustScoreCalculator
import com.ghalbitnet.meshx2.verified.trust.VerifiedIdentityRecord

object ContactCardRenderer {
    fun bind(root: View, profile: CommunityProfile, routeBadge: String, qrBitmap: Bitmap?) {
        val trustSummary = buildTrustSummary(profile).toUi()
        root.background = ContextCompat.getDrawable(root.context, profile.cardTheme.cardBackgroundRes)
        root.findViewById<TextView>(R.id.txtVerifiedBadge)?.text =
            if (profile.signature.isNotBlank()) "VERIFIED ✓" else "LOCAL"
        root.findViewById<TextView>(R.id.txtRouteBadge)?.text = routeBadge
        root.findViewById<TextView>(R.id.txtCardName)?.text = profile.primaryName
        root.findViewById<TextView>(R.id.txtCardNickname)?.text = profile.publicSubtitle
        root.findViewById<TextView>(R.id.txtCardRole)?.text =
            listOfNotNull(
                profile.communityLabel?.takeIf { it.isNotBlank() },
                profile.roleTitle.takeIf { it.isNotBlank() }
            ).joinToString(" • ").ifBlank { "Anggota GhalbitNet" }
        root.findViewById<TextView>(R.id.txtCardStatus)?.text =
            profile.statusMessage.ifBlank { defaultStatus(profile.statusType) }
        root.findViewById<TextView>(R.id.txtCardLocation)?.text =
            profile.region.takeIf { it.isNotBlank() } ?: "Wilayah belum diisi"
        root.findViewById<TextView>(R.id.txtCardSkills)?.text =
            profile.skillTags.takeIf { it.isNotEmpty() }?.joinToString("  •  ") ?: "Belum ada tag keahlian"
        root.findViewById<TextView>(R.id.txtCardTrust)?.text =
            "Trust Score: ${trustSummary.trustScore} • Rank: ${trustSummary.rank}"
        root.findViewById<TextView>(R.id.txtCardMentorBadge)?.text =
            "Mentor: ${trustSummary.mentorLabel}"
        root.findViewById<TextView>(R.id.txtCardReferralBadge)?.text =
            "Referral: ${trustSummary.referralLabel}"
        root.findViewById<TextView>(R.id.txtCardReputationBadge)?.text =
            "Community Reputation: ${trustSummary.reputation}"
        root.findViewById<TextView>(R.id.txtCardCommunity)?.text =
            profile.communityName.takeIf { it.isNotBlank() }
                ?: profile.communityLabel?.takeIf { it.isNotBlank() }
                ?: "Komunitas lokal"
        root.findViewById<TextView>(R.id.txtCardNote)?.apply {
            val note = profile.localNote?.takeIf { it.isNotBlank() }
            visibility = if (note != null) View.VISIBLE else View.GONE
            text = note ?: ""
        }
        root.findViewById<TextView>(R.id.txtCardGlobalId)?.text = profile.globalId
        root.findViewById<TextView>(R.id.txtCardPublicKeyHash)?.text = profile.publicKeyHash
        root.findViewById<ImageView>(R.id.imgCardQr)?.setImageBitmap(qrBitmap)

        root.findViewById<ImageView>(R.id.imgCardAvatar)?.apply {
            val avatar = profile.avatarUri?.takeIf { it.isNotBlank() }
            if (avatar != null) {
                runCatching { setImageURI(Uri.parse(avatar)) }
                    .onFailure { setImageDrawable(null) }
            } else {
                setImageDrawable(null)
            }
            root.findViewById<TextView>(R.id.txtCardInitials)?.visibility =
                if (drawable != null) View.GONE else View.VISIBLE
        }
        root.findViewById<TextView>(R.id.txtCardInitials)?.text = initials(profile.primaryName)
        root.findViewById<View>(R.id.viewCardAccent)?.setBackgroundColor(
            runCatching { Color.parseColor(profile.bannerColor) }.getOrDefault(Color.parseColor(profile.cardTheme.accentColor))
        )
        Log.d("GHALBIT-CARD", "rendered id=${profile.globalId}")
        Log.d("GHALBIT-CARD", "lightweight theme applied")
    }

    private fun buildTrustSummary(profile: CommunityProfile): com.ghalbitnet.meshx2.verified.trust.ProfessionalCardTrustSummary {
        val identity =
            VerifiedIdentityRecord(
                globalId = profile.globalId,
                publicKeyHash = profile.publicKeyHash,
                displayName = profile.primaryName,
                community = profile.communityName.ifBlank { profile.communityLabel ?: "GHALBITNET" },
                role = profile.roleTitle.ifBlank { "Anggota" },
                createdAt = profile.updatedAt,
                verifiedAt = profile.verifiedAt,
                identityLevel = if (profile.signature.isNotBlank()) IdentityLevel.COMMUNITY_VERIFIED else IdentityLevel.UNVERIFIED
            )
        val footprint =
            CommunityFootprint(
                communityId = profile.communityName.ifBlank { "GHALBITNET" },
                joinedAt = profile.updatedAt,
                eventsParticipated = profile.skillTags.size.coerceAtMost(8),
                projectsParticipated = profile.skillTags.size.coerceAtMost(5),
                helpProvidedCount = if (profile.statusMessage.isNotBlank()) 1 else 0
            )
        val trustScore = RealTrustScoreCalculator.calculate(identity = identity, community = footprint)
        val mentorCount = 0
        val referralActive = 0
        val referralRewarded = 0
        val reputation =
            CommunityReputationEngine.calculate(
                memberCount = 0,
                activeMentors = mentorCount,
                successfulReferrals = referralRewarded,
                contributionPoints = trustScore / 5
            )
        val summary =
            ProfessionalCardSummaryFactory.create(
                trustScore = trustScore,
                mentorCount = mentorCount,
                referralActive = referralActive,
                referralRewarded = referralRewarded,
                reputation = reputation
            )
        val normalizedRank = normalizeRank(summary.trustRank)
        val normalizedMentor = summary.mentorLevel.ifBlank { MentorBadgeRenderer.level(0) }
        val normalizedReferral = summary.referralLabel.ifBlank { "0/0" }
        return summary.copy(
            trustRank = normalizedRank,
            mentorLevel = normalizedMentor,
            referralLabel = normalizedReferral
        )
    }

    private fun com.ghalbitnet.meshx2.verified.trust.ProfessionalCardTrustSummary.toUi(): ProfileTrustSummaryUi {
        return ProfileTrustSummaryUi(
            trustScore = trustScore,
            rank = trustRank,
            mentorLabel = mentorLevel,
            referralLabel = referralLabel,
            reputation = communityReputation
        )
    }

    private fun initials(name: String): String {
        return name.split(" ")
            .mapNotNull { it.trim().takeIf(String::isNotBlank)?.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")
            .ifBlank { "GN" }
    }

    private fun defaultStatus(type: CommunityStatusType): String {
        return when (type) {
            CommunityStatusType.AVAILABLE -> "Tersedia untuk membantu"
            CommunityStatusType.BUSY -> "Sedang sibuk"
            CommunityStatusType.EMERGENCY_HELPER -> "Relawan darurat aktif"
            CommunityStatusType.RELAY_OPERATOR -> "Operator relay komunitas"
            CommunityStatusType.OFFLINE -> "Sedang offline"
            CommunityStatusType.CUSTOM -> "Status komunitas"
        }
    }

    private fun normalizeRank(rank: String): String {
        return if (rank.equals("Pemula", ignoreCase = true)) "Aktif" else rank
    }
}
