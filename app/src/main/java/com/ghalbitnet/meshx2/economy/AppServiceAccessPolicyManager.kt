package com.ghalbitnet.meshx2.economy

import android.content.Context
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.chat.RemoteModeManager
import com.ghalbitnet.meshx2.core.network.ConnectivityStatusDetector
import com.ghalbitnet.meshx2.model.MeshNode

object AppServiceAccessPolicyManager {

    data class Decision(
        val title: String,
        val detail: String,
        val rewardLabel: String,
        val localAvailable: Boolean,
        val internetAvailable: Boolean
    )

    fun evaluate(
        context: Context,
        nodes: List<MeshNode>
    ): Decision {
        val connectivity =
            ConnectivityStatusDetector.snapshot(context, nodes)
        val remoteModeEnabled =
            RemoteModeManager.isEnabled(context)

        return when {
            connectivity.hasLocal && connectivity.hasInternet -> Decision(
                title = context.getString(R.string.app_service_access_full),
                detail = context.getString(R.string.app_service_access_full_desc),
                rewardLabel = context.getString(R.string.app_service_access_reward),
                localAvailable = true,
                internetAvailable = true
            )

            connectivity.hasLocal -> Decision(
                title = context.getString(R.string.app_service_access_local),
                detail = context.getString(R.string.app_service_access_local_desc),
                rewardLabel = context.getString(R.string.app_service_access_reward),
                localAvailable = true,
                internetAvailable = false
            )

            connectivity.hasInternet && remoteModeEnabled -> Decision(
                title = context.getString(R.string.app_service_access_internet),
                detail = context.getString(R.string.app_service_access_internet_desc),
                rewardLabel = context.getString(R.string.app_service_access_reward),
                localAvailable = false,
                internetAvailable = true
            )

            connectivity.hasInternet -> Decision(
                title = context.getString(R.string.app_service_access_internet_ready),
                detail = context.getString(R.string.app_service_access_internet_ready_desc),
                rewardLabel = context.getString(R.string.app_service_access_reward),
                localAvailable = false,
                internetAvailable = true
            )

            else -> Decision(
                title = context.getString(R.string.app_service_access_unavailable),
                detail = context.getString(R.string.app_service_access_unavailable_desc),
                rewardLabel = context.getString(R.string.app_service_access_reward),
                localAvailable = false,
                internetAvailable = false
            )
        }
    }
}
