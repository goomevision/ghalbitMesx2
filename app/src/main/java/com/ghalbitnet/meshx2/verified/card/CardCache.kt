package com.ghalbitnet.meshx2.verified.card

import java.util.concurrent.ConcurrentHashMap

object CardCache {
    private val cache = ConcurrentHashMap<String, CardStorageEntity>()

    fun put(card: CardStorageEntity) { cache[card.globalId] = card }
    fun get(globalId: String): CardStorageEntity? = cache[globalId]
    fun remove(globalId: String) { cache.remove(globalId) }
    fun clear() { cache.clear() }
}
