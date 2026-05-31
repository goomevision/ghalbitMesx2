package com.ghalbitnet.meshx2.activityfeed

enum class ActivityFeedType {
    CHAT_SENT,
    CHAT_RECEIVED,
    FILE_SENT,
    FILE_RECEIVED,
    CALL_STARTED,
    CALL_ENDED,
    CALL_MISSED,
    SOS_SENT,
    SOS_RECEIVED,
    WALLET_TRANSFER,
    WALLET_MINT,
    WALLET_BURN,
    NODE_ONLINE,
    NODE_OFFLINE,
    PEER_VERIFIED,
    PEER_LOST,
    SYNC_SUCCESS,
    SYNC_FAILED,
    SECURITY_EVENT,
    RUNTIME_EVENT
}

enum class ActivityFeedCategory {
    ALL,
    CHAT,
    FILE,
    CALL,
    SOS,
    WALLET,
    NETWORK,
    SECURITY,
    RUNTIME
}

fun ActivityFeedType.category(): ActivityFeedCategory =
    when (this) {
        ActivityFeedType.CHAT_SENT,
        ActivityFeedType.CHAT_RECEIVED -> ActivityFeedCategory.CHAT

        ActivityFeedType.FILE_SENT,
        ActivityFeedType.FILE_RECEIVED -> ActivityFeedCategory.FILE

        ActivityFeedType.CALL_STARTED,
        ActivityFeedType.CALL_ENDED,
        ActivityFeedType.CALL_MISSED -> ActivityFeedCategory.CALL

        ActivityFeedType.SOS_SENT,
        ActivityFeedType.SOS_RECEIVED -> ActivityFeedCategory.SOS

        ActivityFeedType.WALLET_TRANSFER,
        ActivityFeedType.WALLET_MINT,
        ActivityFeedType.WALLET_BURN -> ActivityFeedCategory.WALLET

        ActivityFeedType.NODE_ONLINE,
        ActivityFeedType.NODE_OFFLINE,
        ActivityFeedType.PEER_VERIFIED,
        ActivityFeedType.PEER_LOST,
        ActivityFeedType.SYNC_SUCCESS,
        ActivityFeedType.SYNC_FAILED -> ActivityFeedCategory.NETWORK

        ActivityFeedType.SECURITY_EVENT -> ActivityFeedCategory.SECURITY
        ActivityFeedType.RUNTIME_EVENT -> ActivityFeedCategory.RUNTIME
    }
