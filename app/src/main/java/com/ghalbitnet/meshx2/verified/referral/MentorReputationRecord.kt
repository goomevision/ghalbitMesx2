package com.ghalbitnet.meshx2.verified.referral

data class MentorReputationRecord(
 val mentorGlobalId:String,
 val studentGlobalId:String,
 val activityType:String,
 val awardedPoints:Int,
 val timestamp:Long
)