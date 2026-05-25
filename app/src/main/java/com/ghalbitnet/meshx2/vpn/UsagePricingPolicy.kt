package com.ghalbitnet.meshx2.vpn

data class UsagePricingPolicy(
    val pricePerMbGbht: Double = 0.001,
    val providerShareRatio: Double = 0.70,
    val relayShareRatio: Double = 0.20,
    val builderShareRatio: Double = 0.10
) {
    companion object {
        fun default(): UsagePricingPolicy = UsagePricingPolicy()
    }
}
