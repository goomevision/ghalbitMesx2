package com.ghalbitnet.meshx2.verified.referral

interface ReferralRepository {
    suspend fun save(entity: ReferralEntity)
    suspend fun findByUser(globalId: String): ReferralEntity?
}
