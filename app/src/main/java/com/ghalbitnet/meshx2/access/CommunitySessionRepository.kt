package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.vpn.GhalbitLocalDatabase
import com.ghalbitnet.meshx2.vpn.VpnLogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object CommunitySessionRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var trustDao: TrustDao? = null

    @Volatile
    private var sessionDao: CommunitySessionDao? = null

    private val trustCache = ConcurrentHashMap<String, ClientTrustEntity>()
    private val sessionCache = ConcurrentHashMap<String, CommunitySessionEntity>()

    fun init(context: Context) {
        if (trustDao != null && sessionDao != null) return
        val db = GhalbitLocalDatabase.getInstance(context.applicationContext)
        trustDao = db.trustDao()
        sessionDao = db.communitySessionDao()
    }

    fun syncFromCurrentState(context: Context) {
        init(context)
        val dao = sessionDao ?: return
        val trust = trustDao ?: return
        val now = System.currentTimeMillis()
        scope.launch {
            val peerRecordsByIp = PeerAuthRegistry.all().associateBy { it.ipAddress }
            val hotspotSessionsByIp = HotspotClientSessionManager.all().associateBy { it.clientIp }
            val unauthorizedByIp = UnauthorizedClientRegistry.all().associateBy { it.ipAddress }
            val ips =
                (peerRecordsByIp.keys + hotspotSessionsByIp.keys + unauthorizedByIp.keys)
                    .filter { it.isNotBlank() }
                    .toSet()

            ips.forEach { ip ->
                val peer = peerRecordsByIp[ip]
                val hotspot = hotspotSessionsByIp[ip]
                val unauthorized = unauthorizedByIp[ip]
                val existing = dao.getSession(ip)
                val trustEntity = trust.getClientTrust(ip)
                val authStatus =
                    peer?.status
                        ?: unauthorized?.status
                        ?: when (hotspot?.status) {
                            GatewayClientPolicy.ClientStatus.AUTHORIZED -> NetworkAccessPolicy.AuthStatus.AUTHORIZED
                            GatewayClientPolicy.ClientStatus.TOKEN_EXPIRED -> NetworkAccessPolicy.AuthStatus.EXPIRED
                            GatewayClientPolicy.ClientStatus.UNAUTHORIZED -> NetworkAccessPolicy.AuthStatus.UNAUTHORIZED
                            else -> NetworkAccessPolicy.AuthStatus.UNKNOWN_NO_HELLO_AUTH
                        }
                val tokenStatus =
                    when {
                        hotspot?.accessToken?.isNotBlank() == true -> "VALID"
                        peer?.accessToken?.isNullOrBlank() == false -> "VALID"
                        authStatus == NetworkAccessPolicy.AuthStatus.EXPIRED -> "EXPIRED"
                        else -> "MISSING"
                    }
                val reconnectCount =
                    ((trustEntity?.reconnectCount ?: 0) + if (existing != null) 1 else 0)
                        .coerceAtMost(999)
                val trustScore =
                    ClientTrustEvaluator.evaluate(
                        context = context,
                        ipAddress = ip,
                        nodeId = peer?.nodeId ?: hotspot?.nodeId ?: unauthorized?.nodeId,
                        authStatus = authStatus,
                        accessTokenStatus = tokenStatus,
                        reconnectCount = reconnectCount,
                        trustEntity = trustEntity
                    )
                val finalTrustEntity =
                    (trustEntity ?: ClientTrustEntity(clientIp = ip))
                        .copy(
                            nodeId = peer?.nodeId ?: hotspot?.nodeId ?: unauthorized?.nodeId,
                            trustLevel = trustScore.level.name,
                            trustScore = trustScore.score,
                            reconnectCount = reconnectCount,
                            updatedAt = now
                        )
                if (trustEntity?.trustLevel != null && trustEntity.trustLevel != finalTrustEntity.trustLevel) {
                    VpnLogManager.info(
                        "CLIENT_TRUST_LEVEL_CHANGED",
                        "client=$ip from=${trustEntity.trustLevel} to=${finalTrustEntity.trustLevel}"
                    )
                }
                trust.upsertClientTrust(finalTrustEntity)
                trustCache[ip] = finalTrustEntity
                VpnLogManager.info(
                    "CLIENT_TRUST_DB_UPDATED",
                    "client=$ip level=${finalTrustEntity.trustLevel} score=${finalTrustEntity.trustScore}"
                )

                val session =
                    CommunitySessionEntity(
                        clientId = ip,
                        nodeId = peer?.nodeId ?: hotspot?.nodeId ?: unauthorized?.nodeId,
                        ipAddress = ip,
                        macAddress = unauthorized?.macAddress ?: HotspotBlocklistAssistant.lookupPossibleMacPublic(ip),
                        trustLevel = trustScore.level.name,
                        authStatus = authStatus.name,
                        accessTokenStatus = tokenStatus,
                        firstSeen = existing?.firstSeen ?: unauthorized?.lastSeen ?: hotspot?.lastSeen ?: peer?.lastSeen ?: now,
                        lastSeen = unauthorized?.lastSeen ?: hotspot?.lastSeen ?: peer?.lastSeen ?: now,
                        uploadBytes = existing?.uploadBytes ?: 0L,
                        downloadBytes = existing?.downloadBytes ?: 0L,
                        totalBytes = existing?.totalBytes ?: 0L,
                        isManualApproved = finalTrustEntity.isManualApproved,
                        isBlocked = finalTrustEntity.isBlocked,
                        isSuspicious = finalTrustEntity.isSuspicious,
                        providerNote = finalTrustEntity.providerNote
                    )
                dao.upsertSession(session)
                sessionCache[ip] = session
                VpnLogManager.info(
                    "COMMUNITY_CLIENT_SESSION_UPDATED",
                    "client=${session.ipAddress} trust=${session.trustLevel} auth=${session.authStatus} token=${session.accessTokenStatus}"
                )
            }
        }
    }

    fun recordProxyTraffic(context: Context, clientIp: String, uploadDelta: Long, downloadDelta: Long) {
        init(context)
        val dao = sessionDao ?: return
        scope.launch {
            val existing = dao.getSession(clientIp) ?: return@launch
            dao.upsertSession(
                existing.copy(
                    uploadBytes = existing.uploadBytes + uploadDelta.coerceAtLeast(0L),
                    downloadBytes = existing.downloadBytes + downloadDelta.coerceAtLeast(0L),
                    totalBytes = existing.totalBytes + uploadDelta.coerceAtLeast(0L) + downloadDelta.coerceAtLeast(0L),
                    lastSeen = System.currentTimeMillis()
                )
            )
            sessionCache[clientIp] =
                existing.copy(
                    uploadBytes = existing.uploadBytes + uploadDelta.coerceAtLeast(0L),
                    downloadBytes = existing.downloadBytes + downloadDelta.coerceAtLeast(0L),
                    totalBytes = existing.totalBytes + uploadDelta.coerceAtLeast(0L) + downloadDelta.coerceAtLeast(0L),
                    lastSeen = System.currentTimeMillis()
                )
        }
    }

    suspend fun allSessions(context: Context): List<CommunitySessionEntity> {
        init(context)
        return (sessionDao?.getAllSessions() ?: emptyList()).also { sessions ->
            sessions.forEach { sessionCache[it.clientId] = it }
        }
    }

    suspend fun session(context: Context, clientId: String): CommunitySessionEntity? {
        init(context)
        return sessionDao?.getSession(clientId)?.also { sessionCache[clientId] = it }
    }

    suspend fun trust(context: Context, clientIp: String): ClientTrustEntity? {
        init(context)
        return trustDao?.getClientTrust(clientIp)?.also { trustCache[clientIp] = it }
    }

    fun cachedTrust(clientIp: String): ClientTrustEntity? = trustCache[clientIp]

    fun cachedSession(clientIp: String): CommunitySessionEntity? = sessionCache[clientIp]

    fun saveTrust(context: Context, entity: ClientTrustEntity) {
        init(context)
        val dao = trustDao ?: return
        trustCache[entity.clientIp] = entity
        scope.launch {
            dao.upsertClientTrust(entity)
            VpnLogManager.info(
                "CLIENT_TRUST_DB_UPDATED",
                "client=${entity.clientIp} level=${entity.trustLevel} score=${entity.trustScore}"
            )
        }
    }

    fun logAction(context: Context, action: ProviderActionLogEntity) {
        init(context)
        val dao = trustDao ?: return
        scope.launch {
            dao.insertActionLog(action)
            VpnLogManager.info(
                "PROVIDER_ACTION_DB_SAVED",
                "action=${action.actionType} client=${action.clientIp}"
            )
        }
    }

    fun providerId(context: Context): String {
        return GlobalMeshIdentityManager.buildGlobalId(KeyStoreManager(context).publicKeyBase64)
    }
}
