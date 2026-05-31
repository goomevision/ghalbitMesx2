package com.ghalbitnet.meshx2.verified.card

interface CardRepository {
    suspend fun save(card: CardStorageEntity)
    suspend fun get(globalId: String): CardStorageEntity?
    suspend fun delete(globalId: String)
    suspend fun list(): List<CardStorageEntity>
}
