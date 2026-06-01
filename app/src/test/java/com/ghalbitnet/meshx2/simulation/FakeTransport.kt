package com.ghalbitnet.meshx2.simulation

enum class TransportType {
    INTERNET,
    RELAY,
    MESH
}

data class SendResult(
    val ok: Boolean,
    val transport: TransportType,
    val reason: String? = null
)

class FakeTransport(
    private val net: FakeNetworkCondition
) {
    fun sendChat(preferInternet: Boolean): SendResult {
        if (preferInternet && net.internetAvailable) {
            return SendResult(true, TransportType.INTERNET)
        }
        if (net.relayAvailable) return SendResult(true, TransportType.RELAY)
        return SendResult(false, TransportType.MESH, "ROUTE_NOT_FOUND")
    }
}

