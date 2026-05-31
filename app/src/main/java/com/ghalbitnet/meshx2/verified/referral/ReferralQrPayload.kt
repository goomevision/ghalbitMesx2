package com.ghalbitnet.meshx2.verified.referral

data class ReferralQrPayload(
    val referralCode:String,
    val sponsorGlobalId:String,
    val downloadUrl:String
)