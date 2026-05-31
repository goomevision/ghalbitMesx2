package com.ghalbitnet.meshx2.verified.referral

object ContributionLedgerManager {
    fun totalPoints(entries: List<ContributionLedgerEntry>): Int {
        return entries.sumOf { it.points }
    }
}
