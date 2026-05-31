package com.ghalbitnet.meshx2.verified.integration

import com.ghalbitnet.meshx2.verified.card.CardStorageEntity

object ContactNameCardIntegration {
    fun canDisplay(card: CardStorageEntity): Boolean {
        return card.displayName.isNotBlank()
    }
}
