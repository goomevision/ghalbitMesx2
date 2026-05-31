package com.ghalbitnet.meshx2.verified.referral

data class TrustScoreSnapshot(
 val globalId:String,
 val score:Int,
 val referrals:Int,
 val mentors:Int,
 val activeNodes:Int
)