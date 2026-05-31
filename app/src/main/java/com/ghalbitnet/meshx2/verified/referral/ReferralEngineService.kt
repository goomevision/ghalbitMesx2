package com.ghalbitnet.meshx2.verified.referral

object ReferralEngineService {
    fun register(
        referralCode: String,
        sponsorGlobalId: String,
        newUserGlobalId: String,
        now: Long = System.currentTimeMillis()
    ): ReferralRegistrationRecord {
        return ReferralRegistrationRecord(
            referralCode = referralCode,
            sponsorGlobalId = sponsorGlobalId,
            newUserGlobalId = newUserGlobalId,
            installedAt = now,
            status = "REGISTERED"
        )
    }
}
