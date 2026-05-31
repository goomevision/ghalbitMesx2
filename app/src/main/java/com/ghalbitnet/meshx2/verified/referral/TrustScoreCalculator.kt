package com.ghalbitnet.meshx2.verified.referral

object TrustScoreCalculator {
    fun calculate(referrals:Int, mentors:Int, activeNodes:Int): Int {
        return (referrals * 2) + (mentors * 5) + (activeNodes * 3)
    }
}
