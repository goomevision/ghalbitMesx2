package com.ghalbitnet.meshx2.ui

import com.ghalbitnet.meshx2.chat.ChatDeliveryState

object GhalbitStatusStyle {
    fun accentFor(state: ChatDeliveryState): Int {
        return when (state) {
            ChatDeliveryState.READ,
            ChatDeliveryState.READ_REMOTE,
            ChatDeliveryState.MEDIA_READ_REMOTE -> GhalbitColors.ELECTRIC_BLUE
            ChatDeliveryState.DELIVERED,
            ChatDeliveryState.DELIVERED_REMOTE,
            ChatDeliveryState.SENT_LOCAL,
            ChatDeliveryState.SENT_INTERNET,
            ChatDeliveryState.MEDIA_DELIVERED_REMOTE -> GhalbitColors.ENERGY_GREEN
            ChatDeliveryState.ACCEPTED_BY_RELAY,
            ChatDeliveryState.QUEUED_REMOTE,
            ChatDeliveryState.MEDIA_QUEUED_REMOTE -> GhalbitColors.CYAN
            ChatDeliveryState.FAILED_FINAL,
            ChatDeliveryState.MEDIA_EXPIRED,
            ChatDeliveryState.EXPIRED_REMOTE -> GhalbitColors.WARNING
            else -> GhalbitColors.TEXT_SECONDARY
        }
    }
}
