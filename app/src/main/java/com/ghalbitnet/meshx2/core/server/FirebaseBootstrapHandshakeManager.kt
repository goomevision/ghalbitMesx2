package com.ghalbitnet.meshx2.core.server

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.ghalbitnet.meshx2.core.log.MeshLogger
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

object FirebaseBootstrapHandshakeManager {

    private const val PREFS_NAME = "firebase_bootstrap_handshake"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_SUMMARY_JSON = "summary_json"

    data class Snapshot(
        val lastSync: Long,
        val pendingCount: Int,
        val ackedCount: Int,
        val lastAckPeer: String,
        val detail: String
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun snapshot(context: Context): Snapshot {
        val raw = prefs(context).getString(KEY_SUMMARY_JSON, "").orEmpty()
        if (raw.isBlank()) {
            return Snapshot(
                lastSync = 0L,
                pendingCount = 0,
                ackedCount = 0,
                lastAckPeer = "",
                detail = "Handshake bootstrap belum dimulai."
            )
        }
        return runCatching {
            val root = JSONObject(raw)
            Snapshot(
                lastSync = root.optLong("lastSync", 0L),
                pendingCount = root.optInt("pendingCount", 0),
                ackedCount = root.optInt("ackedCount", 0),
                lastAckPeer = root.optString("lastAckPeer"),
                detail = root.optString("detail", "Handshake bootstrap belum dimulai.")
            )
        }.getOrDefault(
            Snapshot(
                lastSync = 0L,
                pendingCount = 0,
                ackedCount = 0,
                lastAckPeer = "",
                detail = "Handshake bootstrap belum dimulai."
            )
        )
    }

    suspend fun refresh(
        context: Context,
        localGlobalId: String,
        peers: List<FirebaseBootstrapPeerManager.BootstrapPeer>
    ): Snapshot {
        if (!FirebaseRemoteSyncManager.isReady(context)) {
            return snapshot(context)
        }

        return runCatching {
            val firestore = FirebaseFirestore.getInstance()
            val candidatePeers =
                peers
                    .filter { it.globalId.isNotBlank() && it.globalId != localGlobalId }
                    .take(3)

            candidatePeers.forEach { peer ->
                val docId = buildDocId(localGlobalId, peer.globalId)
                firestore.collection("bootstrapHandshakes")
                    .document(docId)
                    .set(
                        mapOf(
                            "requesterGlobalId" to localGlobalId,
                            "targetGlobalId" to peer.globalId,
                            "targetAlias" to peer.alias,
                            "status" to "PENDING",
                            "createdAt" to System.currentTimeMillis(),
                            "updatedAt" to System.currentTimeMillis(),
                            "source" to "ghalbit-app"
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .await()
            }

            val incoming =
                firestore.collection("bootstrapHandshakes")
                    .whereEqualTo("targetGlobalId", localGlobalId)
                    .get()
                    .await()

            var ackedIncoming = 0
            incoming.documents.forEach { document ->
                val status = document.getString("status").orEmpty()
                if (status.equals("PENDING", ignoreCase = true)) {
                    document.reference.set(
                        mapOf(
                            "status" to "ACKED",
                            "responderGlobalId" to localGlobalId,
                            "ackedAt" to System.currentTimeMillis(),
                            "updatedAt" to System.currentTimeMillis()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
                    ackedIncoming += 1
                }
            }

            val outgoing =
                firestore.collection("bootstrapHandshakes")
                    .whereEqualTo("requesterGlobalId", localGlobalId)
                    .get()
                    .await()

            val outgoingDocs = outgoing.documents
            val pendingCount =
                outgoingDocs.count {
                    it.getString("status").orEmpty().equals("PENDING", ignoreCase = true)
                }
            val ackedDocs =
                outgoingDocs.filter {
                    it.getString("status").orEmpty().equals("ACKED", ignoreCase = true)
                }
            val lastAckPeer =
                ackedDocs.maxByOrNull { it.getLong("ackedAt") ?: 0L }
                    ?.getString("targetAlias")
                    .orEmpty()

            val detail =
                when {
                    ackedDocs.isNotEmpty() ->
                        "Handshake bootstrap aktif. ${ackedDocs.size} peer sudah membalas, terakhir ${lastAckPeer.ifBlank { "peer bootstrap" }}."
                    pendingCount > 0 ->
                        "Handshake bootstrap dikirim. Menunggu $pendingCount balasan peer awal."
                    ackedIncoming > 0 ->
                        "Handshake masuk sudah dibalas. Sistem siap saling mengenali peer awal."
                    else ->
                        "Belum ada peer bootstrap yang merespon."
                }

            val snapshot =
                Snapshot(
                    lastSync = System.currentTimeMillis(),
                    pendingCount = pendingCount,
                    ackedCount = ackedDocs.size,
                    lastAckPeer = lastAckPeer,
                    detail = detail
                )
            save(context, snapshot)
            MeshLogger.i("BOOTSTRAP", detail)
            snapshot
        }.getOrElse { error ->
            MeshLogger.w("BOOTSTRAP", "Handshake bootstrap gagal: ${error.message ?: "unknown"}")
            snapshot(context)
        }
    }

    private fun save(
        context: Context,
        snapshot: Snapshot
    ) {
        val json =
            JSONObject()
                .put("lastSync", snapshot.lastSync)
                .put("pendingCount", snapshot.pendingCount)
                .put("ackedCount", snapshot.ackedCount)
                .put("lastAckPeer", snapshot.lastAckPeer)
                .put("detail", snapshot.detail)
        prefs(context)
            .edit()
            .putLong(KEY_LAST_SYNC, snapshot.lastSync)
            .putString(KEY_SUMMARY_JSON, json.toString())
            .apply()
    }

    private fun buildDocId(
        requesterGlobalId: String,
        targetGlobalId: String
    ): String {
        return "${requesterGlobalId}_${targetGlobalId}".replace(":", "_")
    }
}
