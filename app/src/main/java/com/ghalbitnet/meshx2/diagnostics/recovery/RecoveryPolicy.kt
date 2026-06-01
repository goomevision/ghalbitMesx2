package com.ghalbitnet.meshx2.diagnostics.recovery

object RecoveryPolicy {
    fun decide(issue: RecoveryIssue): RecoveryAction {
        val action = when (issue.type) {
            RecoveryErrorType.SERVER_BASE_URL_MISSING ->
                RecoveryAction(RecoveryActionName.MARK_SERVER_PARTIAL, "serverPartial", applied = true)
            RecoveryErrorType.SERVER_TIMEOUT ->
                RecoveryAction(RecoveryActionName.RETRY_WITH_BACKOFF, "retryBackoff", applied = true)
            RecoveryErrorType.SERVER_AUTH,
            RecoveryErrorType.SERVER_ENDPOINT_MISSING,
            RecoveryErrorType.SERVER_INTERNAL_ERROR ->
                RecoveryAction(RecoveryActionName.MARK_SERVER_PARTIAL, "serverPartial", applied = true)
            RecoveryErrorType.ECONNREFUSED ->
                RecoveryAction(RecoveryActionName.COOLDOWN_FAILED_HOST, "hostCooldown30s", applied = true)
            RecoveryErrorType.ROUTE_NOT_FOUND ->
                RecoveryAction(RecoveryActionName.FALLBACK_PENDING_QUEUE, "pendingQueueFallback", applied = true)
            RecoveryErrorType.ROUTE_LOCK_SPAM ->
                RecoveryAction(RecoveryActionName.RELEASE_ROUTE_LOCK, "routeLockReleased", applied = true)
            RecoveryErrorType.PEER_STALE,
            RecoveryErrorType.RECONNECT_FAILED ->
                RecoveryAction(RecoveryActionName.REDISCOVERY_LIMITED, "rediscoveryLimited", applied = true)
            RecoveryErrorType.PENDING_QUEUE_STUCK ->
                RecoveryAction(RecoveryActionName.SAFE_RETRY, "safeRetryQueue", applied = true)
            RecoveryErrorType.DUPLICATE_MESSAGE_ID ->
                RecoveryAction(RecoveryActionName.DEDUP_MESSAGE, "dedupApplied", applied = true)
            RecoveryErrorType.FAILED_BEFORE_TTL ->
                RecoveryAction(RecoveryActionName.CONVERT_FAILED_TO_PENDING, "failedToPending", applied = true)
            RecoveryErrorType.PENDING_TOO_LONG ->
                RecoveryAction(RecoveryActionName.SAFE_RETRY, "retryPending", applied = true)
            RecoveryErrorType.RECEIPT_MISMATCH ->
                RecoveryAction(RecoveryActionName.RESEND_ACK_SAFE, "ackResendSafe", applied = true)
            RecoveryErrorType.MEDIA_UPLOAD_FAILED,
            RecoveryErrorType.MEDIA_DOWNLOAD_FAILED ->
                RecoveryAction(RecoveryActionName.SAFE_RETRY, "retryMedia", applied = true)
            RecoveryErrorType.CALL_STUCK_RINGING,
            RecoveryErrorType.CALL_RINGTONE_STUCK ->
                RecoveryAction(RecoveryActionName.STOP_RINGTONE, "ringtoneStopped", applied = true)
            RecoveryErrorType.CALL_ACCEPT_NOT_CONNECTED ->
                RecoveryAction(RecoveryActionName.FORCE_CALL_CLEANUP, "callCleanup", applied = true)
            RecoveryErrorType.CALL_RX_WITHOUT_PLAYBACK ->
                RecoveryAction(RecoveryActionName.ENABLE_SAFE_PLAYBACK, "safePlaybackEnabled", applied = true)
            RecoveryErrorType.AUDIO_MIC_SILENCE ->
                RecoveryAction(RecoveryActionName.MARK_AUDIO_NEEDS_USER_CHECK, "userCheckMic", applied = true)
            RecoveryErrorType.AUDIO_CLIPPING,
            RecoveryErrorType.AUDIO_SPEAKER_UNDERRUN ->
                RecoveryAction(RecoveryActionName.INCREASE_AUDIO_BUFFER, "audioBufferIncreased", applied = true)
            RecoveryErrorType.AUDIO_LOOPBACK_FAILED ->
                RecoveryAction(RecoveryActionName.FALLBACK_PTT, "fallbackPtt", applied = true, requiresHumanApproval = false)
        }
        return action
    }
}

