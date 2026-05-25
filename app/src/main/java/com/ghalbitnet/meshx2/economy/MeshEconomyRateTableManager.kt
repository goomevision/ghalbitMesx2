package com.ghalbitnet.meshx2.economy

import android.content.Context
import kotlin.math.round

object MeshEconomyRateTableManager {

    private const val PREFS_NAME = "mesh_economy_rate_table"
    private const val KEY_BURN_PER_MB = "burn_per_mb"
    private const val KEY_GATEWAY_PER_MB = "gateway_per_mb"
    private const val KEY_RELAY_PER_MB = "relay_per_mb"
    private const val KEY_TREASURY_PER_MB = "treasury_per_mb"
    private const val KEY_BUILDER_PER_MB = "builder_per_mb"
    private const val KEY_VALIDATOR_PER_MB = "validator_per_mb"
    private const val KEY_BUILDER_SHARE = "builder_share"
    private const val KEY_CHAT_MULTIPLIER = "chat_multiplier"
    private const val KEY_MEDIA_MULTIPLIER = "media_multiplier"
    private const val KEY_CALL_MULTIPLIER = "call_multiplier"
    private const val KEY_SOS_MULTIPLIER = "sos_multiplier"
    private const val KEY_CONTROL_MULTIPLIER = "control_multiplier"
    private const val KEY_OTHER_MULTIPLIER = "other_multiplier"

    data class RateTable(
        val burnPerMb: Double = 0.08,
        val gatewayPerMb: Double = 0.045,
        val relayPerMb: Double = 0.022,
        val treasuryPerMb: Double = 0.006,
        val builderPerMb: Double = 0.01,
        val validatorPerMb: Double = 0.01,
        val builderShareRate: Double = 0.10,
        val chatMultiplier: Double = 1.00,
        val mediaMultiplier: Double = 1.15,
        val callMultiplier: Double = 1.30,
        val sosMultiplier: Double = 1.55,
        val controlMultiplier: Double = 0.55,
        val otherMultiplier: Double = 0.85
    ) {
        fun familyMultiplier(
            family: ServiceFamily
        ): Double {
            return when (family) {
                ServiceFamily.INTERNET -> otherMultiplier
                ServiceFamily.CHAT -> chatMultiplier
                ServiceFamily.MEDIA -> mediaMultiplier
                ServiceFamily.CALL -> callMultiplier
                ServiceFamily.SOS -> sosMultiplier
                ServiceFamily.CONTROL -> controlMultiplier
                ServiceFamily.OTHER -> otherMultiplier
            }
        }

        fun updated(
            family: ServiceFamily,
            multiplier: Double
        ): RateTable {
            return when (family) {
                ServiceFamily.INTERNET -> copy(otherMultiplier = multiplier)
                ServiceFamily.CHAT -> copy(chatMultiplier = multiplier)
                ServiceFamily.MEDIA -> copy(mediaMultiplier = multiplier)
                ServiceFamily.CALL -> copy(callMultiplier = multiplier)
                ServiceFamily.SOS -> copy(sosMultiplier = multiplier)
                ServiceFamily.CONTROL -> copy(controlMultiplier = multiplier)
                ServiceFamily.OTHER -> copy(otherMultiplier = multiplier)
            }
        }
    }

    enum class Preset {
        BALANCED,
        GATEWAY_FOCUS,
        RELAY_FOCUS,
        SOS_PRIORITY
    }

    fun current(
        context: Context
    ): RateTable {
        val prefs =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        return RateTable(
            burnPerMb = prefs.getFloat(KEY_BURN_PER_MB, 0.08f).toDouble(),
            gatewayPerMb = prefs.getFloat(KEY_GATEWAY_PER_MB, 0.045f).toDouble(),
            relayPerMb = prefs.getFloat(KEY_RELAY_PER_MB, 0.022f).toDouble(),
            treasuryPerMb = prefs.getFloat(KEY_TREASURY_PER_MB, 0.006f).toDouble(),
            builderPerMb =
                prefs.getFloat(
                    KEY_BUILDER_PER_MB,
                    prefs.getFloat(KEY_BUILDER_SHARE, 0.10f)
                ).toDouble(),
            validatorPerMb = prefs.getFloat(KEY_VALIDATOR_PER_MB, 0.01f).toDouble(),
            builderShareRate = prefs.getFloat(KEY_BUILDER_SHARE, 0.10f).toDouble(),
            chatMultiplier = prefs.getFloat(KEY_CHAT_MULTIPLIER, 1.00f).toDouble(),
            mediaMultiplier = prefs.getFloat(KEY_MEDIA_MULTIPLIER, 1.15f).toDouble(),
            callMultiplier = prefs.getFloat(KEY_CALL_MULTIPLIER, 1.30f).toDouble(),
            sosMultiplier = prefs.getFloat(KEY_SOS_MULTIPLIER, 1.55f).toDouble(),
            controlMultiplier = prefs.getFloat(KEY_CONTROL_MULTIPLIER, 0.55f).toDouble(),
            otherMultiplier = prefs.getFloat(KEY_OTHER_MULTIPLIER, 0.85f).toDouble()
        )
    }

    fun save(
        context: Context,
        table: RateTable
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_BURN_PER_MB, table.burnPerMb.toFloat())
            .putFloat(KEY_GATEWAY_PER_MB, table.gatewayPerMb.toFloat())
            .putFloat(KEY_RELAY_PER_MB, table.relayPerMb.toFloat())
            .putFloat(KEY_TREASURY_PER_MB, table.treasuryPerMb.toFloat())
            .putFloat(KEY_BUILDER_PER_MB, table.builderPerMb.toFloat())
            .putFloat(KEY_VALIDATOR_PER_MB, table.validatorPerMb.toFloat())
            .putFloat(KEY_BUILDER_SHARE, table.builderShareRate.toFloat())
            .putFloat(KEY_CHAT_MULTIPLIER, table.chatMultiplier.toFloat())
            .putFloat(KEY_MEDIA_MULTIPLIER, table.mediaMultiplier.toFloat())
            .putFloat(KEY_CALL_MULTIPLIER, table.callMultiplier.toFloat())
            .putFloat(KEY_SOS_MULTIPLIER, table.sosMultiplier.toFloat())
            .putFloat(KEY_CONTROL_MULTIPLIER, table.controlMultiplier.toFloat())
            .putFloat(KEY_OTHER_MULTIPLIER, table.otherMultiplier.toFloat())
            .apply()
    }

    fun reset(
        context: Context
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun preset(
        preset: Preset
    ): RateTable {
        return when (preset) {
            Preset.BALANCED -> RateTable()
            Preset.GATEWAY_FOCUS -> RateTable(
                burnPerMb = 0.08,
                gatewayPerMb = 0.055,
                relayPerMb = 0.018,
                treasuryPerMb = 0.006,
                builderPerMb = 0.010,
                validatorPerMb = 0.009,
                builderShareRate = 0.10,
                chatMultiplier = 1.00,
                mediaMultiplier = 1.12,
                callMultiplier = 1.25,
                sosMultiplier = 1.45,
                controlMultiplier = 0.50,
                otherMultiplier = 0.82
            )
            Preset.RELAY_FOCUS -> RateTable(
                burnPerMb = 0.08,
                gatewayPerMb = 0.040,
                relayPerMb = 0.030,
                treasuryPerMb = 0.006,
                builderPerMb = 0.010,
                validatorPerMb = 0.009,
                builderShareRate = 0.10,
                chatMultiplier = 1.00,
                mediaMultiplier = 1.18,
                callMultiplier = 1.28,
                sosMultiplier = 1.50,
                controlMultiplier = 0.58,
                otherMultiplier = 0.88
            )
            Preset.SOS_PRIORITY -> RateTable(
                burnPerMb = 0.085,
                gatewayPerMb = 0.048,
                relayPerMb = 0.024,
                treasuryPerMb = 0.007,
                builderPerMb = 0.010,
                validatorPerMb = 0.010,
                builderShareRate = 0.10,
                chatMultiplier = 0.95,
                mediaMultiplier = 1.10,
                callMultiplier = 1.25,
                sosMultiplier = 1.80,
                controlMultiplier = 0.60,
                otherMultiplier = 0.85
            )
        }
    }

    fun round2(
        value: Double
    ): Double {
        return round(value * 100.0) / 100.0
    }
}
