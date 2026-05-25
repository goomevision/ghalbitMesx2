package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.core.network.InternetProviderReadinessManager

object HotspotClientDetector {

    data class Snapshot(
        val providerActive: Boolean,
        val hotspotActive: Boolean,
        val authorizedPeerCount: Int,
        val unauthorizedObservationCount: Int,
        val unauthorizedActive: Boolean,
        val accessMode: HotspotAccessMode,
        val observations: List<UnauthorizedDeviceDetector.Observation>
    )

    fun snapshot(context: Context): Snapshot {
        val provider = InternetProviderReadinessManager.snapshot(context)
        val observations = UnauthorizedDeviceDetector.recentObservations()
        val authorizedPeerCount =
            observations.mapNotNull { it.nodeId }
                .distinct()
                .count { PeerAuthRegistry.isAuthorized(it) }

        return Snapshot(
            providerActive = provider.providerReady || provider.providerActive,
            hotspotActive = provider.hotspotActive,
            authorizedPeerCount = authorizedPeerCount,
            unauthorizedObservationCount = observations.size,
            unauthorizedActive = provider.hotspotActive && observations.isNotEmpty(),
            accessMode = HotspotAccessMode.STOCK_ANDROID_LIMITED,
            observations = observations
        )
    }
}
