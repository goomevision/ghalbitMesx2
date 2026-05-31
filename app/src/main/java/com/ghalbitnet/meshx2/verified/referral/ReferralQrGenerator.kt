package com.ghalbitnet.meshx2.verified.referral

object ReferralQrGenerator {
    fun buildPayload(payload: ReferralQrPayload): String {
        return "${payload.referralCode}|${payload.sponsorGlobalId}|${payload.downloadUrl}"
    }
}
