package com.ghalbitnet.meshx2.core.server

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.ghalbitnet.meshx2.chat.GlobalContactDirectory
import com.ghalbitnet.meshx2.core.log.MeshLogger
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

object FirebaseBootstrapPeerManager {

    private const val PREFS_NAME = "firebase_bootstrap_peers"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_PEERS_JSON = "peers_json"

    data class BootstrapPeer(
        val globalId: String,
        val alias: String,
        val trustScore: Int,
        val online: Boolean,
        val providerReady: Boolean,
        val status: String
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun lastSync(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_SYNC, 0L)
    }

    fun cachedPeers(context: Context): List<BootstrapPeer> {
        val raw = prefs(context).getString(KEY_PEERS_JSON, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        BootstrapPeer(
                            globalId = item.optString("globalId"),
                            alias = item.optString("alias"),
                            trustScore = item.optInt("trustScore", 0),
                            online = item.optBoolean("online", false),
                            providerReady = item.optBoolean("providerReady", false),
                            status = item.optString("status")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun refresh(context: Context): List<BootstrapPeer> {
        if (!FirebaseRemoteSyncManager.isReady(context)) {
            return cachedPeers(context)
        }

        return runCatching {
            val firestore = FirebaseFirestore.getInstance()
            val bootstrapDoc = firestore.collection("bootstrapState").document("default").get().await()
            val bootstrapIds =
                buildSet {
                    (bootstrapDoc.get("recommendedPeerIds") as? List<*>)?.forEach { raw ->
                        raw?.toString()?.trim()?.takeIf(String::isNotBlank)?.let { add(it) }
                    }
                    (bootstrapDoc.get("recommendedGatewayIds") as? List<*>)?.forEach { raw ->
                        raw?.toString()?.trim()?.takeIf(String::isNotBlank)?.let { add(it) }
                    }
                }

            if (bootstrapIds.isEmpty()) {
                save(context, emptyList())
                return@runCatching emptyList()
            }

            val peers =
                bootstrapIds.mapNotNull { globalId ->
                    val doc = firestore.collection("nodeRegistry").document(globalId).get().await()
                    if (!doc.exists()) {
                        null
                    } else {
                        BootstrapPeer(
                            globalId = globalId,
                            alias = buildAlias(globalId, doc.getString("status").orEmpty()),
                            trustScore = (doc.getLong("trustScore") ?: 0L).toInt(),
                            online = doc.getBoolean("online") == true,
                            providerReady = doc.getBoolean("providerReady") == true,
                            status = doc.getString("status").orEmpty()
                        )
                    }
                }.sortedWith(
                    compareByDescending<BootstrapPeer> { it.providerReady }
                        .thenByDescending { it.online }
                        .thenByDescending { it.trustScore }
                        .thenBy { it.alias.lowercase() }
                )

            seedContacts(context, peers)
            save(context, peers)
            MeshLogger.i("BOOTSTRAP", "Firebase bootstrap peers=${peers.size}")
            peers
        }.getOrElse { error ->
            MeshLogger.w("BOOTSTRAP", "Bootstrap refresh gagal: ${error.message ?: "unknown"}")
            cachedPeers(context)
        }
    }

    private fun seedContacts(
        context: Context,
        peers: List<BootstrapPeer>
    ) {
        peers.forEach { peer ->
            val existing = GlobalContactDirectory.find(context, peer.globalId)
            if (existing == null) {
                GlobalContactDirectory.saveContact(
                    context = context,
                    globalId = peer.globalId,
                    alias = peer.alias,
                    group = "BOOTSTRAP",
                    note = buildNote(peer)
                )
            }
        }
    }

    private fun save(
        context: Context,
        peers: List<BootstrapPeer>
    ) {
        val array =
            JSONArray().apply {
                peers.forEach { peer ->
                    put(
                        JSONObject()
                            .put("globalId", peer.globalId)
                            .put("alias", peer.alias)
                            .put("trustScore", peer.trustScore)
                            .put("online", peer.online)
                            .put("providerReady", peer.providerReady)
                            .put("status", peer.status)
                    )
                }
            }
        prefs(context)
            .edit()
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .putString(KEY_PEERS_JSON, array.toString())
            .apply()
    }

    private fun buildAlias(
        globalId: String,
        status: String
    ): String {
        val suffix = globalId.takeLast(6)
        return when {
            status.contains("PROVIDER", ignoreCase = true) -> "Gateway $suffix"
            status.contains("ONLINE", ignoreCase = true) -> "Peer $suffix"
            else -> "Bootstrap $suffix"
        }
    }

    private fun buildNote(peer: BootstrapPeer): String {
        return buildString {
            append("Peer awal dari Firebase")
            append(" | trust ")
            append(peer.trustScore)
            if (peer.providerReady) {
                append(" | siap penyedia")
            }
            if (peer.online) {
                append(" | online")
            }
        }
    }
}
