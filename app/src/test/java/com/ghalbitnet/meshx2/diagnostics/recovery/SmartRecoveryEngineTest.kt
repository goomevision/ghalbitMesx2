package com.ghalbitnet.meshx2.diagnostics.recovery

import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRecoveryEngineTest {

    @Test
    fun detects_server_baseurl_missing() {
        val result = SmartRecoveryEngine.runWithSignals(
            signals = listOf(RecoverySignal("server.baseUrl", ""))
        )
        assertTrue(result.issues.any { it.type == RecoveryErrorType.SERVER_BASE_URL_MISSING })
    }

    @Test
    fun detects_econnrefused() {
        val result = SmartRecoveryEngine.runWithSignals(
            signals = listOf(RecoverySignal("server.error", "ECONNREFUSED"))
        )
        assertTrue(result.issues.any { it.type == RecoveryErrorType.ECONNREFUSED })
    }

    @Test
    fun detects_duplicate_message() {
        val result = SmartRecoveryEngine.runWithSignals(
            signals = listOf(RecoverySignal("chat.message", "DUPLICATE"))
        )
        assertTrue(result.issues.any { it.type == RecoveryErrorType.DUPLICATE_MESSAGE_ID })
    }

    @Test
    fun detects_failed_before_ttl() {
        val result = SmartRecoveryEngine.runWithSignals(
            signals = listOf(RecoverySignal("chat.failed", "BEFORE_TTL"))
        )
        assertTrue(result.issues.any { it.type == RecoveryErrorType.FAILED_BEFORE_TTL })
    }

    @Test
    fun detects_stuck_ringing() {
        val result = SmartRecoveryEngine.runWithSignals(
            signals = listOf(RecoverySignal("call.state", "STUCK_RINGING"))
        )
        assertTrue(result.issues.any { it.type == RecoveryErrorType.CALL_STUCK_RINGING })
    }

    @Test
    fun detects_rx_without_playback() {
        val result = SmartRecoveryEngine.runWithSignals(
            signals = listOf(RecoverySignal("call.audio", "RX_GT0_PLAYED_0"))
        )
        assertTrue(result.issues.any { it.type == RecoveryErrorType.CALL_RX_WITHOUT_PLAYBACK })
    }

    @Test
    fun detects_route_lock_spam() {
        val result = SmartRecoveryEngine.runWithSignals(
            signals = listOf(RecoverySignal("network.routeLock", "SPAM"))
        )
        assertTrue(result.issues.any { it.type == RecoveryErrorType.ROUTE_LOCK_SPAM })
    }

    @Test
    fun detects_loop_guard_like_stuck_queue() {
        val result = SmartRecoveryEngine.runWithSignals(
            signals = listOf(RecoverySignal("pending.queue", "STUCK"))
        )
        assertTrue(result.issues.any { it.type == RecoveryErrorType.PENDING_QUEUE_STUCK })
    }
}
