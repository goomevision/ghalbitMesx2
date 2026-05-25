package com.ghalbitnet.meshx2.chat

import android.content.Context
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.network.HybridConnectivityPlanner
import com.ghalbitnet.meshx2.core.server.FirebaseRemoteSyncManager
import com.ghalbitnet.meshx2.model.MeshNode

object RemoteModeManager {

    enum class ServerState {
        DISABLED,
        READY_TO_CONNECT,
        CONNECTED_PLACEHOLDER,
        CONNECTION_ISSUE
    }

    private const val PREFS_NAME = "remote_mode_prefs"
    private const val KEY_ENABLED = "remote_mode_enabled"

    data class Snapshot(
        val enabled: Boolean,
        val state: ServerState,
        val title: String,
        val description: String
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(
        context: Context,
        enabled: Boolean
    ) {
        prefs(context)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun toggle(context: Context): Boolean {
        val next = !isEnabled(context)
        setEnabled(context, next)
        return next
    }

    fun snapshot(
        context: Context,
        nodes: List<MeshNode>
    ): Snapshot {
        val enabled = isEnabled(context)
        val hybrid = HybridConnectivityPlanner.snapshot(context, nodes)

        if (!enabled) {
            return Snapshot(
                enabled = false,
                state = ServerState.DISABLED,
                title = context.getString(R.string.remote_server_disabled),
                description = context.getString(R.string.remote_server_disabled_desc)
            )
        }

        if (!hybrid.internetReady) {
            return Snapshot(
                enabled = true,
                state = ServerState.CONNECTION_ISSUE,
                title = context.getString(R.string.remote_server_issue),
                description = context.getString(R.string.remote_server_internet_needed_desc)
            )
        }

        if (!FirebaseRemoteSyncManager.isReady(context)) {
            return Snapshot(
                enabled = true,
                state = ServerState.READY_TO_CONNECT,
                title = context.getString(R.string.remote_server_ready),
                description = context.getString(R.string.remote_server_waiting_firebase_desc)
            )
        }

        if (FirebaseRemoteSyncManager.lastSuccess(context) > 0L) {
            return Snapshot(
                enabled = true,
                state = ServerState.CONNECTED_PLACEHOLDER,
                title = context.getString(R.string.remote_server_connected_firebase),
                description = context.getString(
                    R.string.remote_server_connected_firebase_desc,
                    FirebaseRemoteSyncManager.remoteOnlineIds(context).size
                )
            )
        }

        if (hybrid.remoteReady) {
            return Snapshot(
                enabled = true,
                state = ServerState.READY_TO_CONNECT,
                title = context.getString(R.string.remote_server_ready),
                description = context.getString(R.string.remote_server_waiting_sync_desc)
            )
        }

        return Snapshot(
            enabled = true,
            state = ServerState.CONNECTION_ISSUE,
            title = context.getString(R.string.remote_server_issue),
            description = context.getString(R.string.remote_server_failed_firebase_desc)
        )
    }
}
