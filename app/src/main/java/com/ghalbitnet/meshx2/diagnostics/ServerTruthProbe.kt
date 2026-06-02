package com.ghalbitnet.meshx2.diagnostics

import android.content.Context
import com.ghalbitnet.meshx2.BuildConfig
import com.ghalbitnet.meshx2.diagnostics.evidence.RuntimeEvidenceCollector
import com.ghalbitnet.meshx2.diagnostics.evidence.RuntimeEvidenceTags
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import com.ghalbitnet.meshx2.util.LogThrottle

data class ServerEndpointReadiness(
    val endpoint: String,
    val method: String,
    val category: String,
    val status: String
)

object ServerTruthProbe {
    fun baseUrls(): Map<String, String> = mapOf(
        "relayBaseUrl" to OnlineFallbackTransport.relayBaseUrl(),
        "presenceBaseUrl" to OnlineFallbackTransport.presenceBaseUrl(),
        "identityBaseUrl" to OnlineFallbackTransport.relayBaseUrl(),
        "sessionBaseUrl" to OnlineFallbackTransport.relayBaseUrl(),
        "mediaBaseUrl" to OnlineFallbackTransport.relayBaseUrl(),
        "websocketUrl" to ""
    )

    fun endpointCatalog(): List<ServerEndpointReadiness> = listOf(
        ServerEndpointReadiness("/health", "GET", "PING", "READY_IN_APP"),
        ServerEndpointReadiness("/ping", "GET", "PING", "READY_IN_APP"),
        ServerEndpointReadiness("/identity/register", "POST", "IDENTITY", "READY_IN_APP"),
        ServerEndpointReadiness("/identity/sync", "POST", "IDENTITY", "READY_IN_APP"),
        ServerEndpointReadiness("/identity/lookup/{callId}", "GET", "IDENTITY", "READY_IN_APP"),
        ServerEndpointReadiness("/presence/heartbeat", "POST", "PRESENCE", "READY_IN_APP"),
        ServerEndpointReadiness("/presence/{globalId}", "GET", "PRESENCE", "READY_IN_APP"),
        ServerEndpointReadiness("/relay/send", "POST", "RELAY", "READY_IN_APP"),
        ServerEndpointReadiness("/relay/inbox/{globalId}", "GET", "RELAY", "READY_IN_APP"),
        ServerEndpointReadiness("/receipt/delivered", "POST", "RELAY", "READY_IN_APP"),
        ServerEndpointReadiness("/receipt/read", "POST", "RELAY", "READY_IN_APP"),
        ServerEndpointReadiness("/session/prepare-route", "POST", "SESSION", "READY_IN_APP"),
        ServerEndpointReadiness("/session/validate-route", "POST", "SESSION", "READY_IN_APP"),
        ServerEndpointReadiness("/session/heartbeat", "POST", "SESSION", "READY_IN_APP"),
        ServerEndpointReadiness("/session/start", "POST", "SESSION", "READY_IN_APP"),
        ServerEndpointReadiness("/session/ringing", "POST", "SESSION", "READY_IN_APP"),
        ServerEndpointReadiness("/session/accept", "POST", "SESSION", "READY_IN_APP"),
        ServerEndpointReadiness("/session/reject", "POST", "SESSION", "READY_IN_APP"),
        ServerEndpointReadiness("/session/end", "POST", "SESSION", "READY_IN_APP")
    )

    fun logSnapshot(context: Context) {
        val configured = BuildConfig.INTERNET_RELAY_CONFIGURED
        val urls = baseUrls()
        RuntimeEvidenceCollector.record(
            context,
            if (configured) RuntimeEvidenceTags.SERVER_BASE_URL_CONFIGURED else RuntimeEvidenceTags.SERVER_NOT_CONFIGURED,
            source = "ServerTruthProbe",
            status = configured.toString(),
            details = "relay=${urls["relayBaseUrl"]} presence=${urls["presenceBaseUrl"]}"
        )
        LogThrottle.d(
            "GHALBIT-SERVER-TRUTH",
            "base-url",
            "BASE_URL relay=${urls["relayBaseUrl"]} presence=${urls["presenceBaseUrl"]} configured=$configured",
            15_000L,
            context
        )
    }
}
