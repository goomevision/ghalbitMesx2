package com.ghalbitnet.meshx2.vpn

import java.util.concurrent.ConcurrentHashMap

object UsageMeter {

    enum class Direction {
        UPLOAD,
        DOWNLOAD
    }

    data class UsageRecord(
        val nodeId: String,
        val sessionId: String,
        val packetId: String,
        val timestamp: Long,
        val ipVersion: Int,
        val protocol: String,
        val sourceIp: String?,
        val sourcePort: Int?,
        val destinationIp: String?,
        val destinationPort: Int?,
        val packetBytes: Int,
        val direction: Direction,
        val routeMode: String,
        val counted: Boolean = true
    )

    @Volatile
    private var currentSession: UserUsageSession? = null
    @Volatile
    private var currentCounter: UsageCounter? = null
    private val recentRecords = ArrayDeque<UsageRecord>()
    private const val MAX_RECORDS = 400
    private val sessionSeen = ConcurrentHashMap<String, Boolean>()
    private val lastUserIdentifiedAt = ConcurrentHashMap<String, Long>()
    @Volatile
    private var lastSummaryAt: Long = 0L
    @Volatile
    private var lastPassiveUsageLogAt: Long = 0L
    private const val USER_IDENTIFIED_INTERVAL_MS = 30_000L
    private const val PASSIVE_USAGE_LOG_INTERVAL_MS = 5_000L

    fun ensureSession(
        nodeId: String,
        sessionId: String
    ) {
        val existing = currentSession
        if (existing?.sessionId == sessionId && existing.nodeId == nodeId) {
            currentSession = existing.copy(lastSeen = System.currentTimeMillis())
            return
        }
        if (sessionSeen.putIfAbsent(sessionId, true) == null) {
            VpnLogManager.info("USAGE_SESSION_STARTED", "nodeId=$nodeId sessionId=$sessionId")
            UsageRepository.startSession(
                nodeId = nodeId,
                mode = UsageRepository.currentOperatingMode(),
                sessionId = sessionId
            )
        }
        currentSession =
            UserUsageSession(
                nodeId = nodeId,
                sessionId = sessionId,
                startedAt = System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis()
            )
        currentCounter =
            UsageCounter(
                nodeId = nodeId,
                sessionId = sessionId,
                updatedAt = System.currentTimeMillis()
            )
    }

    fun maybeLogUserIdentified(
        nodeId: String,
        sessionId: String
    ) {
        val key = "$nodeId|$sessionId"
        val now = System.currentTimeMillis()
        val last = lastUserIdentifiedAt[key] ?: 0L
        if (last == 0L || now - last >= USER_IDENTIFIED_INTERVAL_MS) {
            lastUserIdentifiedAt[key] = now
            VpnLogManager.info("USER_IDENTIFIED", "nodeId=$nodeId sessionId=$sessionId")
        }
    }

    fun record(record: UsageRecord) {
        ensureSession(record.nodeId, record.sessionId)
        if (recentRecords.size >= MAX_RECORDS) {
            recentRecords.removeFirst()
        }
        recentRecords.addLast(record)
        val current =
            currentCounter ?: UsageCounter(
                nodeId = record.nodeId,
                sessionId = record.sessionId
            )
        val updated =
            current.copy(
                totalPackets = current.totalPackets + 1,
                totalUploadBytes =
                    current.totalUploadBytes +
                        if (record.direction == Direction.UPLOAD) record.packetBytes else 0,
                totalDownloadBytes =
                    current.totalDownloadBytes +
                        if (record.direction == Direction.DOWNLOAD) record.packetBytes else 0,
                tcpPackets = current.tcpPackets + if (record.protocol == "TCP") 1 else 0,
                udpPackets = current.udpPackets + if (record.protocol == "UDP") 1 else 0,
                icmpPackets = current.icmpPackets + if (record.protocol == "ICMP") 1 else 0,
                ipv6Packets = current.ipv6Packets + if (record.ipVersion == 6) 1 else 0,
                unknownPackets = current.unknownPackets + if (record.protocol == "UNKNOWN") 1 else 0,
                updatedAt = System.currentTimeMillis()
            )
        currentCounter = updated
        UsageRepository.updateTotals(updated)
        val now = System.currentTimeMillis()
        val shouldSummarize =
            updated.totalPackets % 100L == 0L || now - lastSummaryAt >= 1_000L
        if (shouldSummarize) {
            lastSummaryAt = now
            VpnLogManager.info(
                "USAGE_PACKET_COUNTED",
                "session=${record.sessionId} packets=${updated.totalPackets} protocol=${record.protocol} lastBytes=${record.packetBytes}"
            )
            VpnLogManager.info(
                "USAGE_TOTAL_UPDATED",
                "session=${updated.sessionId} packets=${updated.totalPackets} up=${updated.totalUploadBytes} down=${updated.totalDownloadBytes} tcp=${updated.tcpPackets} udp=${updated.udpPackets}"
            )
        }
    }

    fun recordDownloadDelta(
        nodeId: String,
        sessionId: String,
        bytes: Long
    ) {
        if (bytes <= 0L) return
        ensureSession(nodeId, sessionId)
        val current =
            currentCounter ?: UsageCounter(
                nodeId = nodeId,
                sessionId = sessionId
            )
        val updated =
            current.copy(
                totalDownloadBytes = current.totalDownloadBytes + bytes,
                updatedAt = System.currentTimeMillis()
            )
        currentCounter = updated
        UsageRepository.recordDelta(
            sessionId = sessionId,
            upDelta = 0L,
            downDelta = bytes,
            source = "DOWNLOAD_MONITOR"
        )
        UsageRepository.updateTotals(updated)
        VpnLogManager.info(
            "USAGE_DOWNLOAD_UPDATED",
            "session=$sessionId downDelta=$bytes totalDown=${updated.totalDownloadBytes}"
        )
        VpnLogManager.info(
            "USAGE_TOTAL_BIDIRECTIONAL",
            "session=$sessionId packets=${updated.totalPackets} up=${updated.totalUploadBytes} down=${updated.totalDownloadBytes} total=${updated.totalUploadBytes + updated.totalDownloadBytes} tcp=${updated.tcpPackets} udp=${updated.udpPackets}"
        )
    }

    fun recordNetworkDeltas(
        nodeId: String,
        sessionId: String,
        uploadBytes: Long,
        downloadBytes: Long
    ) {
        if (uploadBytes <= 0L && downloadBytes <= 0L) return
        ensureSession(nodeId, sessionId)
        val current =
            currentCounter ?: UsageCounter(
                nodeId = nodeId,
                sessionId = sessionId
            )
        val updated =
            current.copy(
                totalUploadBytes = current.totalUploadBytes + uploadBytes.coerceAtLeast(0L),
                totalDownloadBytes = current.totalDownloadBytes + downloadBytes.coerceAtLeast(0L),
                updatedAt = System.currentTimeMillis()
            )
        currentCounter = updated
        UsageRepository.recordDelta(
            sessionId = sessionId,
            upDelta = uploadBytes.coerceAtLeast(0L),
            downDelta = downloadBytes.coerceAtLeast(0L),
            source = "PASSIVE"
        )
        UsageRepository.updateTotals(updated)
        val now = System.currentTimeMillis()
        if (now - lastPassiveUsageLogAt >= PASSIVE_USAGE_LOG_INTERVAL_MS) {
            lastPassiveUsageLogAt = now
            if (downloadBytes > 0L) {
                VpnLogManager.info(
                    "USAGE_DOWNLOAD_UPDATED",
                    "session=$sessionId downDelta=$downloadBytes totalDown=${updated.totalDownloadBytes}"
                )
            }
            VpnLogManager.info(
                "PASSIVE_USAGE_UPDATED",
                "session=$sessionId upDelta=$uploadBytes downDelta=$downloadBytes"
            )
            VpnLogManager.info(
                "PASSIVE_USAGE_SUMMARY",
                "session=$sessionId up=${updated.totalUploadBytes} down=${updated.totalDownloadBytes} total=${updated.totalUploadBytes + updated.totalDownloadBytes} packets=${updated.totalPackets} tcp=${updated.tcpPackets} udp=${updated.udpPackets}"
            )
            VpnLogManager.info(
                "USAGE_TOTAL_BIDIRECTIONAL",
                "session=$sessionId packets=${updated.totalPackets} up=${updated.totalUploadBytes} down=${updated.totalDownloadBytes} total=${updated.totalUploadBytes + updated.totalDownloadBytes} tcp=${updated.tcpPackets} udp=${updated.udpPackets}"
            )
        }
    }

    fun snapshotCounter(): UsageCounter? = currentCounter

    fun snapshotRecords(): List<UsageRecord> = recentRecords.toList()

    fun closeActiveSession() {
        currentCounter?.let { UsageRepository.updateTotals(it) }
        currentSession?.let { UsageRepository.closeSession(it.sessionId) }
        currentSession = null
        currentCounter = null
    }
}
