package com.ghalbitnet.meshx2.verified.referral

data class ReferralRewardPlan(
    val rewardAsset: String = "GHBT",
    val rewardAmount: Double,
    val requiresNewInstall: Boolean = true,
    val requiresIdentityVerification: Boolean = false
)
