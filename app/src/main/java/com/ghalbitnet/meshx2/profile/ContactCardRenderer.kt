package com.ghalbitnet.meshx2.profile

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ghalbitnet.meshx2.R

object ContactCardRenderer {
    fun bind(root: View, profile: CommunityProfile, routeBadge: String, qrBitmap: Bitmap?) {
        val mapped = ProfessionalCardDataMapper.fromProfile(root.context, profile)
        val model = mapped.model
        val trustSummary = mapped.trustSummary
        val theme = ProfessionalCardTierSystem.themeFor(mapped.tier)

        root.background = ContextCompat.getDrawable(root.context, profile.cardTheme.cardBackgroundRes)
        root.findViewById<TextView>(R.id.txtVerifiedBadge)?.text = verificationLabel(model.verificationStatus, mapped.tier.name)
        root.findViewById<TextView>(R.id.txtVerifiedBadge)?.apply {
            background?.setTint(theme.badgeBgColor)
            setTextColor(theme.badgeTextColor)
        }
        root.findViewById<TextView>(R.id.txtRouteBadge)?.text = routeBadge
        root.findViewById<TextView>(R.id.txtRouteBadge)?.setTextColor(theme.routeTextColor)
        root.findViewById<TextView>(R.id.txtCardName)?.text = model.displayName
        root.findViewById<TextView>(R.id.txtCardNickname)?.text = model.nickname.ifBlank { model.globalId }
        root.findViewById<TextView>(R.id.txtCardRole)?.text =
            listOfNotNull(profile.communityLabel?.takeIf { it.isNotBlank() }, model.role.takeIf { it.isNotBlank() })
                .joinToString(" • ").ifBlank { "Anggota Komunitas" }
        val bioParts = mutableListOf<String>()
        if (model.careerHeadline.isNotBlank()) bioParts += model.careerHeadline
        if (model.bio.isNotBlank()) bioParts += model.bio
        if (!model.visionStatement.equals("Belum diisi", true) && model.visionStatement.isNotBlank()) {
            bioParts += "Visi: ${model.visionStatement}"
        }
        if (!model.missionStatement.equals("Belum diisi", true) && model.missionStatement.isNotBlank()) {
            bioParts += "Misi: ${model.missionStatement}"
        }
        if (model.activeProjects.isNotEmpty()) {
            bioParts += "Proyek: ${model.activeProjects.joinToString(" • ")}"
        }
        root.findViewById<TextView>(R.id.txtCardBio)?.text = bioParts.joinToString("\n").ifBlank { "Belum ada bio." }

        val statusParts = mutableListOf<String>()
        statusParts += profile.statusMessage.ifBlank { defaultStatus(profile.statusType) }
        if (model.availabilityStatus.isNotBlank()) statusParts += "Ketersediaan: ${model.availabilityStatus}"
        if (model.helpOffered.isNotEmpty()) statusParts += "Bantuan: ${model.helpOffered.joinToString(" • ")}"
        if (model.helpNeeded.isNotEmpty()) statusParts += "Butuh Bantuan: ${model.helpNeeded.joinToString(" • ")}"
        root.findViewById<TextView>(R.id.txtCardStatus)?.text = statusParts.joinToString("\n")
        root.findViewById<TextView>(R.id.txtCardLocation)?.text = model.region
        root.findViewById<TextView>(R.id.txtCardCommunity)?.text = model.community
        root.findViewById<TextView>(R.id.txtCardOrganization)?.text = profile.organization?.ifBlank { "Organisasi belum diisi" } ?: "Organisasi belum diisi"
        val skillsParts = mutableListOf<String>()
        if (model.skillsOffered.isNotEmpty()) skillsParts += "Ditawarkan: ${model.skillsOffered.joinToString(" • ")}"
        if (model.skillsWanted.isNotEmpty()) skillsParts += "Dicari: ${model.skillsWanted.joinToString(" • ")}"
        if (skillsParts.isEmpty() && profile.skillTags.isNotEmpty()) {
            skillsParts += profile.skillTags.joinToString(" • ")
        }
        if (model.communityRoles.isNotEmpty()) {
            skillsParts += "Peran: ${model.communityRoles.joinToString(" • ")}"
        }
        root.findViewById<TextView>(R.id.txtCardSkills)?.text =
            skillsParts.joinToString("\n").ifBlank { "Belum ada tag keahlian" }
        root.findViewById<TextView>(R.id.txtCardTrust)?.text =
            "Trust Score: ${trustSummary.trustScore} • Rank: ${trustSummary.trustRank}"
        root.findViewById<TextView>(R.id.txtCardMentorBadge)?.text = "Mentor: ${trustSummary.mentorLevel}"
        root.findViewById<TextView>(R.id.txtCardReferralBadge)?.text =
            when {
                model.referralRewardedCount > 0 -> "Referral: ${trustSummary.referralLabel} (rewarded)"
                model.referralPendingCount > 0 -> "Referral: ${trustSummary.referralLabel} (pending reward)"
                trustSummary.referralLabel.equals("belum tersedia", true) -> "Referral: belum tersedia"
                else -> "Referral: ${trustSummary.referralLabel}"
            }
        root.findViewById<TextView>(R.id.txtCardReputationBadge)?.text =
            if (model.communityReputation <= 0 && model.contributionSummary.contains("belum tersedia", ignoreCase = true)) {
                "Community Reputation: belum tersedia"
            } else {
                "Community Reputation: ${trustSummary.communityReputation}"
            }

        root.findViewById<TextView>(R.id.txtCardNote)?.apply {
            val note = profile.localNote?.takeIf { it.isNotBlank() }
            visibility = if (note != null) View.VISIBLE else View.GONE
            text = note ?: ""
        }
        root.findViewById<TextView>(R.id.txtCardGlobalId)?.text = model.globalId
        root.findViewById<TextView>(R.id.txtCardPublicKeyHash)?.text = model.publicKeyHash.ifBlank { "identity hash belum tersedia" }
        root.findViewById<ImageView>(R.id.imgCardQr)?.setImageBitmap(qrBitmap)

        root.findViewById<ImageView>(R.id.imgCardAvatar)?.apply {
            val avatar = model.profilePhotoUri?.takeIf { it.isNotBlank() }
            if (avatar != null) {
                val loaded = SafeAvatarLoader.loadInto(this, avatar)
                if (!loaded) setImageDrawable(null)
            } else {
                setImageDrawable(null)
            }
            root.findViewById<TextView>(R.id.txtCardInitials)?.visibility = if (drawable != null) View.GONE else View.VISIBLE
        }
        root.findViewById<TextView>(R.id.txtCardInitials)?.text = initials(model.displayName)
        root.findViewById<View>(R.id.viewCardAccent)?.setBackgroundColor(
            runCatching { Color.parseColor(profile.bannerColor) }.getOrDefault(theme.accentColor)
        )
        root.findViewById<TextView>(R.id.txtCardRole)?.background?.setTint(theme.badgeBgColor)
        root.findViewById<ImageView>(R.id.imgCardQr)?.alpha = 0.96f
        Log.d("GHALBIT-CARD", "rendered id=${profile.globalId}")
    }

    private fun verificationLabel(status: ProfileVerificationStatus, tier: String): String {
        val statusText = when (status) {
            ProfileVerificationStatus.VALID_SIGNATURE -> "VERIFIED"
            ProfileVerificationStatus.INVALID_SIGNATURE -> "INVALID SIGNATURE"
            ProfileVerificationStatus.UNSIGNED -> "UNSIGNED"
            ProfileVerificationStatus.UNKNOWN -> "UNKNOWN"
        }
        return "$statusText • $tier"
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
}
