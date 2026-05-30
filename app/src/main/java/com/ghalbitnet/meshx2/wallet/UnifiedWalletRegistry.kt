package com.ghalbitnet.meshx2.wallet

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object UnifiedWalletRegistry {
    private const val TAG = "GHALBIT-WALLET"
    private const val PREFS = "ghalbit_wallet_registry"
    private const val KEY_OWNERS = "wallet_owners"
    private const val KEY_NODES = "wallet_owned_nodes"
    private const val KEY_BINDINGS = "wallet_node_bindings"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    suspend fun upsertOwner(context: Context, owner: WalletOwner) {
        lock.withLock {
            val owners = loadOwners(context).associateBy { it.walletId }.toMutableMap()
            owners[owner.walletId] = owner
            saveOwners(context, owners.values.toList())
            Log.d(TAG, "Registered wallet owner ${owner.walletId}")
        }
    }

    suspend fun bindNode(
        context: Context,
        ownedNode: OwnedNode,
        binding: NodeWalletBinding = NodeWalletBinding(
            nodeId = ownedNode.nodeId,
            ownerWalletId = ownedNode.ownerWalletId
        )
    ) {
        lock.withLock {
            val ownedNodes = loadOwnedNodes(context).associateBy { it.nodeId }.toMutableMap()
            val bindings = loadBindings(context).associateBy { it.nodeId }.toMutableMap()
            ownedNodes[ownedNode.nodeId] = ownedNode
            bindings[binding.nodeId] = binding
            saveOwnedNodes(context, ownedNodes.values.toList())
            saveBindings(context, bindings.values.toList())
            Log.d(TAG, "Bound node ${ownedNode.nodeId} to wallet ${ownedNode.ownerWalletId}")
        }
    }

    suspend fun getBinding(context: Context, nodeId: String): NodeWalletBinding? = lock.withLock {
        loadBindings(context).firstOrNull { it.nodeId == nodeId }
    }

    suspend fun getOwnedNodes(context: Context, ownerWalletId: String): List<OwnedNode> = lock.withLock {
        loadOwnedNodes(context).filter { it.ownerWalletId == ownerWalletId }
    }

    suspend fun getOwners(context: Context): List<WalletOwner> = lock.withLock {
        loadOwners(context)
    }

    suspend fun getPrimaryOwner(context: Context): WalletOwner? = lock.withLock {
        loadOwners(context).firstOrNull { it.primary }
    }

    private suspend fun loadOwners(context: Context): List<WalletOwner> = withContext(Dispatchers.IO) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_OWNERS, "[]").orEmpty()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    WalletOwner(
                        walletId = item.getString("walletId"),
                        displayName = item.optString("displayName", item.getString("walletId")),
                        primary = item.optBoolean("primary", false),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private suspend fun loadOwnedNodes(context: Context): List<OwnedNode> = withContext(Dispatchers.IO) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NODES, "[]").orEmpty()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    OwnedNode(
                        nodeId = item.getString("nodeId"),
                        ownerWalletId = item.getString("ownerWalletId"),
                        nodeLabel = item.optString("nodeLabel", item.getString("nodeId")),
                        active = item.optBoolean("active", true),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private suspend fun loadBindings(context: Context): List<NodeWalletBinding> = withContext(Dispatchers.IO) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BINDINGS, "[]").orEmpty()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    NodeWalletBinding(
                        nodeId = item.getString("nodeId"),
                        ownerWalletId = item.getString("ownerWalletId"),
                        rewardCollectionEnabled = item.optBoolean("rewardCollectionEnabled", true),
                        boundAt = item.optLong("boundAt", System.currentTimeMillis()),
                        metadataVersion = item.optInt("metadataVersion", 1)
                    )
                )
            }
        }
    }

    private fun saveOwners(context: Context, owners: List<WalletOwner>) {
        val array = JSONArray()
        owners.forEach { owner ->
            array.put(
                JSONObject()
                    .put("walletId", owner.walletId)
                    .put("displayName", owner.displayName)
                    .put("primary", owner.primary)
                    .put("createdAt", owner.createdAt)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_OWNERS, array.toString()).apply()
    }

    private fun saveOwnedNodes(context: Context, ownedNodes: List<OwnedNode>) {
        val array = JSONArray()
        ownedNodes.forEach { ownedNode ->
            array.put(
                JSONObject()
                    .put("nodeId", ownedNode.nodeId)
                    .put("ownerWalletId", ownedNode.ownerWalletId)
                    .put("nodeLabel", ownedNode.nodeLabel)
                    .put("active", ownedNode.active)
                    .put("createdAt", ownedNode.createdAt)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NODES, array.toString()).apply()
    }

    private fun saveBindings(context: Context, bindings: List<NodeWalletBinding>) {
        val array = JSONArray()
        bindings.forEach { binding ->
            array.put(
                JSONObject()
                    .put("nodeId", binding.nodeId)
                    .put("ownerWalletId", binding.ownerWalletId)
                    .put("rewardCollectionEnabled", binding.rewardCollectionEnabled)
                    .put("boundAt", binding.boundAt)
                    .put("metadataVersion", binding.metadataVersion)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_BINDINGS, array.toString()).apply()
    }
}
