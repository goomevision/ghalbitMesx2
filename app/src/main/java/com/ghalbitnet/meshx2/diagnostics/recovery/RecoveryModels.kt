package com.ghalbitnet.meshx2.diagnostics.recovery

enum class RecoverySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class RecoveryErrorType {
    SERVER_BASE_URL_MISSING,
    SERVER_TIMEOUT,
    SERVER_AUTH,
    SERVER_ENDPOINT_MISSING,
    SERVER_INTERNAL_ERROR,
    ECONNREFUSED,
    ROUTE_NOT_FOUND,
    ROUTE_LOCK_SPAM,
    PEER_STALE,
    RECONNECT_FAILED,
    PENDING_QUEUE_STUCK,
    DUPLICATE_MESSAGE_ID,
    FAILED_BEFORE_TTL,
    PENDING_TOO_LONG,
    RECEIPT_MISMATCH,
    MEDIA_UPLOAD_FAILED,
    MEDIA_DOWNLOAD_FAILED,
    CALL_STUCK_RINGING,
    CALL_ACCEPT_NOT_CONNECTED,
    CALL_RX_WITHOUT_PLAYBACK,
    CALL_RINGTONE_STUCK,
    AUDIO_MIC_SILENCE,
    AUDIO_CLIPPING,
    AUDIO_SPEAKER_UNDERRUN,
    AUDIO_LOOPBACK_FAILED
}

data class RecoveryIssue(
    val type: RecoveryErrorType,
    val source: String,
    val detail: String,
    val severity: RecoverySeverity
)

enum class RecoveryActionName {
    RETRY_WITH_BACKOFF,
    MARK_SERVER_PARTIAL,
    RELEASE_ROUTE_LOCK,
    COOLDOWN_FAILED_HOST,
    REDISCOVERY_LIMITED,
    FALLBACK_PENDING_QUEUE,
    CONVERT_FAILED_TO_PENDING,
    SAFE_RETRY,
    DEDUP_MESSAGE,
    RESEND_ACK_SAFE,
    STOP_RINGTONE,
    FORCE_CALL_CLEANUP,
    ENABLE_SAFE_PLAYBACK,
    FALLBACK_PTT,
    INCREASE_AUDIO_BUFFER,
    MARK_AUDIO_NEEDS_USER_CHECK,
    SKIP
}

data class RecoveryAction(
    val name: RecoveryActionName,
    val result: String,
    val applied: Boolean,
    val requiresHumanApproval: Boolean = false
)

data class AutoFixSuggestion(
    val error: RecoveryErrorType,
    val file: String,
    val reason: String,
    val safePatch: String,
    val risk: String,
    val humanApprovalRequired: Boolean
)

data class RecoveryRunResult(
    val issues: List<RecoveryIssue>,
    val actions: List<RecoveryAction>,
    val suggestions: List<AutoFixSuggestion>,
    val recovered: Int,
    val pending: Int,
    val failed: Int
)

data class RecoverySignal(
    val key: String,
    val value: String
)

