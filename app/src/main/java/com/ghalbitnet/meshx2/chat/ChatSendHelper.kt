package com.ghalbitnet.meshx2.chat

import android.content.Context
import android.util.Base64
import android.util.Log
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.call.CallManager
import com.ghalbitnet.meshx2.core.log.MeshLogger
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.core.runtime.PacketTraceEntry
import com.ghalbitnet.meshx2.core.runtime.PacketTraceStore
import com.ghalbitnet.meshx2.identity.CentralIdentityResolver
import com.ghalbitnet.meshx2.identity.IdentityDiagnosticsFormatter
import com.ghalbitnet.meshx2.identity.IdentityBridge
import com.ghalbitnet.meshx2.identity.IdentityDisplayFormatter
import com.ghalbitnet.meshx2.identity.IdentityRegistry
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.network.ReliablePacketSender
import com.ghalbitnet.meshx2.online.DeliveryStatus
import com.ghalbitnet.meshx2.online.OnlineFallbackTransport
import com.ghalbitnet.meshx2.online.OnlinePresenceManager
import com.ghalbitnet.meshx2.online.PendingMessage
import com.ghalbitnet.meshx2.online.PendingMessageStore
import com.ghalbitnet.meshx2.routing.PacketTtlManager
import com.ghalbitnet.meshx2.routing.IntelligentRouteMemory
import com.ghalbitnet.meshx2.security.CryptoEngine
import com.ghalbitnet.meshx2.security.KeyStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ChatSendHelper {
    data class ChatSendResult(
        val deliveryStatus: DeliveryStatus,
        val routeLabel: String,
        val successful: Boolean,
        val pendingQueued: Boolean = false,
        val expiresAt: Long = 0L
    )

    suspend fun sendTextMessage(
        context: Context,
        keyStore: KeyStoreManager,
        peerName: String,
        peerIp: String,
        message: String,
        packetId: String,
        peerGlobalId: String? = null,
        peerPublicKey: String? = null,
        peerWalletAddress: String? = null,
        peerDisplayName: String? = null
    ): ChatSendResult {
        return withContext(Dispatchers.IO) {
            OnlinePresenceManager.bind(context)
            val persistedMetadata =
                ConversationIdentityStore.get(
                    context = context,
                    chatId = peerName
                )

            val targetIdentity =
                IdentityRegistry.resolveForChatTarget(
                    globalId = peerGlobalId ?: persistedMetadata?.globalId,
                    peerName = peerName,
                    ipAddress = peerIp,
                    publicKey = peerPublicKey ?: persistedMetadata?.publicKey,
                    walletAddress = peerWalletAddress ?: persistedMetadata?.walletAddress
                )
                    ?: IdentityRegistry.upsert(
                        IdentityBridge.fromChatPeer(
                            peerName = peerName,
                            peerIp = peerIp,
                            publicKey = peerPublicKey ?: persistedMetadata?.publicKey,
                            walletAddress = peerWalletAddress ?: persistedMetadata?.walletAddress,
                            globalId = peerGlobalId ?: persistedMetadata?.globalId,
                            displayName = peerDisplayName ?: persistedMetadata?.canonicalDisplayName
                        )
                    )

            ConversationIdentityStore.upsert(
                context = context,
                chatId = peerName,
                identity = targetIdentity
            )

            val resolvedPeerName =
                targetIdentity.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: peerName

            val resolvedPeerIp =
                targetIdentity.lastKnownIp
                    ?.takeIf { it.isNotBlank() }
                    ?: persistedMetadata?.routeHint?.takeIf { it.isNotBlank() }
                    ?: peerGlobalId?.let { IntelligentRouteMemory.getHint(context, it)?.nextHopId }
                    ?: IntelligentRouteMemory.getHint(context, peerName)?.nextHopId
                    ?: peerIp

            val resolvedPeerPublicKey =
                targetIdentity.publicKey
                    ?.takeIf { it.isNotBlank() }
                    ?: peerPublicKey

            MeshLogger.i(
                "OUTBOUND_CHAT_IDENTITY",
                IdentityDiagnosticsFormatter.formatResolved(
                    CentralIdentityResolver.resolve(
                        context = context,
                        legacyChatId = peerName,
                        peerName = resolvedPeerName,
                        peerIp = resolvedPeerIp,
                        globalIdHint = targetIdentity.globalId,
                        publicKeyHint = targetIdentity.publicKey,
                        walletAddressHint = targetIdentity.walletAddress,
                        displayNameHint = targetIdentity.displayName,
                        reinforce = false
                    )
                )
            )

            val securePayload =
                buildChatPayload(
                    keyStore = keyStore,
                    peerName = peerName,
                    peerPublicKey = resolvedPeerPublicKey,
                    message = message
                )

            val packet =
                MeshPacket(
                    packetId = packetId,
                    source = MainActivity.myGlobalPeerId,
                    destination = peerName,
                    type = "CHAT",
                    payload = securePayload.payload,
                    encrypted = securePayload.encrypted
                )

            val targetGlobalId = targetIdentity.globalId.ifBlank { peerGlobalId.orEmpty() }.ifBlank { null }
            val localGlobalId = GlobalMeshIdentityManager.buildGlobalId(keyStore.publicKeyBase64)
            val routeDecision =
                AdaptiveRouteManager.evaluate(
                    context = context,
                    chatId = peerName,
                    globalId = targetGlobalId,
                    routeHint = resolvedPeerIp
                )
            val localPossible =
                routeDecision.routeType == AdaptiveRouteType.LOCAL_MESH_DIRECT ||
                    routeDecision.routeType == AdaptiveRouteType.LOCAL_RELAY ||
                    routeDecision.routeType == AdaptiveRouteType.NEARBY
            if (localPossible) {
                Log.d(
                    "GHALBIT-CHAT-ROUTE",
                    "id=$packetId route=${routeDecision.routeType.name} transport=${routeDecision.transport}"
                )
                val sent =
                    ReliablePacketSender.sendWithRetry(
                        routeDecision.nextHop ?: resolvedPeerIp,
                        packet
                    )
                PacketTraceStore.record(
                    PacketTraceEntry(
                        packetType = "CHAT",
                        sourceNodeId = MainActivity.myGlobalPeerId,
                        targetNodeId = peerName,
                        routeType = routeDecision.routeType.name,
                        transport = routeDecision.transport,
                        deliveryState = if (sent) DeliveryStatus.LOCAL_SENT.name else DeliveryStatus.FAILED.name
                    )
                )
                MeshLogger.i(
                    "DELIVERY",
                    "target=${targetGlobalId ?: peerName} local=true online=false route=${routeDecision.routeType.name} sent=$sent"
                )
                if (sent) {
                    return@withContext ChatSendResult(
                        deliveryStatus = DeliveryStatus.LOCAL_SENT,
                        routeLabel = "Local",
                        successful = true
                    )
                }
            }

            val onlinePresence =
                targetGlobalId?.let { OnlinePresenceManager.checkPeerOnline(context, it) }
            val internetRoute =
                targetGlobalId?.let { OnlinePresenceManager.getOnlineRoute(context, it) }
                    ?: onlinePresence?.route
            if (internetRoute != null) {
                Log.d(
                    "GHALBIT-CHAT-ROUTE",
                    "id=$packetId route=INTERNET_RELAY relay=${internetRoute.relayUrl}"
                )
                val relayResult =
                    OnlineFallbackTransport.sendMessageViaInternet(
                        context = context,
                        route = internetRoute,
                        packetId = packetId,
                        sourceNodeId = MainActivity.myGlobalPeerId,
                        sourceGlobalId = localGlobalId,
                        sourcePublicKeyHash = CallManager.publicKeyHash(keyStore.publicKeyBase64),
                        sourcePublicKey = keyStore.publicKeyBase64,
                        targetNodeId = peerName,
                        targetGlobalId = targetGlobalId,
                        message = securePayload.payload,
                        contentType = if (securePayload.encrypted) "ENCRYPTED_TEXT" else "TEXT",
                        senderDisplayName = resolvedPeerName
                    )
                PacketTraceStore.record(
                    PacketTraceEntry(
                        packetType = "CHAT",
                        sourceNodeId = MainActivity.myGlobalPeerId,
                        targetNodeId = peerName,
                        routeType = AdaptiveRouteType.INTERNET_RELAY.name,
                        transport = "INTERNET_RELAY",
                        deliveryState = if (relayResult.successful) relayResult.status else DeliveryStatus.FAILED.name
                    )
                )
                MeshLogger.i(
                    "DELIVERY",
                    "target=${targetGlobalId ?: peerName} local=false online=${onlinePresence != null} route=internet status=${relayResult.status}"
                )
                if (relayResult.successful) {
                    val deliveryStatus =
                        when (relayResult.status.uppercase()) {
                            "ACCEPTED" -> DeliveryStatus.ACCEPTED_BY_RELAY
                            "QUEUED" -> DeliveryStatus.QUEUED_REMOTE
                            else -> DeliveryStatus.INTERNET_SENT
                        }
                    return@withContext ChatSendResult(
                        deliveryStatus = deliveryStatus,
                        routeLabel = "Online",
                        successful = true,
                        expiresAt = relayResult.expiresAt
                    )
                }
            }

            PendingMessageStore.upsert(
                context = context,
                message =
                    PendingMessage(
                        packetId = packetId,
                        messageId = packetId,
                        chatId = peerName,
                        targetNodeId = peerName,
                        targetGlobalId = targetGlobalId,
                        content = message,
                        expiresAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L,
                        deliveryStatus = DeliveryStatus.PENDING_SYNC,
                        routeHint = resolvedPeerIp,
                        peerPublicKey = resolvedPeerPublicKey,
                        peerWalletAddress = targetIdentity.walletAddress,
                        peerDisplayName = targetIdentity.displayName
                    )
            )
            Log.d("GHALBIT-CHAT-PENDING", "id=$packetId reason=noRoute")
            MeshLogger.w(
                "DELIVERY",
                "target=${targetGlobalId ?: peerName} local=$localPossible online=${onlinePresence != null} route=pending"
            )
            PacketTraceStore.record(
                PacketTraceEntry(
                    packetType = "CHAT",
                    sourceNodeId = MainActivity.myGlobalPeerId,
                    targetNodeId = peerName,
                    routeType = AdaptiveRouteType.PENDING_QUEUE.name,
                    transport = "PENDING_QUEUE",
                    deliveryState = DeliveryStatus.PENDING_SYNC.name
                )
            )
            ChatSendResult(
                deliveryStatus = DeliveryStatus.PENDING_SYNC,
                routeLabel = "Pending",
                successful = false,
                pendingQueued = true
            )
        }
    }

    private fun buildChatPayload(
        keyStore: KeyStoreManager,
        peerName: String,
        peerPublicKey: String?,
        message: String
    ): ChatPayload {
        val plainPayload =
            PacketTtlManager.attachTtl(message)

        val encryptionKey =
            peerPublicKey?.takeIf { it.isNotBlank() }
                ?: keyStore.getPeerKey(peerName)

        if (encryptionKey.isNullOrBlank()) {
            return ChatPayload(
                payload = plainPayload,
                encrypted = false
            )
        }

        return try {
            val sharedSecret =
                CryptoEngine.deriveSharedSecret(
                    keyStore.privateKey,
                    CryptoEngine.base64ToPublicKey(encryptionKey)
                )

            val encryptedBytes =
                CryptoEngine.encrypt(
                    plainPayload.toByteArray(),
                    sharedSecret
                )

            ChatPayload(
                payload = Base64.encodeToString(
                    encryptedBytes,
                    Base64.NO_WRAP
                ),
                encrypted = true
            )
        } catch (e: Exception) {
            MeshLogger.e(
                "CHAT",
                "Encryption failed; sending plaintext fallback",
                e
            )

            ChatPayload(
                payload = plainPayload,
                encrypted = false
            )
        }
    }

    private data class ChatPayload(
        val payload: String,
        val encrypted: Boolean
    )
}
