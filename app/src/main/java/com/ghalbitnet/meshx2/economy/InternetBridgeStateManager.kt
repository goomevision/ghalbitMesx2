package com.ghalbitnet.meshx2.economy

import android.content.Context
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
object InternetBridgeStateManager {

    private const val PREFS_NAME = "internet_bridge_state_manager"
    private const val KEY_STATE = "state"
    private const val KEY_DETAIL = "detail"
    private const val KEY_UPDATED_AT = "updated_at"

    enum class BridgeState {
        WAITING,
        READY,
        ACTIVE,
        STOPPING,
        ERROR
    }

    data class Snapshot(
        val state: BridgeState,
        val detail: String,
        val updatedAt: Long
    ) {
        val canStart: Boolean
            get() = state == BridgeState.READY

        val canStop: Boolean
            get() = state == BridgeState.ACTIVE || state == BridgeState.STOPPING
    }

    fun mark(
        context: Context,
        state: BridgeState,
        detail: String
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, state.name)
            .putString(KEY_DETAIL, detail)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun snapshot(
        context: Context
    ): Snapshot {
        val prefs =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val storedState =
            prefs.getString(KEY_STATE, null)

        val storedDetail =
            prefs.getString(KEY_DETAIL, null)

        val updatedAt =
            prefs.getLong(KEY_UPDATED_AT, 0L)

        if (storedState != null && storedDetail != null) {
            return Snapshot(
                state = BridgeState.valueOf(storedState),
                detail = storedDetail,
                updatedAt = updatedAt
            )
        }

        return Snapshot(
            state = BridgeState.WAITING,
            detail = context.getString(R.string.internet_bridge_state_waiting_detail),
            updatedAt = updatedAt
        )
    }

    fun evaluate(
        context: Context
    ): Snapshot {
        // Read-model tingkat produk untuk layar ekonomi.
        // Saat ini ia memakai persisted active flag dari MeshVpnService sebagai sinyal
        // "service benar-benar aktif", lalu fallback ke policy evaluation ketika service
        // tidak aktif. Ini bukan detail runtime packet loop.
        if (MeshVpnServiceStateProxy.isActive(context)) {
            return Snapshot(
                state = BridgeState.ACTIVE,
                detail = context.getString(R.string.internet_bridge_state_active_detail),
                updatedAt = System.currentTimeMillis()
            )
        }

        val localGlobalId =
            runCatching {
                GlobalMeshIdentityManager.buildGlobalId(KeyStoreManager(context).publicKeyBase64)
            }.getOrDefault("")
        val decision =
            InternetBridgePolicyManager.evaluate(context, localGlobalId)

        return when {
            decision.allowed -> Snapshot(
                state = BridgeState.READY,
                detail = decision.detail,
                updatedAt = System.currentTimeMillis()
            )
            decision.routeMode == InternetBridgePolicyManager.RouteMode.UNAVAILABLE -> Snapshot(
                state = BridgeState.WAITING,
                detail = decision.detail,
                updatedAt = System.currentTimeMillis()
            )
            else -> Snapshot(
                state = BridgeState.ERROR,
                detail = decision.detail,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * Isolated tiny proxy to avoid circular compile references.
     *
     * TODO(core-stabilization):
     * Ganti dengan contract/helper status VPN read-only bersama agar UI economy,
     * controller, dan service membaca sumber yang sama.
     */
    object MeshVpnServiceStateProxy {
        fun isActive(
            context: Context
        ): Boolean {
            return com.ghalbitnet.meshx2.service.MeshVpnService.isBridgeServiceActive(context)
        }
    }
}
