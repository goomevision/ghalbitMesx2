package com.ghalbitnet.meshx2.verified.referral

data class TrustRelationshipRecord(
    val sponsorGlobalId:String,
    val memberGlobalId:String,
    val relationshipType:String,
    val establishedAt:Long
)