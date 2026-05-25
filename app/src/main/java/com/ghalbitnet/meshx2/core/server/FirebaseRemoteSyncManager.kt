package com.ghalbitnet.meshx2.core.server

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.firestore.FirebaseFirestore
import com.ghalbitnet.meshx2.chat.GlobalContactDirectory
import com.ghalbitnet.meshx2.core.network.InternetProviderAccessManager
import com.ghalbitnet.meshx2.core.network.InternetProviderConventionManager
import com.ghalbitnet.meshx2.core.network.InternetProviderReadinessManager
import com.ghalbitnet.meshx2.settings.OnboardingManager
import com.ghalbitnet.meshx2.model.MeshNode
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

object FirebaseRemoteSyncManager {

    private const val PREFS_NAME = "firebase_remote_sync"
    private const val KEY_LAST_SUCCESS = "last_success"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_LAST_REMOTE_ONLINE_COUNT = "last_remote_online_count"
    private const val KEY_REMOTE_ONLINE_IDS = "remote_online_ids"
    private const val KEY_BRIDGE_POLICY_JSON = "bridge_policy_json"
    private const val KEY_ECONOMY_POLICY_JSON = "economy_policy_json"
    private const val KEY_WALLET_BALANCE_PREFIX = "wallet_balance_"
    private const val KEY_WALLET_OWNER_CLASS_PREFIX = "wallet_owner_class_"
    private const val KEY_PEER_POLICY_JSON_PREFIX = "peer_policy_json_"
    private const val KEY_PARTICIPANT_JSON_PREFIX = "participant_json_"

    data class SyncResult(
        val success: Boolean,
        val detail: String,
        val remoteOnlineCount: Int,
        val onlineContactIds: Set<String>
    )

    data class CachedPeerPolicy(
        val globalId: String,
        val tier: String,
        val customDailyQuotaMb: Int?
    )

    data class CachedParticipantProfile(
        val globalId: String,
        val online: Boolean,
        val contributionApproved: Boolean,
        val onboardingCompleted: Boolean,
        val participantRoles: Set<String>,
        val providerReady: Boolean,
        val providerActive: Boolean,
        val hotspotActive: Boolean
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isReady(context: Context): Boolean {
        return FirebaseBootstrap.ensureInitialized(context)
    }

    fun cachedBridgePolicyJson(context: Context): String {
        return prefs(context).getString(KEY_BRIDGE_POLICY_JSON, "").orEmpty()
    }

    fun cachedEconomyPolicyJson(context: Context): String {
        return prefs(context).getString(KEY_ECONOMY_POLICY_JSON, "").orEmpty()
    }

    fun remoteOnlineIds(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_REMOTE_ONLINE_IDS, emptySet()) ?: emptySet()
    }

    fun lastSuccess(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_SUCCESS, 0L)
    }

    fun lastError(context: Context): String {
        return prefs(context).getString(KEY_LAST_ERROR, "").orEmpty()
    }

    fun cachedWalletBalance(
        context: Context,
        globalId: String
    ): Double? {
        val cleanId = GlobalContactDirectory.normalizeGlobalId(globalId)
        val key = "$KEY_WALLET_BALANCE_PREFIX$cleanId"
        if (!prefs(context).contains(key)) {
            return null
        }
        return prefs(context).getFloat(key, 0f).toDouble()
    }

    fun cachedWalletOwnerClass(
        context: Context,
        globalId: String
    ): String? {
        val cleanId = GlobalContactDirectory.normalizeGlobalId(globalId)
        return prefs(context).getString("$KEY_WALLET_OWNER_CLASS_PREFIX$cleanId", "").orEmpty()
            .ifBlank { null }
    }

    fun cachedPeerPolicy(
        context: Context,
        globalId: String
    ): CachedPeerPolicy? {
        val cleanId = GlobalContactDirectory.normalizeGlobalId(globalId)
        val raw = prefs(context).getString("$KEY_PEER_POLICY_JSON_PREFIX$cleanId", "").orEmpty()
        if (raw.isBlank()) {
            return null
        }
        return runCatching {
            val root = JSONObject(raw)
            CachedPeerPolicy(
                globalId = cleanId,
                tier = root.optString("tier", "STANDARD"),
                customDailyQuotaMb =
                    if (root.has("customDailyQuotaMb") && !root.isNull("customDailyQuotaMb")) {
                        root.optInt("customDailyQuotaMb").takeIf { it > 0 }
                    } else {
                        null
                    }
            )
        }.getOrNull()
    }

    fun cachedParticipantProfile(
        context: Context,
        globalId: String
    ): CachedParticipantProfile? {
        val cleanId = GlobalContactDirectory.normalizeGlobalId(globalId)
        val raw = prefs(context).getString("$KEY_PARTICIPANT_JSON_PREFIX$cleanId", "").orEmpty()
        if (raw.isBlank()) {
            return null
        }
        return runCatching {
            val root = JSONObject(raw)
            CachedParticipantProfile(
                globalId = cleanId,
                online = root.optBoolean("online", false),
                contributionApproved = root.optBoolean("contributionApproved", false),
                onboardingCompleted = root.optBoolean("onboardingCompleted", false),
                participantRoles = parseJsonStringSet(root.optJSONArray("participantRoles")),
                providerReady = root.optBoolean("providerReady", false),
                providerActive = root.optBoolean("providerActive", false),
                hotspotActive = root.optBoolean("hotspotActive", false)
            )
        }.getOrNull()
    }

    suspend fun refreshControlData(
        context: Context,
        globalIds: Set<String>
    ) {
        if (!FirebaseBootstrap.ensureInitialized(context)) {
            return
        }
        FirebaseEconomySyncManager.syncPending(context)
        val cleanIds =
            globalIds
                .map { GlobalContactDirectory.normalizeGlobalId(it) }
                .filter { it.isNotBlank() }
                .toSet()

        if (cleanIds.isEmpty()) {
            return
        }

        val firestore = FirebaseFirestore.getInstance()
        cleanIds.forEach { globalId ->
            runCatching {
                val walletDoc = firestore.collection("wallets").document(globalId).get().await()
                if (walletDoc.exists()) {
                    val balance = walletDoc.getDouble("balance") ?: 0.0
                    val ownerClass = walletDoc.getString("ownerClass").orEmpty()
                    prefs(context)
                        .edit()
                        .putFloat("$KEY_WALLET_BALANCE_PREFIX$globalId", balance.toFloat())
                        .putString("$KEY_WALLET_OWNER_CLASS_PREFIX$globalId", ownerClass)
                        .apply()
                }

                val peerPolicyDoc =
                    firestore.collection("peerPolicies").document(globalId).get().await()
                if (peerPolicyDoc.exists()) {
                    prefs(context)
                        .edit()
                        .putString(
                            "$KEY_PEER_POLICY_JSON_PREFIX$globalId",
                            peerPolicyDoc.data.orEmpty().toJson()
                        )
                        .apply()
                }

                val providerProfileDoc =
                    firestore.collection("providerProfiles").document(globalId).get().await()
                val presenceSnapshot =
                    FirebaseDatabase.getInstance().getReference("presence").child(globalId).get().await()
                val participantPayload =
                    mutableMapOf<String, Any>(
                        "globalId" to globalId,
                        "online" to (presenceSnapshot.child("online").getValue(Boolean::class.java) == true),
                        "contributionApproved" to false,
                        "onboardingCompleted" to false,
                        "participantRoles" to emptyList<String>(),
                        "providerReady" to false,
                        "providerActive" to false,
                        "hotspotActive" to false
                    )

                val presenceRoles =
                    presenceSnapshot.child("participantRoles").children.mapNotNull {
                        it.getValue(String::class.java)?.trim()?.takeIf { value -> value.isNotBlank() }
                    }
                if (presenceRoles.isNotEmpty()) {
                    participantPayload["participantRoles"] = presenceRoles
                }
                participantPayload["contributionApproved"] =
                    presenceSnapshot.child("contributionApproved").getValue(Boolean::class.java) == true
                participantPayload["onboardingCompleted"] =
                    presenceSnapshot.child("onboardingCompleted").getValue(Boolean::class.java) == true
                participantPayload["providerReady"] =
                    presenceSnapshot.child("providerReady").getValue(Boolean::class.java) == true
                participantPayload["providerActive"] =
                    presenceSnapshot.child("providerActive").getValue(Boolean::class.java) == true
                participantPayload["hotspotActive"] =
                    presenceSnapshot.child("hotspotActive").getValue(Boolean::class.java) == true

                if (providerProfileDoc.exists()) {
                    providerProfileDoc.getBoolean("contributionApproved")?.let {
                        participantPayload["contributionApproved"] = it
                    }
                    providerProfileDoc.getBoolean("onboardingCompleted")?.let {
                        participantPayload["onboardingCompleted"] = it
                    }
                    providerProfileDoc.getBoolean("providerReady")?.let {
                        participantPayload["providerReady"] = it
                    }
                    providerProfileDoc.getBoolean("providerActive")?.let {
                        participantPayload["providerActive"] = it
                    }
                    providerProfileDoc.getBoolean("hotspotActive")?.let {
                        participantPayload["hotspotActive"] = it
                    }
                    val profileRoles =
                        providerProfileDoc.get("participantRoles") as? List<*>
                    if (!profileRoles.isNullOrEmpty()) {
                        participantPayload["participantRoles"] =
                            profileRoles.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
                    }
                }

                prefs(context)
                    .edit()
                    .putString("$KEY_PARTICIPANT_JSON_PREFIX$globalId", participantPayload.toJson())
                    .apply()
            }
        }
    }

    suspend fun syncProviderProfile(
        context: Context,
        globalId: String
    ) {
        if (!FirebaseBootstrap.ensureInitialized(context)) {
            return
        }

        runCatching {
            val profile = InternetProviderAccessManager.snapshot(context)
            val readiness = InternetProviderReadinessManager.snapshot(context)
            val onboarding = OnboardingManager.snapshot(context)
            FirebaseFirestore.getInstance()
                .collection("providerProfiles")
                .document(globalId)
                .set(
                    mapOf(
                        "globalId" to globalId,
                        "participantRoles" to onboarding.participantRoles,
                        "contributionApproved" to onboarding.contributionApproved,
                        "onboardingCompleted" to onboarding.completed,
                        "consentGiven" to profile.consentGiven,
                        "hotspotSsid" to profile.hotspotSsid,
                        "hotspotPassword" to profile.hotspotPassword,
                        "recommendedSsid" to InternetProviderConventionManager.recommendedSsid(globalId),
                        "recommendedPassword" to InternetProviderConventionManager.STANDARD_PASSWORD,
                        "credentialsReady" to profile.credentialsReady,
                        "conventionAligned" to readiness.conventionAligned,
                        "hotspotActive" to readiness.hotspotActive,
                        "providerReady" to readiness.providerReady,
                        "providerActive" to readiness.providerActive,
                        "status" to readiness.status.name,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
        }
    }

    suspend fun syncPresenceAndPolicies(
        context: Context,
        globalId: String,
        nodes: List<MeshNode>,
        contactCount: Int,
        remoteModeEnabled: Boolean
    ): SyncResult {
        if (!FirebaseBootstrap.ensureInitialized(context)) {
            return SyncResult(false, "Firebase belum siap.", 0, emptySet())
        }

        return runCatching {
            val now = System.currentTimeMillis()
            val providerReadiness = InternetProviderReadinessManager.snapshot(context)
            val onboarding = OnboardingManager.snapshot(context)
            val presenceRef = FirebaseDatabase.getInstance().getReference("presence").child(globalId)
            presenceRef.child("globalId").setValue(globalId).await()
            presenceRef.child("online").setValue(remoteModeEnabled).await()
            presenceRef.child("contactCount").setValue(contactCount).await()
            presenceRef.child("localNodeCount").setValue(nodes.size).await()
            presenceRef.child("participantRoles").setValue(onboarding.participantRoles).await()
            presenceRef.child("contributionApproved").setValue(onboarding.contributionApproved).await()
            presenceRef.child("onboardingCompleted").setValue(onboarding.completed).await()
            presenceRef.child("providerReady").setValue(providerReadiness.providerReady).await()
            presenceRef.child("providerActive").setValue(providerReadiness.providerActive).await()
            presenceRef.child("hotspotActive").setValue(providerReadiness.hotspotActive).await()
            presenceRef.child("credentialsReady").setValue(providerReadiness.credentialsReady).await()
            presenceRef.child("sharingName").setValue(providerReadiness.shareName).await()
            presenceRef.child("updatedAt").setValue(now).await()
            presenceRef.child("online").onDisconnect().setValue(false)
            presenceRef.child("updatedAt").onDisconnect().setValue(ServerValue.TIMESTAMP)

            cacheFirestorePolicies(context)
            runCatching { syncProviderProfile(context, globalId) }
            refreshControlData(
                context,
                setOf(globalId) + GlobalContactDirectory.getAll(context).map { it.globalId }
            )

            val presenceSnapshot = FirebaseDatabase.getInstance().getReference("presence").get().await()
            val onlineIds = extractOnlineIds(presenceSnapshot)

            prefs(context)
                .edit()
                .putLong(KEY_LAST_SUCCESS, now)
                .putString(KEY_LAST_ERROR, "")
                .putInt(KEY_LAST_REMOTE_ONLINE_COUNT, onlineIds.size)
                .putStringSet(KEY_REMOTE_ONLINE_IDS, onlineIds)
                .apply()

            SyncResult(
                success = true,
                detail = "Firebase tersambung.",
                remoteOnlineCount = onlineIds.size,
                onlineContactIds = onlineIds
            )
        }.getOrElse { error ->
            prefs(context)
                .edit()
                .putString(KEY_LAST_ERROR, error.message ?: "Firebase tidak merespon.")
                .apply()

            SyncResult(
                success = false,
                detail = error.message ?: "Firebase tidak merespon.",
                remoteOnlineCount = prefs(context).getInt(KEY_LAST_REMOTE_ONLINE_COUNT, 0),
                onlineContactIds = remoteOnlineIds(context)
            )
        }
    }

    private suspend fun cacheFirestorePolicies(context: Context) {
        val bridgeDoc =
            FirebaseFirestore.getInstance().collection("bridgePolicies").document("default").get().await()
        if (bridgeDoc.exists()) {
            prefs(context)
                .edit()
                .putString(KEY_BRIDGE_POLICY_JSON, bridgeDoc.data.orEmpty().toJson())
                .apply()
        }

        val economyDoc =
            FirebaseFirestore.getInstance().collection("economyPolicies").document("default").get().await()
        if (economyDoc.exists()) {
            prefs(context)
                .edit()
                .putString(KEY_ECONOMY_POLICY_JSON, economyDoc.data.orEmpty().toJson())
                .apply()
        }
    }

    private fun extractOnlineIds(snapshot: DataSnapshot): Set<String> {
        return buildSet {
            snapshot.children.forEach { child ->
                if (child.child("online").getValue(Boolean::class.java) == true) {
                    child.key?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }
    }

    private fun parseJsonStringSet(source: org.json.JSONArray?): Set<String> {
        if (source == null) return emptySet()
        return buildSet {
            for (index in 0 until source.length()) {
                val value = source.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun Map<String, Any>.toJson(): String {
        return org.json.JSONObject(this).toString()
    }
}
