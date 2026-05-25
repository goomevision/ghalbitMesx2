package com.ghalbitnet.meshx2.economy

import android.content.Context
import com.ghalbitnet.meshx2.core.server.MeshServerApiClient
import com.ghalbitnet.meshx2.core.server.FirebaseRemoteSyncManager
import org.json.JSONObject

object MeshEconomyServerPolicyManager {

    data class ServerPolicy(
        val versionLabel: String,
        val sourceLabel: String,
        val priceReferencePerGbhtIdr: Int,
        val appBonusTable: MeshEconomyRateTableManager.RateTable,
        val internetBridgeTable: MeshEconomyRateTableManager.RateTable,
        val localEditingLocked: Boolean = true
    ) {
        fun rateTableFor(
            usageMode: ServiceUsageMode
        ): MeshEconomyRateTableManager.RateTable {
            return when (usageMode) {
                ServiceUsageMode.APP_BONUS -> appBonusTable
                ServiceUsageMode.INTERNET_BRIDGE -> internetBridgeTable
            }
        }

        fun pricingLabelFor(
            usageMode: ServiceUsageMode
        ): String {
            return when (usageMode) {
                ServiceUsageMode.APP_BONUS -> "BONUS APP"
                ServiceUsageMode.INTERNET_BRIDGE -> "JASA INTERNET LUAR"
            }
        }
    }

    fun current(
        context: Context
    ): ServerPolicy {
        val cached =
            FirebaseRemoteSyncManager.cachedEconomyPolicyJson(context)
                .ifBlank { MeshServerApiClient.cachedEconomyPolicyJson(context) }
        if (cached.isNotBlank()) {
            parseServerPolicy(cached)?.let { return it }
        }

        return defaultPolicy()
    }

    private fun defaultPolicy(): ServerPolicy {
        return ServerPolicy(
            versionLabel = "GBHT-V1-2026.05",
            sourceLabel = "GBHT Policy v1 - bayar sesuai pemakaian",
            priceReferencePerGbhtIdr = 100,
            appBonusTable = MeshEconomyRateTableManager.RateTable(
                burnPerMb = 0.0,
                gatewayPerMb = 0.026,
                relayPerMb = 0.012,
                treasuryPerMb = 0.003,
                builderPerMb = 0.005,
                validatorPerMb = 0.005,
                builderShareRate = 0.10,
                chatMultiplier = 1.00,
                mediaMultiplier = 1.05,
                callMultiplier = 1.10,
                sosMultiplier = 1.20,
                controlMultiplier = 0.45,
                otherMultiplier = 0.80
            ),
            internetBridgeTable = MeshEconomyRateTableManager.RateTable(
                burnPerMb = 0.09765625,
                gatewayPerMb = 0.0537109375,
                relayPerMb = 0.01953125,
                treasuryPerMb = 0.0048828125,
                builderPerMb = 0.009765625,
                validatorPerMb = 0.009765625,
                builderShareRate = 0.10,
                chatMultiplier = 1.00,
                mediaMultiplier = 1.05,
                callMultiplier = 1.10,
                sosMultiplier = 1.20,
                controlMultiplier = 0.55,
                otherMultiplier = 1.00
            ),
            localEditingLocked = true
        )
    }

    private fun parseServerPolicy(rawJson: String): ServerPolicy? {
        return runCatching {
            val root = JSONObject(rawJson)
            val appBonus = root.optJSONObject("appBonusTable") ?: JSONObject()
            val internetBridge = root.optJSONObject("internetBridgeTable") ?: JSONObject()
            ServerPolicy(
                versionLabel = root.optString("versionLabel", "SERVER"),
                sourceLabel = root.optString("sourceLabel", "Server jarak jauh"),
                priceReferencePerGbhtIdr = root.optInt("priceReferencePerGbhtIdr", defaultPolicy().priceReferencePerGbhtIdr),
                appBonusTable = parseRateTable(appBonus, defaultPolicy().appBonusTable),
                internetBridgeTable = parseRateTable(internetBridge, defaultPolicy().internetBridgeTable),
                localEditingLocked = root.optBoolean("localEditingLocked", true)
            )
        }.getOrNull()
    }

    private fun parseRateTable(
        source: JSONObject,
        fallback: MeshEconomyRateTableManager.RateTable
    ): MeshEconomyRateTableManager.RateTable {
        return MeshEconomyRateTableManager.RateTable(
            burnPerMb = source.optDouble("burnPerMb", fallback.burnPerMb),
            gatewayPerMb = source.optDouble("gatewayPerMb", fallback.gatewayPerMb),
            relayPerMb = source.optDouble("relayPerMb", fallback.relayPerMb),
            treasuryPerMb = source.optDouble("treasuryPerMb", fallback.treasuryPerMb),
            builderPerMb = source.optDouble("builderPerMb", fallback.builderPerMb),
            validatorPerMb = source.optDouble("validatorPerMb", fallback.validatorPerMb),
            builderShareRate = source.optDouble("builderShareRate", fallback.builderShareRate),
            chatMultiplier = source.optDouble("chatMultiplier", fallback.chatMultiplier),
            mediaMultiplier = source.optDouble("mediaMultiplier", fallback.mediaMultiplier),
            callMultiplier = source.optDouble("callMultiplier", fallback.callMultiplier),
            sosMultiplier = source.optDouble("sosMultiplier", fallback.sosMultiplier),
            controlMultiplier = source.optDouble("controlMultiplier", fallback.controlMultiplier),
            otherMultiplier = source.optDouble("otherMultiplier", fallback.otherMultiplier)
        )
    }
}
