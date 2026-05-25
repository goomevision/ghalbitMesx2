package com.ghalbitnet.meshx2.core.runtime

import android.content.Context
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.core.network.InternetProviderReadinessManager
import com.ghalbitnet.meshx2.core.server.FirebaseRemoteSyncManager
import com.ghalbitnet.meshx2.economy.InternetBridgePolicyManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.settings.OnboardingManager
import com.ghalbitnet.meshx2.token.TokenManager
import com.ghalbitnet.meshx2.vpn.VpnStatusProvider
import kotlinx.coroutines.runBlocking

object GhalbitSystemStatusManager {

    data class Snapshot(
        val globalId: String,
        val onboardingCompleted: Boolean,
        val firebaseReady: Boolean,
        val meshRunning: Boolean,
        val vpnBridgeActive: Boolean,
        val hotspotReady: Boolean,
        val providerReady: Boolean,
        val walletBalance: Double,
        val internetAllowed: Boolean,
        val internetDecision: String
    )

    fun snapshot(context: Context): Snapshot {
        val keyStore = KeyStoreManager(context)
        val globalId = GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)
        val onboarding = OnboardingManager.snapshot(context)
        val provider = InternetProviderReadinessManager.snapshot(context)
        val firebaseReady = FirebaseRemoteSyncManager.isReady(context)
        val meshRuntime = MeshRuntimeState.snapshot()
        val vpnStatus = VpnStatusProvider.snapshot(context)

        TokenManager.init(context)
        val walletBalance =
            runBlocking {
                TokenManager.ensureWalletBootstrap(globalId)
                FirebaseRemoteSyncManager.cachedWalletBalance(context, globalId)
                    ?: TokenManager.getWalletBalanceForGlobalId(globalId)
            }

        val bridgeDecision = InternetBridgePolicyManager.evaluate(context, globalId)

        return Snapshot(
            globalId = globalId,
            onboardingCompleted = onboarding.completed,
            firebaseReady = firebaseReady,
            meshRunning = meshRuntime.isMeshRunning,
            vpnBridgeActive = vpnStatus.serviceActive,
            hotspotReady = provider.hotspotActive,
            providerReady = provider.providerReady,
            walletBalance = walletBalance,
            internetAllowed = bridgeDecision.allowed,
            internetDecision = vpnStatus.warning ?: bridgeDecision.detail
        )
    }
}
