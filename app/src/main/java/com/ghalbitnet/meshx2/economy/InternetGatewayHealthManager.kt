package com.ghalbitnet.meshx2.economy

import android.content.Context
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.network.InternetGatewayRegistry

object InternetGatewayHealthManager {

    data class HealthPresentation(
        val signalIndicator: String,
        val statusLabel: String,
        val summaryLabel: String
    )

    fun present(
        context: Context,
        gateway: InternetGatewayRegistry.GatewaySelection,
        selectedGatewayId: String
    ): HealthPresentation {
        val signalIndicator =
            when {
                gateway.routeScore >= 88 -> "[||||]"
                gateway.routeScore >= 76 -> "[||| ]"
                gateway.routeScore >= 62 -> "[||  ]"
                else -> "[|   ]"
            }

        val isSelected =
            gateway.nodeId == selectedGatewayId

        val statusLabel =
            when {
                gateway.activeLoad >= 3 || gateway.recentUsageMb >= 768.0 ->
                    context.getString(R.string.internet_bridge_health_busy)
                isSelected && (gateway.routeScore < 60 || gateway.latency > 260 || gateway.signal < 40) ->
                    context.getString(R.string.internet_bridge_health_risky)
                !isSelected && gateway.routeScore >= 72 ->
                    context.getString(R.string.internet_bridge_health_backup_ready)
                else ->
                    context.getString(R.string.internet_bridge_health_stable)
            }

        val summaryLabel =
            if (isSelected) {
                context.getString(R.string.internet_bridge_health_active_now)
            } else {
                context.getString(R.string.internet_bridge_health_ready_waiting)
            }

        return HealthPresentation(
            signalIndicator = signalIndicator,
            statusLabel = statusLabel,
            summaryLabel = summaryLabel
        )
    }
}
