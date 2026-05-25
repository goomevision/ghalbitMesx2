package com.ghalbitnet.meshx2.vpn

import android.content.Context
import com.ghalbitnet.meshx2.core.network.InternetGatewayRegistry
import com.ghalbitnet.meshx2.core.network.PeerAddressRegistry
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import org.json.JSONObject

object GatewaySelector {

    private const val PREFS_NAME = "ghalbit_vpn_gateway_selector"
    private const val KEY_FAILED_GATEWAYS = "failed_gateways"
    private const val FAILURE_COOLDOWN_MS = 15_000L

    data class GatewaySnapshot(
        val available: Boolean,
        val gatewayId: String,
        val gatewayName: String,
        val gatewayAddress: String,
        val gatewayPort: Int,
        val routeScore: Int,
        val detail: String
    )

    fun current(context: Context): GatewaySnapshot {
        val now = System.currentTimeMillis()
        val failures = loadFailures(context, now)
        val candidates =
            InternetGatewayRegistry
                .candidates(context, DiscoveryManager.discoverNodes())
                .filterNot { candidate -> failures.has(candidate.nodeId.ifBlank { "local" }) }

        if (candidates.isEmpty()) {
            return GatewaySnapshot(
                available = false,
                gatewayId = "",
                gatewayName = "",
                gatewayAddress = "",
                gatewayPort = 0,
                routeScore = 0,
                detail = "Belum ada gateway yang siap."
            )
        }

        for (gateway in candidates) {
            val resolved = resolveGatewayEndpoint(context, gateway)
            if (resolved == null) {
                VpnLogManager.warn(
                    "GATEWAY_REJECTED_MISSING_ADDRESS",
                    "gateway=${gateway.name} id=${gateway.nodeId.ifBlank { "local" }} tidak punya address socket nyata"
                )
                continue
            }
            VpnLogManager.info(
                "GATEWAY_SELECTED_WITH_ADDRESS",
                "gateway=${gateway.name} id=${gateway.nodeId.ifBlank { "local" }} address=${resolved.address}:${resolved.port}"
            )
            return GatewaySnapshot(
                available = true,
                gatewayId = gateway.nodeId,
                gatewayName = gateway.name,
                gatewayAddress = resolved.address,
                gatewayPort = resolved.port,
                routeScore = gateway.routeScore,
                detail = gateway.routeReason
            )
        }

        return GatewaySnapshot(
            available = false,
            gatewayId = "",
            gatewayName = "",
            gatewayAddress = "",
            gatewayPort = 0,
            routeScore = 0,
            detail = "Gateway belum punya alamat mesh socket."
        )
    }

    private fun resolveGatewayEndpoint(
        context: Context,
        gateway: InternetGatewayRegistry.GatewaySelection
    ): PeerAddressRegistry.Entry? {
        if (gateway.isLocal || gateway.ipAddress == "local") {
            return null
        }
        if (gateway.ipAddress.isNotBlank() && gateway.ipAddress != "0.0.0.0") {
            return PeerAddressRegistry.Entry(
                peerId = gateway.nodeId,
                address = gateway.ipAddress,
                port = PeerAddressRegistry.DEFAULT_MESH_SOCKET_PORT,
                lastSeen = System.currentTimeMillis()
            )
        }
        return PeerAddressRegistry.resolve(context, gateway.nodeId)
            ?: PeerAddressRegistry.resolve(context, gateway.name)
    }

    fun markFailed(
        context: Context,
        gatewayId: String,
        gatewayName: String,
        reason: String
    ) {
        if (gatewayId.isBlank()) return
        val source = loadFailures(context, System.currentTimeMillis())
        source.put(gatewayId, System.currentTimeMillis() + FAILURE_COOLDOWN_MS)
        saveFailures(context, source)
        VpnLogManager.warn(
            "GATEWAY_FAILOVER_PENDING",
            "Gateway $gatewayName ditandai gagal sementara: $reason"
        )
    }

    private fun loadFailures(
        context: Context,
        now: Long
    ): JSONObject {
        val raw =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_FAILED_GATEWAYS, "{}")
                .orEmpty()
        val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        val cleaned = JSONObject()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val until = json.optLong(key, 0L)
            if (until > now) {
                cleaned.put(key, until)
            }
        }
        saveFailures(context, cleaned)
        return cleaned
    }

    private fun saveFailures(
        context: Context,
        source: JSONObject
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FAILED_GATEWAYS, source.toString())
            .apply()
    }
}
