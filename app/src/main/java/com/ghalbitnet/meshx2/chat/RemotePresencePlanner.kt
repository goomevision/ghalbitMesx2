package com.ghalbitnet.meshx2.chat

import android.content.Context
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.network.HybridConnectivityPlanner
import com.ghalbitnet.meshx2.model.MeshNode

object RemotePresencePlanner {

    data class ContactState(
        val label: String,
        val detail: String
    )

    fun forContact(
        context: Context,
        nodes: List<MeshNode>
    ): ContactState {
        val hybrid =
            HybridConnectivityPlanner.snapshot(context, nodes)

        return when {
            hybrid.internetReady && hybrid.remoteReady -> ContactState(
                label = context.getString(R.string.saved_contacts_status_hybrid_ready),
                detail = context.getString(R.string.saved_contacts_status_hybrid_ready_desc)
            )

            hybrid.internetReady -> ContactState(
                label = context.getString(R.string.saved_contacts_status_server_wait),
                detail = context.getString(R.string.saved_contacts_status_server_wait_desc)
            )

            else -> ContactState(
                label = context.getString(R.string.saved_contacts_status_wait_internet),
                detail = context.getString(R.string.saved_contacts_status_wait_internet_desc)
            )
        }
    }

    fun summary(
        context: Context,
        nodes: List<MeshNode>,
        remoteCount: Int
    ): String {
        val hybrid =
            HybridConnectivityPlanner.snapshot(context, nodes)

        return when {
            remoteCount == 0 ->
                context.getString(R.string.saved_contacts_remote_summary_empty)

            hybrid.internetReady ->
                context.getString(
                    R.string.saved_contacts_remote_summary_ready,
                    remoteCount
                )

            else ->
                context.getString(
                    R.string.saved_contacts_remote_summary_waiting,
                    remoteCount
                )
        }
    }
}
