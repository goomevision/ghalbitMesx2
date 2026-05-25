package com.ghalbitnet.meshx2.vpn

import java.util.Locale

object UsageCostCalculator {

    fun calculate(
        totalBytes: Long,
        policy: UsagePricingPolicy = UsagePricingPolicy.default()
    ): UsageCostPreview {
        val totalMb = totalBytes / 1024.0 / 1024.0
        val estimatedCost = totalMb * policy.pricePerMbGbht
        val preview =
            UsageCostPreview(
                totalMb = totalMb,
                estimatedCostGbht = estimatedCost,
                providerShareGbht = estimatedCost * policy.providerShareRatio,
                relayShareGbht = estimatedCost * policy.relayShareRatio,
                builderShareGbht = estimatedCost * policy.builderShareRatio
            )
        VpnLogManager.info(
            "USAGE_COST_CALCULATED",
            String.format(
                Locale.getDefault(),
                "totalMb=%.4f estimated=%.6f provider=%.6f relay=%.6f builder=%.6f",
                preview.totalMb,
                preview.estimatedCostGbht,
                preview.providerShareGbht,
                preview.relayShareGbht,
                preview.builderShareGbht
            )
        )
        return preview
    }
}
