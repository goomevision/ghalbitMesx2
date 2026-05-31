package com.ghalbitnet.meshx2.verified.referral

data class ReferralRegistrationRecord(
 val referralCode:String,
 val sponsorGlobalId:String,
 val newUserGlobalId:String,
 val installedAt:Long,
 val status:String
)