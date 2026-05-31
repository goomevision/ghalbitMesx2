package com.ghalbitnet.meshx2.verified.referral

data class ContributionLedgerEntry(
 val globalId:String,
 val contributionType:String,
 val points:Int,
 val description:String,
 val timestamp:Long
)