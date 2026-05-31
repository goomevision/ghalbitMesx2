package com.ghalbitnet.meshx2.verified.trust

object ReferralCounter {
    fun active(records: List<ReferralRecord>): Int =
        records.count { it.status == ReferralStatus.ACTIVE }

    fun rewarded(records: List<ReferralRecord>): Int =
        records.count { it.status == ReferralStatus.REWARDED }
}
