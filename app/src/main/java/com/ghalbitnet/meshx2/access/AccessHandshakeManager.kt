package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.vpn.VpnLogManager

object AccessHandshakeManager {

    data class HandshakeResult(
        val status: NetworkAccessPolicy.AuthStatus,
        val detail: String,
        val accessToken: String? = null
    )

    fun authorizeIncoming(
        context: Context,
        hello: NodeIdentityManager.HelloAuth,
        ipAddress: String,
        port: Int = NetworkAccessPolicy.DEFAULT_MESH_SOCKET_PORT
    ): HandshakeResult {
        PeerAuthRegistry.cleanupExpired()
        UnauthorizedClientRegistry.markPending(ipAddress, hello.nodeId, "HELLO_AUTH sedang diverifikasi")

        if (PeerAuthRegistry.isBlocked(hello.nodeId)) {
            HotspotClientSessionManager.upsert(
                clientIp = ipAddress,
                nodeId = hello.nodeId,
                status = GatewayClientPolicy.ClientStatus.UNAUTHORIZED,
                detail = PeerAuthRegistry.blockedReason(hello.nodeId) ?: "Node diblokir"
            )
            UnauthorizedClientRegistry.markStatus(
                ipAddress,
                NetworkAccessPolicy.AuthStatus.BLOCKED,
                hello.nodeId,
                PeerAuthRegistry.blockedReason(hello.nodeId) ?: "Node diblokir"
            )
            return HandshakeResult(
                status = NetworkAccessPolicy.AuthStatus.BLOCKED,
                detail = PeerAuthRegistry.blockedReason(hello.nodeId) ?: "Node diblokir"
            )
        }

        if (GlobalMeshIdentityManager.buildGlobalId(hello.publicKey) != hello.nodeId) {
            UnauthorizedDeviceDetector.block(hello.nodeId, "Public key tidak cocok dengan nodeId.")
            HotspotClientSessionManager.upsert(ipAddress, hello.nodeId, GatewayClientPolicy.ClientStatus.UNAUTHORIZED, "Public key mismatch")
            UnauthorizedClientRegistry.markStatus(ipAddress, NetworkAccessPolicy.AuthStatus.UNAUTHORIZED, hello.nodeId, "Public key mismatch")
            return HandshakeResult(NetworkAccessPolicy.AuthStatus.UNAUTHORIZED, "Public key mismatch")
        }

        if (!NetworkAccessPolicy.isAppVersionAccepted(hello.appVersion)) {
            UnauthorizedDeviceDetector.block(hello.nodeId, "Versi aplikasi tidak diterima.")
            HotspotClientSessionManager.upsert(ipAddress, hello.nodeId, GatewayClientPolicy.ClientStatus.UNAUTHORIZED, "Unsupported app version")
            UnauthorizedClientRegistry.markStatus(ipAddress, NetworkAccessPolicy.AuthStatus.UNAUTHORIZED, hello.nodeId, "Unsupported app version")
            return HandshakeResult(NetworkAccessPolicy.AuthStatus.UNAUTHORIZED, "Unsupported app version")
        }

        if (!NetworkAccessPolicy.isTimestampAccepted(hello.timestamp)) {
            UnauthorizedDeviceDetector.block(hello.nodeId, "Timestamp kadaluarsa.")
            HotspotClientSessionManager.upsert(ipAddress, hello.nodeId, GatewayClientPolicy.ClientStatus.TOKEN_EXPIRED, "Timestamp expired")
            UnauthorizedClientRegistry.markStatus(ipAddress, NetworkAccessPolicy.AuthStatus.EXPIRED, hello.nodeId, "Timestamp expired")
            return HandshakeResult(NetworkAccessPolicy.AuthStatus.EXPIRED, "Timestamp expired")
        }

        if (PeerAuthRegistry.hasSeenNonce(hello.nodeId, hello.nonce)) {
            UnauthorizedDeviceDetector.block(hello.nodeId, "Nonce dipakai ulang.")
            HotspotClientSessionManager.upsert(ipAddress, hello.nodeId, GatewayClientPolicy.ClientStatus.UNAUTHORIZED, "Nonce replayed")
            UnauthorizedClientRegistry.markStatus(ipAddress, NetworkAccessPolicy.AuthStatus.UNAUTHORIZED, hello.nodeId, "Nonce replayed")
            return HandshakeResult(NetworkAccessPolicy.AuthStatus.UNAUTHORIZED, "Nonce replayed")
        }

        if (!com.ghalbitnet.meshx2.security.CryptoEngine.verifySignature(
                hello.publicKey,
                hello.signingPayload(),
                hello.signature
            )
        ) {
            UnauthorizedDeviceDetector.block(hello.nodeId, "Signature tidak valid.")
            HotspotClientSessionManager.upsert(ipAddress, hello.nodeId, GatewayClientPolicy.ClientStatus.UNAUTHORIZED, "Invalid signature")
            UnauthorizedClientRegistry.markStatus(ipAddress, NetworkAccessPolicy.AuthStatus.UNAUTHORIZED, hello.nodeId, "Invalid signature")
            return HandshakeResult(NetworkAccessPolicy.AuthStatus.UNAUTHORIZED, "Invalid signature")
        }

        PeerAuthRegistry.noteNonce(hello.nodeId, hello.nonce, hello.timestamp)
        val existingRecord = PeerAuthRegistry.get(hello.nodeId)
        val keyChanged =
            existingRecord != null &&
                existingRecord.publicKey.isNotBlank() &&
                existingRecord.publicKey != hello.publicKey
        if (keyChanged) {
            VpnLogManager.warn(
                "ACCESS_TOKEN_REFRESH_REQUIRED",
                "nodeId=${hello.nodeId} reason=PUBLIC_KEY_CHANGED"
            )
            AccessTokenManager.expireToken(hello.nodeId)
        }
        val existingToken = AccessTokenManager.getValidToken(hello.nodeId)
        if (existingRecord?.accessToken != null && existingToken == null) {
            VpnLogManager.info(
                "ACCESS_TOKEN_REFRESH_REQUIRED",
                "nodeId=${hello.nodeId} reason=TOKEN_MISSING_OR_EXPIRED"
            )
        }
        val tokenLifecycle =
            AccessTokenManager.issueOrReuseToken(
                nodeId = hello.nodeId,
                refreshRequired = keyChanged
            )
        val token = tokenLifecycle.token
        PeerAuthRegistry.upsert(
            PeerAuthRegistry.PeerAuthRecord(
                nodeId = hello.nodeId,
                publicKey = hello.publicKey,
                walletAddress = hello.walletAddress,
                appVersion = hello.appVersion,
                ipAddress = ipAddress,
                port = port,
                status = NetworkAccessPolicy.AuthStatus.AUTHORIZED,
                accessToken = token.token,
                reason = "HELLO_AUTH accepted",
                lastSeen = System.currentTimeMillis(),
                expiresAt = token.expiresAt
            )
        )
        if (tokenLifecycle.reused) {
            VpnLogManager.info(
                "ACCESS_TOKEN_REUSED",
                "nodeId=${hello.nodeId} token=${token.token.take(12)} expiresAt=${token.expiresAt} stats=${AccessTokenManager.stats()}"
            )
        } else {
            VpnLogManager.info(
                "ACCESS_TOKEN_ISSUED",
                "nodeId=${hello.nodeId} token=${token.token.take(12)} expiresAt=${token.expiresAt} stats=${AccessTokenManager.stats()}"
            )
        }
        HotspotClientSessionManager.upsert(
            clientIp = ipAddress,
            nodeId = hello.nodeId,
            status = GatewayClientPolicy.ClientStatus.AUTHORIZED,
            detail = "Peer authorized untuk layanan Ghalbit.",
            accessToken = token.token
        )
        UnauthorizedClientRegistry.markAuthorized(ipAddress, hello.nodeId, "Peer authorized")
        return HandshakeResult(
            status = NetworkAccessPolicy.AuthStatus.AUTHORIZED,
            detail = "Peer authorized",
            accessToken = token.token
        )
    }
}
