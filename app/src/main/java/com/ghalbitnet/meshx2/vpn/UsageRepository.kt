package com.ghalbitnet.meshx2.vpn

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object UsageRepository {

    data class UsageSummary(
        val activeSession: UsageSessionEntity?,
        val unsyncedSessionCount: Int,
        val unsyncedDeltaCount: Int
    )

    data class UsageDebugSummary(
        val activeSession: UsageSessionEntity?,
        val recentSessions: List<UsageSessionEntity>,
        val unsyncedSessionCount: Int,
        val unsyncedDeltaCount: Int
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var dao: UsageDao? = null

    @Volatile
    private var activeOperatingMode: String = "UNKNOWN"

    fun init(context: Context) {
        if (dao != null) return
        dao = GhalbitLocalDatabase.getInstance(context.applicationContext).usageDao()
    }

    fun setOperatingMode(mode: String) {
        activeOperatingMode = mode
    }

    fun currentOperatingMode(): String = activeOperatingMode

    fun startSession(
        nodeId: String,
        mode: String,
        sessionId: String
    ): String {
        val usageDao = dao ?: return sessionId
        scope.launch {
            runCatching {
                val existing = usageDao.getSession(sessionId)
                if (existing == null) {
                    usageDao.insertSession(
                        UsageSessionEntity(
                            sessionId = sessionId,
                            nodeId = nodeId,
                            startTime = System.currentTimeMillis(),
                            operatingMode = mode,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    VpnLogManager.info(
                        "USAGE_DB_SESSION_CREATED",
                        "sessionId=$sessionId nodeId=$nodeId mode=$mode"
                    )
                }
            }.onFailure {
                VpnLogManager.error(
                    "USAGE_DB_WRITE_FAILED",
                    "Gagal membuat usage session $sessionId.",
                    it
                )
            }
        }
        return sessionId
    }

    fun recordDelta(
        sessionId: String,
        upDelta: Long,
        downDelta: Long,
        source: String
    ) {
        if (upDelta <= 0L && downDelta <= 0L) return
        val usageDao = dao ?: return
        scope.launch {
            runCatching {
                val id =
                    usageDao.insertDelta(
                        UsageDeltaEntity(
                            sessionId = sessionId,
                            timestamp = System.currentTimeMillis(),
                            uploadDelta = upDelta,
                            downloadDelta = downDelta,
                            totalDelta = upDelta + downDelta,
                            source = source
                        )
                    )
                VpnLogManager.info(
                    "USAGE_DB_DELTA_INSERTED",
                    "sessionId=$sessionId deltaId=$id up=$upDelta down=$downDelta source=$source"
                )
            }.onFailure {
                VpnLogManager.error(
                    "USAGE_DB_WRITE_FAILED",
                    "Gagal menyimpan usage delta untuk $sessionId.",
                    it
                )
            }
        }
    }

    fun updateTotals(
        counter: UsageCounter,
        providerNodeId: String? = null,
        gatewayNodeId: String? = null
    ) {
        val usageDao = dao ?: return
        scope.launch {
            runCatching {
                usageDao.updateSessionTotals(
                    sessionId = counter.sessionId,
                    totalUploadBytes = counter.totalUploadBytes,
                    totalDownloadBytes = counter.totalDownloadBytes,
                    totalBytes = counter.totalUploadBytes + counter.totalDownloadBytes,
                    packetCount = counter.totalPackets,
                    tcpCount = counter.tcpPackets,
                    udpCount = counter.udpPackets,
                    icmpCount = counter.icmpPackets,
                    ipv6Count = counter.ipv6Packets,
                    unknownCount = counter.unknownPackets,
                    operatingMode = activeOperatingMode,
                    providerNodeId = providerNodeId,
                    gatewayNodeId = gatewayNodeId,
                    updatedAt = System.currentTimeMillis()
                )
                VpnLogManager.info(
                    "USAGE_DB_TOTAL_UPDATED",
                    "sessionId=${counter.sessionId} packets=${counter.totalPackets} up=${counter.totalUploadBytes} down=${counter.totalDownloadBytes}"
                )
            }.onFailure {
                VpnLogManager.error(
                    "USAGE_DB_WRITE_FAILED",
                    "Gagal memperbarui total usage untuk ${counter.sessionId}.",
                    it
                )
            }
        }
    }

    fun closeSession(sessionId: String) {
        val usageDao = dao ?: return
        scope.launch {
            runCatching {
                usageDao.closeSession(
                    sessionId = sessionId,
                    endTime = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                VpnLogManager.info(
                    "USAGE_DB_SESSION_CLOSED",
                    "sessionId=$sessionId"
                )
            }.onFailure {
                VpnLogManager.error(
                    "USAGE_DB_WRITE_FAILED",
                    "Gagal menutup usage session $sessionId.",
                    it
                )
            }
        }
    }

    suspend fun getSummary(): UsageSummary {
        val usageDao = dao
        if (usageDao == null) {
            return UsageSummary(
                activeSession = null,
                unsyncedSessionCount = 0,
                unsyncedDeltaCount = 0
            )
        }
        val active = usageDao.getActiveSession()
        val unsyncedSessions = usageDao.getUnsyncedSessions()
        val unsyncedDeltas = usageDao.getUnsyncedDeltas()
        return UsageSummary(
            activeSession = active,
            unsyncedSessionCount = unsyncedSessions.size,
            unsyncedDeltaCount = unsyncedDeltas.size
        )
    }

    suspend fun getRecentSessions(limit: Int): List<UsageSessionEntity> {
        val usageDao = dao ?: return emptyList()
        return usageDao.getRecentSessions(limit)
    }

    suspend fun getSessionDeltas(sessionId: String): List<UsageDeltaEntity> {
        val usageDao = dao ?: return emptyList()
        return usageDao.getSessionDeltas(sessionId)
    }

    suspend fun getActiveSession(): UsageSessionEntity? {
        val usageDao = dao ?: return null
        return usageDao.getActiveSession()
    }

    suspend fun closeActiveSession(): UsageSessionEntity? {
        val usageDao = dao ?: return null
        val active = usageDao.getActiveSession() ?: return null
        runCatching {
            usageDao.closeSession(
                sessionId = active.sessionId,
                endTime = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            VpnLogManager.info(
                "USAGE_DB_SESSION_CLOSED",
                "sessionId=${active.sessionId}"
            )
        }.onFailure {
            VpnLogManager.error(
                "USAGE_DB_WRITE_FAILED",
                "Gagal menutup usage session aktif ${active.sessionId}.",
                it
            )
        }
        return usageDao.getSession(active.sessionId)
    }

    suspend fun getDebugSummary(limit: Int = 20): UsageDebugSummary {
        val usageDao = dao
        if (usageDao == null) {
            return UsageDebugSummary(
                activeSession = null,
                recentSessions = emptyList(),
                unsyncedSessionCount = 0,
                unsyncedDeltaCount = 0
            )
        }
        val active = usageDao.getActiveSession()
        val recent = usageDao.getRecentSessions(limit)
        return UsageDebugSummary(
            activeSession = active,
            recentSessions = recent,
            unsyncedSessionCount = usageDao.getUnsyncedSessions().size,
            unsyncedDeltaCount = usageDao.getUnsyncedDeltas().size
        )
    }
}
