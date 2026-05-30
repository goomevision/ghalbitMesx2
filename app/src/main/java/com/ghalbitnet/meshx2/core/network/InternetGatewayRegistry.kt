package com.ghalbitnet.meshx2.core.network

import android.content.Context
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.model.MeshNode

object InternetGatewayRegistry {

    data class GatewaySelection(
        val name: String,
        val ipAddress: String,
        val isLocal: Boolean,
        val modeLabel: String,
        val trusted: Int,
        val latency: Int
    ) {
        fun summary(context: Context): String {
            return if (isLocal) {
                context.getString(R.string.gateway_active_local)
            } else {
                context.getString(
                    R.string.gateway_active_remote,
                    name,
                    ipAddress
                )
            }
        }
    }

    fun select(
        context: Context,
        nodes: List<MeshNode>
    ): GatewaySelection? {
        if (ConnectivityStatusDetector.localGatewayActive(context)) {
            return GatewaySelection(
                name = context.getString(R.string.gateway_this_device),
                ipAddress = "local",
                isLocal = true,
                modeLabel = context.getString(R.string.transport_lan_hotspot),
                trusted = 100,
                latency = 0
            )
        }

        val gatewayNode =
            nodes
                .filter { it.online && it.gateway }
                .sortedWith(
                    compareByDescending<MeshNode> { it.trusted }
                        .thenBy { normalizedLatency(it.latency) }
                        .thenByDescending { it.signal }
                )
                .firstOrNull()
                ?: return null

        return GatewaySelection(
            name = gatewayNode.name,
            ipAddress = gatewayNode.ipAddress,
            isLocal = false,
            modeLabel = TransportPreference.modeForAddress(gatewayNode.ipAddress).label,
            trusted = gatewayNode.trusted,
            latency = gatewayNode.latency
        )
    }

    fun summaryText(
        context: Context,
        nodes: List<MeshNode>
    ): String {
        return select(context, nodes)?.summary(context)
            ?: context.getString(R.string.gateway_active_none)
    }

    private fun normalizedLatency(latency: Int): Int {
        return if (latency >= 0) latency else Int.MAX_VALUE
    }
}
