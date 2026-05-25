package com.ghalbitnet.meshx2.vpn

data class UsageCostPreview(
    val totalMb: Double,
    val estimatedCostGbht: Double,
    val providerShareGbht: Double,
    val relayShareGbht: Double,
    val builderShareGbht: Double
) {
    companion object {
        fun empty(): UsageCostPreview =
            UsageCostPreview(
                totalMb = 0.0,
                estimatedCostGbht = 0.0,
                providerShareGbht = 0.0,
                relayShareGbht = 0.0,
                builderShareGbht = 0.0
            )
    }
}
