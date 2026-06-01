package com.ghalbitnet.meshx2.verified.ui

import com.ghalbitnet.meshx2.profile.ProfessionalCardTier
import com.ghalbitnet.meshx2.profile.ProfileVerificationStatus

data class ProfessionalCardUiModel(
    val globalId: String,
    val displayName: String,
    val nickname: String = "",
    val role: String,
    val bio: String = "",
    val community: String,
    val region: String = "Wilayah belum diisi",
    val trustScore: Int,
    val trustRank: String = "Baru",
    val mentorStatus: String = "Belum Menjadi Mentor",
    val referralLabel: String = "0/0",
    val communityReputation: Int = 0,
    val verified: Boolean,
    val verificationStatus: ProfileVerificationStatus = ProfileVerificationStatus.UNKNOWN,
    val tier: ProfessionalCardTier = ProfessionalCardTier.BASIC,
    val badges: List<String> = emptyList(),
    val profilePhotoUri: String? = null,
    val publicKeyHash: String = "",
    val profileVersion: Int = 0,
    val signature: String = "",
    val updatedAt: Long = 0L,
    val contributionSummary: String = "Reputasi komunitas belum tersedia",
    val qrPayload: String? = null
)
