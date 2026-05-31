package com.ghalbitnet.meshx2.verified.referral

object RewardDistributionEngine {
    fun calculateReward(plan: ReferralRewardPlan): Double {
        return plan.rewardAmount
    }
}
