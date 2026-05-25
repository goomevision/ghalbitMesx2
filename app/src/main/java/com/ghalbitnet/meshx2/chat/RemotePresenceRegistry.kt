package com.ghalbitnet.meshx2.chat

import android.content.Context
import android.text.format.DateUtils
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.core.network.HybridConnectivityPlanner
import com.ghalbitnet.meshx2.core.server.FirebaseBootstrapHandshakeManager
import com.ghalbitnet.meshx2.core.server.FirebaseBootstrapPeerManager
import com.ghalbitnet.meshx2.core.server.FirebaseRemoteSyncManager
import com.ghalbitnet.meshx2.core.server.MeshServerApiClient
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.security.KeyStoreManager

object RemotePresenceRegistry {

    private const val PREFS_NAME = "remote_presence_registry"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_TRACKED_COUNT = "tracked_count"

    data class Snapshot(
        val lastSync: Long,
        val trackedCount: Int,
        val active: Boolean,
        val onlineCount: Int
    ) {
        fun lastSyncLabel(context: Context): String {
            return if (lastSync <= 0L) {
                context.getString(R.string.remote_presence_never_synced)
            } else {
                context.getString(
                    R.string.remote_presence_last_sync,
                    DateUtils.getRelativeTimeSpanString(
                        lastSync,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    )
                )
            }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun refresh(
        context: Context,
        nodes: List<MeshNode>
    ): Snapshot {
        val hybrid =
            HybridConnectivityPlanner.snapshot(context, nodes)
        val remoteCount =
            GlobalContactDirectory.getAll(context).size
        val active =
            RemoteModeManager.isEnabled(context) && hybrid.internetReady
        val trackedCount =
            if (active) remoteCount else 0
        val lastSync =
            System.currentTimeMillis()
        val serverOnlineCount =
            if (active) {
                val keyStore = KeyStoreManager(context)
                val globalId = GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)
                val firebaseResult =
                    if (FirebaseRemoteSyncManager.isReady(context)) {
                        FirebaseRemoteSyncManager.syncPresenceAndPolicies(
                            context = context,
                            globalId = globalId,
                            nodes = nodes,
                            contactCount = remoteCount,
                            remoteModeEnabled = true
                        )
                    } else {
                        null
                    }
                when {
                    firebaseResult?.success == true -> firebaseResult.remoteOnlineCount
                    else ->
                        MeshServerApiClient.sendHeartbeat(
                            context = context,
                            globalId = globalId,
                            nodes = nodes,
                            contactCount = remoteCount,
                            remoteModeEnabled = true
                        ).takeIf { it.success }?.remoteOnlineCount
                            ?: simulatedOnlineCount(context, lastSync, active)
                }
            } else {
                0
            }

        if (active) {
            runCatching {
                val bootstrapPeers = FirebaseBootstrapPeerManager.refresh(context)
                val keyStore = KeyStoreManager(context)
                val globalId = GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)
                FirebaseBootstrapHandshakeManager.refresh(context, globalId, bootstrapPeers)
            }
        }

        prefs(context)
            .edit()
            .putLong(KEY_LAST_SYNC, lastSync)
            .putInt(KEY_TRACKED_COUNT, trackedCount)
            .apply()

        return Snapshot(
            lastSync = lastSync,
            trackedCount = trackedCount,
            active = active,
            onlineCount = serverOnlineCount
        )
    }

    fun snapshot(context: Context): Snapshot {
        return Snapshot(
            lastSync = prefs(context).getLong(KEY_LAST_SYNC, 0L),
            trackedCount = prefs(context).getInt(KEY_TRACKED_COUNT, 0),
            active = RemoteModeManager.isEnabled(context),
            onlineCount = simulatedOnlineCount(
                context,
                prefs(context).getLong(KEY_LAST_SYNC, 0L),
                RemoteModeManager.isEnabled(context)
            )
        )
    }

    fun contactState(
        context: Context,
        nodes: List<MeshNode>,
        globalId: String
    ): RemotePresencePlanner.ContactState {
        val hybrid =
            HybridConnectivityPlanner.snapshot(context, nodes)
        val enabled =
            RemoteModeManager.isEnabled(context)
        val snapshot =
            snapshot(context)

        return when {
            !enabled -> RemotePresencePlanner.ContactState(
                label = context.getString(R.string.saved_contacts_status_server_wait),
                detail = context.getString(R.string.remote_presence_disabled_desc)
            )

            !hybrid.internetReady -> RemotePresencePlanner.ContactState(
                label = context.getString(R.string.saved_contacts_status_wait_internet),
                detail = context.getString(R.string.saved_contacts_status_wait_internet_desc)
            )

            snapshot.lastSync > 0L -> {
                val serverOnlineIds =
                    FirebaseRemoteSyncManager.remoteOnlineIds(context)
                        .ifEmpty { MeshServerApiClient.remoteOnlineIds(context) }
                when {
                    serverOnlineIds.contains(globalId) -> RemotePresencePlanner.ContactState(
                        label = context.getString(R.string.saved_contacts_status_remote_online),
                        detail = context.getString(R.string.saved_contacts_status_remote_online_desc)
                    )

                    serverOnlineIds.isNotEmpty() -> RemotePresencePlanner.ContactState(
                        label = context.getString(R.string.saved_contacts_status_remote_waiting),
                        detail = context.getString(R.string.saved_contacts_status_remote_waiting_desc)
                    )

                    else -> simulatedContactState(
                        context = context,
                        globalId = globalId,
                        lastSync = snapshot.lastSync
                    )
                }
            }

            else -> RemotePresencePlanner.ContactState(
                label = context.getString(R.string.saved_contacts_status_hybrid_ready),
                detail = context.getString(R.string.saved_contacts_status_hybrid_ready_desc)
            )
        }
    }

    private fun simulatedOnlineCount(
        context: Context,
        lastSync: Long,
        active: Boolean
    ): Int {
        if (!active || lastSync <= 0L) {
            return 0
        }

        return GlobalContactDirectory.getAll(context)
            .count {
                simulatedBucket(it.globalId, lastSync) == 0
            }
    }

    private fun simulatedContactState(
        context: Context,
        globalId: String,
        lastSync: Long
    ): RemotePresencePlanner.ContactState {
        return when (simulatedBucket(globalId, lastSync)) {
            0 -> RemotePresencePlanner.ContactState(
                label = context.getString(R.string.saved_contacts_status_remote_online),
                detail = context.getString(R.string.saved_contacts_status_remote_online_desc)
            )

            1 -> RemotePresencePlanner.ContactState(
                label = context.getString(R.string.saved_contacts_status_remote_standby),
                detail = context.getString(R.string.saved_contacts_status_remote_standby_desc)
            )

            else -> RemotePresencePlanner.ContactState(
                label = context.getString(R.string.saved_contacts_status_remote_waiting),
                detail = context.getString(R.string.saved_contacts_status_remote_waiting_desc)
            )
        }
    }

    private fun simulatedBucket(
        globalId: String,
        lastSync: Long
    ): Int {
        val minuteSeed =
            (lastSync / 60000L).toInt()
        val hash =
            kotlin.math.abs((globalId.hashCode() * 31) + minuteSeed)
        return hash % 3
    }
}
