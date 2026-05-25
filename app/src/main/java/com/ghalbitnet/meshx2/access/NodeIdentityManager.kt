package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
import java.util.UUID

object NodeIdentityManager {

    data class NodeIdentity(
        val nodeId: String,
        val publicKey: String,
        val walletAddress: String,
        val appVersion: String
    )

    data class HelloAuth(
        val nodeId: String,
        val publicKey: String,
        val walletAddress: String,
        val appVersion: String,
        val timestamp: Long,
        val nonce: String,
        val signature: String,
        val gateway: Boolean,
        val relay: Boolean
    ) {
        fun signingPayload(): String = "$nodeId|$walletAddress|$timestamp|$nonce"
    }

    fun getOrCreateIdentity(context: Context): NodeIdentity {
        val keyStore = KeyStoreManager(context.applicationContext)
        val publicKey = keyStore.publicKeyBase64
        val nodeId = GlobalMeshIdentityManager.buildGlobalId(publicKey)
        val walletAddress = "wallet:$nodeId"
        return NodeIdentity(
            nodeId = nodeId,
            publicKey = publicKey,
            walletAddress = walletAddress,
            appVersion = appVersion(context)
        )
    }

    fun buildHelloAuth(
        context: Context,
        gateway: Boolean,
        relay: Boolean
    ): HelloAuth {
        val identity = getOrCreateIdentity(context)
        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val signature =
            KeyStoreManager(context.applicationContext)
                .signPayload("${identity.nodeId}|${identity.walletAddress}|$timestamp|$nonce")
        return HelloAuth(
            nodeId = identity.nodeId,
            publicKey = identity.publicKey,
            walletAddress = identity.walletAddress,
            appVersion = identity.appVersion,
            timestamp = timestamp,
            nonce = nonce,
            signature = signature,
            gateway = gateway,
            relay = relay
        )
    }

    private fun appVersion(context: Context): String {
        return runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "0.0.0"
        }.getOrDefault("0.0.0")
    }
}
