package com.ghalbitnet.meshx2.verified.trust

data class RewardRule(
    val minimumActiveDays: Int = 30,
    val rewardGhbt: Double = 10.0
)

object RewardEligibility {

    fun isEligible(
        activeDays: Int,
        rule: RewardRule = RewardRule()
    ): Boolean {
        return activeDays >= rule.minimumActiveDays
    }
}
