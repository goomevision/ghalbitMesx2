package com.ghalbitnet.meshx2.diagnostics.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ghalbitnet.meshx2.BuildConfig
import com.ghalbitnet.meshx2.chat.ChatDeliveryManager
import com.ghalbitnet.meshx2.diagnostics.VirtualPeerCallSignalProbe
import com.ghalbitnet.meshx2.diagnostics.VirtualPeerChatProbe
import com.ghalbitnet.meshx2.diagnostics.VirtualPeerInboxProbe
import com.ghalbitnet.meshx2.diagnostics.VirtualPeerOutboundCallProbe
import com.ghalbitnet.meshx2.diagnostics.VirtualPeerPresenceProbe
import com.ghalbitnet.meshx2.diagnostics.audio.AudioTruthProbe
import com.ghalbitnet.meshx2.diagnostics.autodiag.AutoDiagnosticOrchestrator
import com.ghalbitnet.meshx2.diagnostics.virtualcall.OneDeviceIncomingCallDiagnostic
import com.ghalbitnet.meshx2.diagnostics.virtualcall.VirtualCallScenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DiagnosticDebugReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RUN_VIRTUAL_CALL_CHECK = "com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_CHECK"
        const val ACTION_RUN_FULL_DIAGNOSTIC = "com.ghalbitnet.meshx2.debug.RUN_FULL_DIAGNOSTIC"
        const val ACTION_RUN_AUDIO_TRUTH = "com.ghalbitnet.meshx2.debug.RUN_AUDIO_TRUTH"
        const val ACTION_RUN_SERVER_PRESENCE_CHECK = "com.ghalbitnet.meshx2.debug.RUN_SERVER_PRESENCE_CHECK"
        const val ACTION_RUN_RELAY_INBOX_SYNC = "com.ghalbitnet.meshx2.debug.RUN_RELAY_INBOX_SYNC"
        const val ACTION_RUN_VIRTUAL_CHAT_SERVER_SEND = "com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CHAT_SERVER_SEND"
        const val ACTION_RUN_VIRTUAL_CHAT_READ = "com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CHAT_READ"
        const val ACTION_RUN_VIRTUAL_CALL_SERVER_START = "com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_START"
        const val ACTION_RUN_VIRTUAL_CALL_SERVER_RINGING = "com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_RINGING"
        const val ACTION_RUN_VIRTUAL_CALL_SERVER_ACCEPT = "com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_ACCEPT"
        const val ACTION_RUN_VIRTUAL_CALL_SERVER_REJECT = "com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_REJECT"
        const val ACTION_RUN_VIRTUAL_CALL_SERVER_END = "com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_END"
        const val ACTION_RUN_VIRTUAL_CALL_SERVER_FULL_ACCEPT = "com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_FULL_ACCEPT"
        const val ACTION_RUN_VIRTUAL_CALL_SERVER_FULL_REJECT = "com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_FULL_REJECT"
        const val ACTION_RUN_OUTBOUND_CALL_TO_VIRTUAL_PEER = "com.ghalbitnet.meshx2.debug.RUN_OUTBOUND_CALL_TO_VIRTUAL_PEER"
        const val ACTION_RUN_VIRTUAL_PEER_INBOX_CHECK = "com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_PEER_INBOX_CHECK"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action.orEmpty()
        if (!BuildConfig.DEBUG) {
            Log.w("GHALBIT-DEBUG-TRIGGER", "DENIED reason=not_debug_build")
            return
        }
        if (action.isBlank()) {
            Log.w("GHALBIT-DEBUG-TRIGGER", "DENIED reason=blank_action")
            return
        }
        Log.i("GHALBIT-DEBUG-TRIGGER", "RECEIVED action=$action")
        val pendingResult = goAsync()
        scope.launch {
            try {
                when (action) {
                    ACTION_RUN_VIRTUAL_CALL_CHECK -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=virtual_call")
                        Log.i("GHALBIT-VIRTUAL-CALL", "TRIGGER_RECEIVED source=adb_debug")
                        val result = OneDeviceIncomingCallDiagnostic.run(
                            context.applicationContext,
                            VirtualCallScenario(
                                callerPeerId = "VIRTUAL_CALLER_PC",
                                callerGlobalId = "GX-VIRTUAL-CALLER",
                                callerDisplayName = "Virtual Caller Tool"
                            ),
                            triggerSource = "adb_debug"
                        )
                        Log.i("GHALBIT-DEBUG-TRIGGER", "RESULT status=${result.status}")
                    }
                    ACTION_RUN_FULL_DIAGNOSTIC -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=full_diagnostic")
                        val result = AutoDiagnosticOrchestrator.run(context.applicationContext)
                        Log.i("GHALBIT-DEBUG-TRIGGER", "RESULT status=${result.status}")
                    }
                    ACTION_RUN_AUDIO_TRUTH -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=audio_truth")
                        val result = AudioTruthProbe.run(context.applicationContext)
                        Log.i("GHALBIT-DEBUG-TRIGGER", "RESULT status=audio_${result.healthScore}")
                    }
                    ACTION_RUN_SERVER_PRESENCE_CHECK -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=server_presence")
                        val result = VirtualPeerPresenceProbe.run(context.applicationContext)
                        Log.i(
                            "GHALBIT-DEBUG-TRIGGER",
                            "RESULT status=${result.status} register=${result.registerOk} heartbeat=${result.heartbeatOk} lookup=${result.lookupOk} lastSeen=${result.lastSeen}"
                        )
                    }
                    ACTION_RUN_RELAY_INBOX_SYNC -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=relay_inbox_sync")
                        val result = ChatDeliveryManager.syncNow(context.applicationContext, reason = "debug-relay-inbox-sync")
                        Log.i(
                            "GHALBIT-DEBUG-TRIGGER",
                            "RESULT status=relay_inbox_sync inboxMessages=${result.inboxMessages} inboxReceipts=${result.inboxReceipts} pendingMessagesRetried=${result.pendingMessagesRetried} pendingMediaRetried=${result.pendingMediaRetried}"
                        )
                    }
                    ACTION_RUN_VIRTUAL_CHAT_SERVER_SEND -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=virtual_chat_server_send")
                        val result = VirtualPeerChatProbe.send(
                            context.applicationContext,
                            message = intent?.getStringExtra("message").orEmpty().ifBlank { "Halo dari Virtual HP B" }
                        )
                        Log.i(
                            "GHALBIT-DEBUG-TRIGGER",
                            "RESULT status=${result.status} packetId=${result.packetId} http=${result.httpCode} inboxMessages=${result.syncMessages} inboxReceipts=${result.syncReceipts}"
                        )
                    }
                    ACTION_RUN_VIRTUAL_CHAT_READ -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=virtual_chat_read")
                        ChatDeliveryManager.markAnyChatReadRemotely(
                            context.applicationContext,
                            listOf("Virtual HP B", "GX-VIRTUAL-HP-B"),
                            "GX-VIRTUAL-HP-B"
                        )
                        Log.i("GHALBIT-DEBUG-TRIGGER", "RESULT status=virtual_chat_read_requested")
                    }
                    ACTION_RUN_VIRTUAL_CALL_SERVER_START -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=virtual_call_server_start")
                        val result = VirtualPeerCallSignalProbe.start(context.applicationContext)
                        Log.i(
                            "GHALBIT-DEBUG-TRIGGER",
                            "RESULT status=${result.status} callId=${result.callId} code=${result.httpCode} inboxMessages=${result.syncMessages} inboxReceipts=${result.syncReceipts}"
                        )
                    }
                    ACTION_RUN_VIRTUAL_CALL_SERVER_RINGING -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=virtual_call_server_ringing")
                        val result = VirtualPeerCallSignalProbe.ringing(context.applicationContext)
                        Log.i(
                            "GHALBIT-DEBUG-TRIGGER",
                            "RESULT status=${result.status} callId=${result.callId} code=${result.httpCode} inboxMessages=${result.syncMessages} inboxReceipts=${result.syncReceipts}"
                        )
                    }
                    ACTION_RUN_VIRTUAL_CALL_SERVER_ACCEPT -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=virtual_call_server_accept")
                        val result = VirtualPeerCallSignalProbe.accept(context.applicationContext)
                        Log.i(
                            "GHALBIT-DEBUG-TRIGGER",
                            "RESULT status=${result.status} callId=${result.callId} code=${result.httpCode} inboxMessages=${result.syncMessages} inboxReceipts=${result.syncReceipts}"
                        )
                    }
                    ACTION_RUN_VIRTUAL_CALL_SERVER_REJECT -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=virtual_call_server_reject")
                        val result = VirtualPeerCallSignalProbe.reject(context.applicationContext)
                        Log.i(
                            "GHALBIT-DEBUG-TRIGGER",
                            "RESULT status=${result.status} callId=${result.callId} code=${result.httpCode} inboxMessages=${result.syncMessages} inboxReceipts=${result.syncReceipts}"
                        )
                    }
                    ACTION_RUN_VIRTUAL_CALL_SERVER_END -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=virtual_call_server_end")
                        val result = VirtualPeerCallSignalProbe.end(context.applicationContext)
                        Log.i(
                            "GHALBIT-DEBUG-TRIGGER",
                            "RESULT status=${result.status} callId=${result.callId} code=${result.httpCode} inboxMessages=${result.syncMessages} inboxReceipts=${result.syncReceipts}"
                        )
                    }
                    ACTION_RUN_VIRTUAL_CALL_SERVER_FULL_ACCEPT -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=virtual_call_server_full_accept")
                        val result = VirtualPeerCallSignalProbe.fullAcceptFlow(context.applicationContext)
                        Log.i(
                            "GHALBIT-DEBUG-TRIGGER",
                            "RESULT status=${result.status} callId=${result.callId} steps=${result.steps.joinToString("|") { it.status }}"
                        )
                    }
                    ACTION_RUN_VIRTUAL_CALL_SERVER_FULL_REJECT -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=virtual_call_server_full_reject")
                        val result = VirtualPeerCallSignalProbe.fullRejectFlow(context.applicationContext)
                        Log.i(
                            "GHALBIT-DEBUG-TRIGGER",
                            "RESULT status=${result.status} callId=${result.callId} steps=${result.steps.joinToString("|") { it.status }}"
                        )
                    }
                    ACTION_RUN_OUTBOUND_CALL_TO_VIRTUAL_PEER -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=outbound_call_virtual_peer")
                        val result = VirtualPeerOutboundCallProbe.launchServerFirstOutgoingCall(context.applicationContext)
                        Log.i(
                            "GHALBIT-DEBUG-TRIGGER",
                            "RESULT status=${result.status} callId=${result.callId} route=${result.routeLabel} target=${result.targetGlobalId}"
                        )
                    }
                    ACTION_RUN_VIRTUAL_PEER_INBOX_CHECK -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=virtual_peer_inbox")
                        val result = VirtualPeerInboxProbe.run(
                            context.applicationContext,
                            targetGlobalId = intent?.getStringExtra("targetGlobalId").orEmpty().ifBlank {
                                "GX-VIRTUAL-HP-B"
                            }
                        )
                        Log.i(
                            "GHALBIT-DEBUG-TRIGGER",
                            "RESULT status=virtual_peer_inbox globalId=${result.globalId} messages=${result.messages} receipts=${result.receipts} callSignals=${result.callSignals}"
                        )
                    }
                    else -> {
                        Log.w("GHALBIT-DEBUG-TRIGGER", "DENIED reason=unknown_action")
                    }
                }
            } catch (t: Throwable) {
                Log.e("GHALBIT-DEBUG-TRIGGER", "RESULT status=failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
