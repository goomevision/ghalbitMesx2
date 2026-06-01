package com.ghalbitnet.meshx2.profile

import com.ghalbitnet.meshx2.verified.trust.CommunityFootprint
import com.ghalbitnet.meshx2.verified.trust.CommunityReputationEngine
import com.ghalbitnet.meshx2.verified.trust.IdentityLevel
import com.ghalbitnet.meshx2.verified.trust.MentorBadgeRenderer
import com.ghalbitnet.meshx2.verified.trust.ProfessionalCardSummaryFactory
import com.ghalbitnet.meshx2.verified.trust.ProfessionalCardTrustSummary
import com.ghalbitnet.meshx2.verified.trust.RealTrustScoreCalculator
import com.ghalbitnet.meshx2.verified.trust.VerifiedIdentityRecord
import com.ghalbitnet.meshx2.verified.ui.ProfessionalCardUiModel
import com.ghalbitnet.meshx2.security.NodeSigningIdentityManager

object ProfessionalCardDataMapper {

    data class Result(
        val model: ProfessionalCardUiModel,
        val trustSummary: ProfessionalCardTrustSummary,
        val tier: ProfessionalCardTier,
        val verificationStatus: ProfileVerificationStatus,
        val badges: List<String>
    )

    fun fromProfile(profile: CommunityProfile): Result {
        val verificationStatus = verifyProfile(profile)
        val identity = VerifiedIdentityRecord(
            globalId = profile.globalId,
            publicKeyHash = profile.publicKeyHash,
            displayName = profile.primaryName,
            community = profile.communityName.ifBlank { "GhalbitNet Community" },
            role = profile.roleTitle.ifBlank { "Anggota Komunitas" },
            createdAt = profile.updatedAt,
            verifiedAt = profile.verifiedAt,
            identityLevel = if (verificationStatus == ProfileVerificationStatus.VALID_SIGNATURE) IdentityLevel.COMMUNITY_VERIFIED else IdentityLevel.UNVERIFIED
        )
        val footprint = CommunityFootprint(
            communityId = profile.communityName.ifBlank { "GhalbitNet Community" },
            joinedAt = profile.updatedAt,
            eventsParticipated = profile.skillTags.size.coerceAtMost(8),
            projectsParticipated = profile.skillTags.size.coerceAtMost(5),
            helpProvidedCount = if (profile.statusMessage.isNotBlank()) 1 else 0
        )
        val trustScore = RealTrustScoreCalculator.calculate(identity = identity, community = footprint)
        val mentorCount = 0
        val referralActive = 0
        val referralRewarded = 0
        val reputation = CommunityReputationEngine.calculate(
            memberCount = 0,
            activeMentors = mentorCount,
            successfulReferrals = referralRewarded,
            contributionPoints = trustScore / 5
        )

        val summaryRaw = ProfessionalCardSummaryFactory.create(
            trustScore = trustScore,
            mentorCount = mentorCount,
            referralActive = referralActive,
            referralRewarded = referralRewarded,
            reputation = reputation
        )
        val summary = summaryRaw.copy(
            trustRank = normalizeRank(summaryRaw.trustRank),
            mentorLevel = summaryRaw.mentorLevel.ifBlank { MentorBadgeRenderer.level(0) },
            referralLabel = summaryRaw.referralLabel.ifBlank { "0/0" }
        )
        val tier = ProfessionalCardTierSystem.resolve(verificationStatus == ProfileVerificationStatus.VALID_SIGNATURE, summary)
        val badges = buildList {
            if (verificationStatus == ProfileVerificationStatus.VALID_SIGNATURE) add("VERIFIED")
            if (summary.trustScore >= 40) add("TRUSTED")
            if (!summary.mentorLevel.equals("Belum Menjadi Mentor", true)) add("MENTOR")
        }

        val model = ProfessionalCardUiModel(
            globalId = profile.globalId.ifBlank { "GX-UNKNOWN" },
            displayName = profile.primaryName.ifBlank { "Pengguna GHALBITNET" },
            nickname = profile.nickname.ifBlank { profile.globalId.takeLast(6) },
            role = profile.roleTitle.ifBlank { "Anggota Komunitas" },
            bio = profile.bio.ifBlank { "Belum ada bio." },
            community = profile.communityName.ifBlank { "GhalbitNet Community" },
            region = profile.region.ifBlank { "Wilayah belum diisi" },
            profilePhotoUri = profile.avatarUri,
            publicKeyHash = profile.publicKeyHash,
            profileVersion = profile.profileVersion,
            signature = profile.signature,
            updatedAt = profile.updatedAt,
            trustScore = summary.trustScore,
            trustRank = summary.trustRank,
            mentorStatus = summary.mentorLevel,
            referralLabel = summary.referralLabel,
            communityReputation = summary.communityReputation,
            tier = tier,
            verificationStatus = verificationStatus,
            badges = badges,
            verified = verificationStatus == ProfileVerificationStatus.VALID_SIGNATURE
        )
        return Result(model, summary, tier, verificationStatus, badges)
    }

    fun verifyProfile(profile: CommunityProfile): ProfileVerificationStatus {
        if (profile.signature.isBlank()) return ProfileVerificationStatus.UNSIGNED
        if (profile.publicKeyBase64.isBlank()) return ProfileVerificationStatus.UNKNOWN
        val payload = ProfileQrPayload(
            globalId = profile.globalId,
            publicKey = profile.publicKeyBase64,
            publicKeyHash = profile.publicKeyHash,
            displayName = profile.displayName,
            nickname = profile.nickname,
            roleTitle = profile.roleTitle,
            profileVersion = profile.profileVersion,
            relayHint = profile.routeHint,
            signature = ""
        )
        val ok = NodeSigningIdentityManager.verify(
            profile.publicKeyBase64,
            ProfileQrCodec.canonicalPayload(payload),
            profile.signature
        )
        return if (ok) ProfileVerificationStatus.VALID_SIGNATURE else ProfileVerificationStatus.INVALID_SIGNATURE
    }

    private fun normalizeRank(rank: String): String = if (rank.equals("Pemula", true)) "Aktif" else rank
}
