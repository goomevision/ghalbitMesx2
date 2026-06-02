package com.ghalbitnet.meshx2.diagnostics.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ghalbitnet.meshx2.BuildConfig
import com.ghalbitnet.meshx2.chat.ChatDeliveryManager
import com.ghalbitnet.meshx2.diagnostics.VirtualPeerCallSignalProbe
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
        const val ACTION_RUN_VIRTUAL_CHAT_READ = "com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CHAT_READ"
        const val ACTION_RUN_VIRTUAL_CALL_SERVER_START = "com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_START"
        const val ACTION_RUN_VIRTUAL_CALL_SERVER_END = "com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_SERVER_END"

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
                    ACTION_RUN_VIRTUAL_CALL_SERVER_END -> {
                        Log.i("GHALBIT-DEBUG-TRIGGER", "DISPATCH target=virtual_call_server_end")
                        val result = VirtualPeerCallSignalProbe.end(context.applicationContext)
                        Log.i(
                            "GHALBIT-DEBUG-TRIGGER",
                            "RESULT status=${result.status} callId=${result.callId} code=${result.httpCode} inboxMessages=${result.syncMessages} inboxReceipts=${result.syncReceipts}"
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
