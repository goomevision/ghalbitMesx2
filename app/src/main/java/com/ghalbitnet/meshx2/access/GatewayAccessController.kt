package com.ghalbitnet.meshx2.access

object GatewayAccessController {

    data class AccessDecision(
        val status: GatewayClientPolicy.ClientStatus,
        val detail: String,
        val nodeId: String? = null,
        val accessToken: String? = null
    )

    fun evaluate(clientIp: String, presentedToken: String? = null): AccessDecision {
        val peer = PeerAuthRegistry.getByIp(clientIp)
        val unauthorized = UnauthorizedClientRegistry.snapshot(clientIp)
        if (!presentedToken.isNullOrBlank()) {
            val tokenRecord = AccessTokenManager.findByToken(presentedToken)
            if (tokenRecord != null) {
                return AccessDecision(
                    status = GatewayClientPolicy.ClientStatus.AUTHORIZED,
                    detail = "Client authorized lewat access token.",
                    nodeId = tokenRecord.nodeId,
                    accessToken = tokenRecord.token
                )
            }
        }

        if (peer == null) {
            val status =
                if (unauthorized?.status == NetworkAccessPolicy.AuthStatus.EXPIRED) {
                    GatewayClientPolicy.ClientStatus.TOKEN_EXPIRED
                } else {
                    GatewayClientPolicy.ClientStatus.UNAUTHORIZED
                }
            val detail = unauthorized?.detail ?: "Client belum mengirim HELLO_AUTH."
            return AccessDecision(status = status, detail = detail)
        }

        val validToken = AccessTokenManager.getValidToken(peer.nodeId)
        if (validToken == null) {
            return AccessDecision(
                status = GatewayClientPolicy.ClientStatus.TOKEN_EXPIRED,
                detail = "Access token kadaluarsa.",
                nodeId = peer.nodeId
            )
        }

        if (!presentedToken.isNullOrBlank() && presentedToken != validToken.token) {
            return AccessDecision(
                status = GatewayClientPolicy.ClientStatus.UNAUTHORIZED,
                detail = "Access token tidak cocok.",
                nodeId = peer.nodeId
            )
        }

        return AccessDecision(
            status = GatewayClientPolicy.ClientStatus.AUTHORIZED,
            detail = "Client authorized untuk layanan Ghalbit.",
            nodeId = peer.nodeId,
            accessToken = validToken.token
        )
    }
}
