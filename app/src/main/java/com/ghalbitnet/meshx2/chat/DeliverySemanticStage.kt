package com.ghalbitnet.meshx2.chat

enum class DeliverySemanticStage {
    CREATED,
    SENDING,
    SENT_TO_SERVER,
    DELIVERED_TO_DEVICE,
    READ_BY_USER,
    PENDING,
    FAILED_FINAL;

    companion object {
        fun fromState(state: ChatDeliveryState): DeliverySemanticStage =
            when (state) {
                ChatDeliveryState.DRAFT,
                ChatDeliveryState.DRAFT_TEXT,
                ChatDeliveryState.DRAFT_MEDIA,
                ChatDeliveryState.DRAFT_FILE,
                ChatDeliveryState.REVIEW_READY,
                ChatDeliveryState.SEND_CONFIRMED -> CREATED

                ChatDeliveryState.QUEUED,
                ChatDeliveryState.SENDING,
                ChatDeliveryState.MEDIA_UPLOADING,
                ChatDeliveryState.MEDIA_RESUMING -> SENDING

                ChatDeliveryState.ACCEPTED_BY_RELAY,
                ChatDeliveryState.SENT_INTERNET,
                ChatDeliveryState.QUEUED_REMOTE,
                ChatDeliveryState.MEDIA_QUEUED_REMOTE,
                ChatDeliveryState.SENT_LOCAL -> SENT_TO_SERVER

                ChatDeliveryState.DELIVERED,
                ChatDeliveryState.DELIVERED_REMOTE,
                ChatDeliveryState.MEDIA_DELIVERED_REMOTE -> DELIVERED_TO_DEVICE

                ChatDeliveryState.READ,
                ChatDeliveryState.READ_REMOTE,
                ChatDeliveryState.MEDIA_READ_REMOTE -> READ_BY_USER

                ChatDeliveryState.PENDING,
                ChatDeliveryState.WAITING_FOR_ROUTE,
                ChatDeliveryState.WAITING_FOR_PEER,
                ChatDeliveryState.QUEUED_LOCAL -> PENDING

                ChatDeliveryState.FAILED_FINAL,
                ChatDeliveryState.MEDIA_EXPIRED,
                ChatDeliveryState.EXPIRED_REMOTE -> FAILED_FINAL

                else -> PENDING
            }
    }
}
