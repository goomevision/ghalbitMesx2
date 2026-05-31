package com.ghalbitnet.meshx2.verified.card

object CardIntegrityValidator {

    fun validate(card: CardStorageEntity): Boolean {
        if (card.globalId.isBlank()) return false
        if (card.displayName.isBlank()) return false
        if (card.profileHash.isBlank()) return false
        if (card.publicKeyHash.isBlank()) return false
        return true
    }
}
