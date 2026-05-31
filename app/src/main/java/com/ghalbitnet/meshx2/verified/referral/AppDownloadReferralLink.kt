package com.ghalbitnet.meshx2.verified.referral

object AppDownloadReferralLink {
    fun build(referralCode:String):String {
        return "https://ghalbit.net/download?ref=$referralCode"
    }
}
