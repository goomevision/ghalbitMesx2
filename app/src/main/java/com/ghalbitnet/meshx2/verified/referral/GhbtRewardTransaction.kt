package com.ghalbitnet.meshx2.verified.referral

data class GhbtRewardTransaction(
    val transactionId:String,
    val beneficiaryGlobalId:String,
    val amount:Double,
    val reason:String,
    val timestamp:Long
)