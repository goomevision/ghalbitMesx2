package com.ghalbitnet.meshx2.economy

import android.content.Context
import com.ghalbitnet.meshx2.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object DesktopInternetTestManager {

    private const val PREFS_NAME = "desktop_internet_test"
    private const val KEY_STATE = "state"

    enum class Status {
        IDLE,
        ALLOWED,
        DENIED,
        STOPPED
    }

    data class Snapshot(
        val status: Status,
        val active: Boolean,
        val startedAt: Long,
        val detail: String,
        val walletBalance: Double,
        val minimumBalance: Double,
        val hotspotReady: Boolean
    ) {
        fun title(context: Context): String {
            return when (status) {
                Status.IDLE -> context.getString(R.string.desktop_test_status_idle)
                Status.ALLOWED -> context.getString(R.string.desktop_test_status_allowed)
                Status.DENIED -> context.getString(R.string.desktop_test_status_denied)
                Status.STOPPED -> context.getString(R.string.desktop_test_status_stopped)
            }
        }
    }

    fun snapshot(context: Context): Snapshot {
        val raw = prefs(context).getString(KEY_STATE, "").orEmpty()
        if (raw.isBlank()) {
            return Snapshot(
                status = Status.IDLE,
                active = false,
                startedAt = 0L,
                detail = context.getString(R.string.desktop_test_idle_detail),
                walletBalance = 0.0,
                minimumBalance = InternetBridgePolicyManager.current(context).minimumInternetAccessBalance,
                hotspotReady = false
            )
        }
        return runCatching {
            val json = JSONObject(raw)
            Snapshot(
                status = Status.valueOf(json.optString("status", Status.IDLE.name)),
                active = json.optBoolean("active", false),
                startedAt = json.optLong("startedAt", 0L),
                detail = json.optString("detail", context.getString(R.string.desktop_test_idle_detail)),
                walletBalance = json.optDouble("walletBalance", 0.0),
                minimumBalance = json.optDouble("minimumBalance", InternetBridgePolicyManager.current(context).minimumInternetAccessBalance),
                hotspotReady = json.optBoolean("hotspotReady", false)
            )
        }.getOrElse {
            Snapshot(
                status = Status.IDLE,
                active = false,
                startedAt = 0L,
                detail = context.getString(R.string.desktop_test_idle_detail),
                walletBalance = 0.0,
                minimumBalance = InternetBridgePolicyManager.current(context).minimumInternetAccessBalance,
                hotspotReady = false
            )
        }
    }

    suspend fun start(
        context: Context,
        ownerGlobalId: String
    ): Snapshot = withContext(Dispatchers.IO) {
        val providerReady = com.ghalbitnet.meshx2.core.network.InternetProviderReadinessManager.snapshot(context)
        val decision = InternetBridgePolicyManager.evaluate(context, ownerGlobalId)
        val snapshot =
            if (!providerReady.hotspotActive) {
                Snapshot(
                    status = Status.DENIED,
                    active = false,
                    startedAt = 0L,
                    detail = context.getString(R.string.desktop_test_denied_hotspot),
                    walletBalance = decision.walletBalance,
                    minimumBalance = InternetBridgePolicyManager.current(context).minimumInternetAccessBalance,
                    hotspotReady = false
                )
            } else {
                Snapshot(
                    status = Status.DENIED,
                    active = false,
                    startedAt = 0L,
                    detail =
                        context.getString(
                            R.string.desktop_test_denied_external_client,
                            decision.walletBalance,
                            InternetBridgePolicyManager.current(context).minimumInternetAccessBalance
                        ),
                    walletBalance = decision.walletBalance,
                    minimumBalance = InternetBridgePolicyManager.current(context).minimumInternetAccessBalance,
                    hotspotReady = true
                )
            }
        save(context, snapshot)
        snapshot
    }

    fun stop(
        context: Context,
        reason: String
    ): Snapshot {
        val current = snapshot(context)
        val stopped =
            current.copy(
                status = Status.STOPPED,
                active = false,
                detail = reason
            )
        save(context, stopped)
        return stopped
    }

    fun reevaluate(
        context: Context,
        ownerGlobalId: String
    ): Snapshot {
        val current = snapshot(context)
        if (!current.active) {
            return current
        }
        val providerReady = com.ghalbitnet.meshx2.core.network.InternetProviderReadinessManager.snapshot(context)
        return if (!providerReady.hotspotActive) {
            stop(context, context.getString(R.string.desktop_test_stopped_hotspot))
        } else {
            stop(context, context.getString(R.string.desktop_test_stopped_policy, context.getString(R.string.desktop_test_denied_external_short)))
        }
    }

    private fun save(
        context: Context,
        snapshot: Snapshot
    ) {
        val json =
            JSONObject()
                .put("status", snapshot.status.name)
                .put("active", snapshot.active)
                .put("startedAt", snapshot.startedAt)
                .put("detail", snapshot.detail)
                .put("walletBalance", snapshot.walletBalance)
                .put("minimumBalance", snapshot.minimumBalance)
                .put("hotspotReady", snapshot.hotspotReady)
        prefs(context)
            .edit()
            .putString(KEY_STATE, json.toString())
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
