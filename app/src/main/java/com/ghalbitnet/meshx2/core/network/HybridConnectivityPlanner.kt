package com.ghalbitnet.meshx2.core.network

import android.content.Context
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.model.MeshNode

object HybridConnectivityPlanner {

    data class Snapshot(
        val title: String,
        val description: String,
        val localReady: Boolean,
        val internetReady: Boolean,
        val remoteReady: Boolean
    )

    fun snapshot(
        context: Context,
        nodes: List<MeshNode>
    ): Snapshot {
        val connectivity =
            ConnectivityStatusDetector.snapshot(context, nodes)

        val localReady = connectivity.hasLocal
        val internetReady = connectivity.hasInternet

        return when {
            localReady && internetReady -> Snapshot(
                title = context.getString(R.string.hybrid_mode_ready),
                description = context.getString(R.string.hybrid_mode_ready_desc),
                localReady = true,
                internetReady = true,
                remoteReady = true
            )

            localReady -> Snapshot(
                title = context.getString(R.string.hybrid_mode_local_only),
                description = context.getString(R.string.hybrid_mode_local_only_desc),
                localReady = true,
                internetReady = false,
                remoteReady = false
            )

            internetReady -> Snapshot(
                title = context.getString(R.string.hybrid_mode_internet_ready),
                description = context.getString(R.string.hybrid_mode_internet_ready_desc),
                localReady = false,
                internetReady = true,
                remoteReady = true
            )

            else -> Snapshot(
                title = context.getString(R.string.hybrid_mode_offline),
                description = context.getString(R.string.hybrid_mode_offline_desc),
                localReady = false,
                internetReady = false,
                remoteReady = false
            )
        }
    }
}
