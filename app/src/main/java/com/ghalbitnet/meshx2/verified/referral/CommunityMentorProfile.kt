package com.ghalbitnet.meshx2.verified.referral

data class CommunityMentorProfile(
    val globalId:String,
    val displayName:String,
    val taughtMembers:Int=0,
    val referredMembers:Int=0,
    val communitiesFounded:Int=0
)