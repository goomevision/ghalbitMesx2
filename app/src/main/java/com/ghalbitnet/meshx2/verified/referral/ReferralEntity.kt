package com.ghalbitnet.meshx2.verified.referral

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "referral_records")
data class ReferralEntity(
    @PrimaryKey val newUserGlobalId: String,
    val referralCode: String,
    val sponsorGlobalId: String,
    val installedAt: Long,
    val status: String
)
