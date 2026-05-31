package com.ghalbitnet.meshx2.verified.referral

data class InstallAttribution(
 val deviceId:String,
 val referralCode:String?,
 val firstLaunchAt:Long,
 val locked:Boolean=true
)