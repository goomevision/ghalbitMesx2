package com.ghalbitnet.meshx2.diagnostics.recovery

object ErrorClassifier {
    fun classify(signal: RecoverySignal): RecoveryIssue? {
        val normalized = signal.value.trim().uppercase()
        val key = signal.key.trim().lowercase()

        return when {
            key == "server.baseurl" && normalized.isBlank() ->
                RecoveryIssue(RecoveryErrorType.SERVER_BASE_URL_MISSING, signal.key, "baseUrl missing", RecoverySeverity.HIGH)
            key == "server.status" && normalized.contains("SERVER_NOT_CONFIGURED") ->
                RecoveryIssue(RecoveryErrorType.SERVER_BASE_URL_MISSING, signal.key, signal.value, RecoverySeverity.HIGH)
            key == "server.status" && normalized.contains("AUTH_REQUIRED") ->
                RecoveryIssue(RecoveryErrorType.SERVER_AUTH, signal.key, signal.value, RecoverySeverity.HIGH)
            key == "server.status" && normalized.contains("ENDPOINT_MISSING") ->
                RecoveryIssue(RecoveryErrorType.SERVER_ENDPOINT_MISSING, signal.key, signal.value, RecoverySeverity.HIGH)
            key == "server.status" && normalized.contains("SERVER_ERROR") ->
                RecoveryIssue(RecoveryErrorType.SERVER_INTERNAL_ERROR, signal.key, signal.value, RecoverySeverity.HIGH)
            key == "server.status" && normalized.contains("SERVER_DOWN") ->
                RecoveryIssue(RecoveryErrorType.ECONNREFUSED, signal.key, signal.value, RecoverySeverity.HIGH)
            key == "server.http" && normalized == "401" ->
                RecoveryIssue(RecoveryErrorType.SERVER_AUTH, signal.key, "unauthorized", RecoverySeverity.HIGH)
            key == "server.http" && normalized == "403" ->
                RecoveryIssue(RecoveryErrorType.SERVER_AUTH, signal.key, "forbidden", RecoverySeverity.HIGH)
            key == "server.http" && normalized == "404" ->
                RecoveryIssue(RecoveryErrorType.SERVER_ENDPOINT_MISSING, signal.key, "endpoint missing", RecoverySeverity.HIGH)
            key == "server.http" && normalized == "500" ->
                RecoveryIssue(RecoveryErrorType.SERVER_INTERNAL_ERROR, signal.key, "server 500", RecoverySeverity.HIGH)
            key == "server.error" && normalized.contains("TIMEOUT") ->
                RecoveryIssue(RecoveryErrorType.SERVER_TIMEOUT, signal.key, signal.value, RecoverySeverity.MEDIUM)
            key == "server.error" && normalized.contains("ECONNREFUSED") ->
                RecoveryIssue(RecoveryErrorType.ECONNREFUSED, signal.key, signal.value, RecoverySeverity.HIGH)
            key == "network.error" && normalized.contains("ROUTE_NOT_FOUND") ->
                RecoveryIssue(RecoveryErrorType.ROUTE_NOT_FOUND, signal.key, signal.value, RecoverySeverity.HIGH)
            key == "network.routelock" && normalized.contains("SPAM") ->
                RecoveryIssue(RecoveryErrorType.ROUTE_LOCK_SPAM, signal.key, signal.value, RecoverySeverity.MEDIUM)
            key == "network.peer" && normalized.contains("STALE") ->
                RecoveryIssue(RecoveryErrorType.PEER_STALE, signal.key, signal.value, RecoverySeverity.MEDIUM)
            key == "network.reconnect" && normalized.contains("FAILED") ->
                RecoveryIssue(RecoveryErrorType.RECONNECT_FAILED, signal.key, signal.value, RecoverySeverity.MEDIUM)
            key == "pending.queue" && normalized.contains("STUCK") ->
                RecoveryIssue(RecoveryErrorType.PENDING_QUEUE_STUCK, signal.key, signal.value, RecoverySeverity.MEDIUM)
            key == "chat.message" && normalized.contains("DUPLICATE") ->
                RecoveryIssue(RecoveryErrorType.DUPLICATE_MESSAGE_ID, signal.key, signal.value, RecoverySeverity.MEDIUM)
            key == "chat.failed" && normalized.contains("BEFORE_TTL") ->
                RecoveryIssue(RecoveryErrorType.FAILED_BEFORE_TTL, signal.key, signal.value, RecoverySeverity.HIGH)
            key == "chat.pending" && normalized.contains("TOO_LONG") ->
                RecoveryIssue(RecoveryErrorType.PENDING_TOO_LONG, signal.key, signal.value, RecoverySeverity.MEDIUM)
            key == "receipt.state" && normalized.contains("MISMATCH") ->
                RecoveryIssue(RecoveryErrorType.RECEIPT_MISMATCH, signal.key, signal.value, RecoverySeverity.MEDIUM)
            key == "media.upload" && normalized.contains("FAILED") ->
                RecoveryIssue(RecoveryErrorType.MEDIA_UPLOAD_FAILED, signal.key, signal.value, RecoverySeverity.MEDIUM)
            key == "media.download" && normalized.contains("FAILED") ->
                RecoveryIssue(RecoveryErrorType.MEDIA_DOWNLOAD_FAILED, signal.key, signal.value, RecoverySeverity.MEDIUM)
            key == "call.state" && normalized.contains("STUCK_RINGING") ->
                RecoveryIssue(RecoveryErrorType.CALL_STUCK_RINGING, signal.key, signal.value, RecoverySeverity.HIGH)
            key == "call.state" && normalized.contains("ACCEPT_NOT_CONNECTED") ->
                RecoveryIssue(RecoveryErrorType.CALL_ACCEPT_NOT_CONNECTED, signal.key, signal.value, RecoverySeverity.HIGH)
            key == "call.audio" && normalized.contains("RX_GT0_PLAYED_0") ->
                RecoveryIssue(RecoveryErrorType.CALL_RX_WITHOUT_PLAYBACK, signal.key, signal.value, RecoverySeverity.HIGH)
            key == "call.ringtone" && normalized.contains("STUCK") ->
                RecoveryIssue(RecoveryErrorType.CALL_RINGTONE_STUCK, signal.key, signal.value, RecoverySeverity.MEDIUM)
            key == "audio.input" && normalized.contains("SILENCE") ->
                RecoveryIssue(RecoveryErrorType.AUDIO_MIC_SILENCE, signal.key, signal.value, RecoverySeverity.MEDIUM)
            key == "audio.input" && normalized.contains("CLIPPING") ->
                RecoveryIssue(RecoveryErrorType.AUDIO_CLIPPING, signal.key, signal.value, RecoverySeverity.MEDIUM)
            key == "audio.output" && normalized.contains("UNDERRUN") ->
                RecoveryIssue(RecoveryErrorType.AUDIO_SPEAKER_UNDERRUN, signal.key, signal.value, RecoverySeverity.MEDIUM)
            key == "audio.loopback" && normalized.contains("FAILED") ->
                RecoveryIssue(RecoveryErrorType.AUDIO_LOOPBACK_FAILED, signal.key, signal.value, RecoverySeverity.HIGH)
            else -> null
        }
    }
}
