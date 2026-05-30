package com.ghalbitnet.meshx2.security

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ghalbitnet.meshx2.core.network.GlobalMeshIdentityManager
import com.ghalbitnet.meshx2.util.LogThrottle
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

object NodeSigningIdentityManager {
    private const val PREFS = "ghalbit_signing_identity"
    private const val KEY_PUBLIC = "public"
    private const val KEY_PRIVATE = "private"
    private const val KEY_NODE_ID = "node_id"
    private const val ALGORITHM = "Ed25519"

    data class SigningIdentity(
        val nodeId: String,
        val publicKeyBase64: String,
        val publicKeyHash: String,
        val globalId: String
    )

    fun getOrCreate(context: Context): SigningIdentity {
        val prefs = prefs(context)
        val existingPublic = prefs.getString(KEY_PUBLIC, null)
        val existingPrivate = prefs.getString(KEY_PRIVATE, null)
        val nodeId = prefs.getString(KEY_NODE_ID, null) ?: "relay-${UUID.randomUUID().toString().take(8)}"
        if (!existingPublic.isNullOrBlank() && !existingPrivate.isNullOrBlank()) {
            return SigningIdentity(
                nodeId = nodeId,
                publicKeyBase64 = existingPublic,
                publicKeyHash = sha256(existingPublic),
                globalId = GlobalMeshIdentityManager.buildGlobalId(existingPublic)
            )
        }
        val generator = KeyPairGenerator.getInstance(ALGORITHM)
        val pair = generator.generateKeyPair()
        val publicBase64 = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
        val privateBase64 = Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP)
        prefs.edit()
            .putString(KEY_PUBLIC, publicBase64)
            .putString(KEY_PRIVATE, privateBase64)
            .putString(KEY_NODE_ID, nodeId)
            .apply()
        Log.d("GHALBIT-CRYPTO", "keypair generated nodeId=$nodeId")
        return SigningIdentity(
            nodeId = nodeId,
            publicKeyBase64 = publicBase64,
            publicKeyHash = sha256(publicBase64),
            globalId = GlobalMeshIdentityManager.buildGlobalId(publicBase64)
        )
    }

    fun sign(context: Context, canonicalPayload: String, messageId: String): String {
        val privateKey = decodePrivateKey(prefs(context).getString(KEY_PRIVATE, null).orEmpty())
        val signature = Signature.getInstance(ALGORITHM)
        signature.initSign(privateKey)
        signature.update(canonicalPayload.toByteArray())
        val signed = Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
        LogThrottle.d("GHALBIT-CRYPTO", "sign:$messageId", "sign messageId=$messageId", 10_000L, context)
        return signed
    }

    fun verify(publicKeyBase64: String, canonicalPayload: String, signatureBase64: String): Boolean {
        return runCatching {
            val verifier = Signature.getInstance(ALGORITHM)
            verifier.initVerify(decodePublicKey(publicKeyBase64))
            verifier.update(canonicalPayload.toByteArray())
            verifier.verify(Base64.decode(signatureBase64, Base64.NO_WRAP))
        }.getOrDefault(false)
    }

    private fun decodePrivateKey(value: String): PrivateKey {
        val keyFactory = KeyFactory.getInstance(ALGORITHM)
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(value, Base64.NO_WRAP)))
    }

    private fun decodePublicKey(value: String): PublicKey {
        val keyFactory = KeyFactory.getInstance(ALGORITHM)
        return keyFactory.generatePublic(X509EncodedKeySpec(Base64.decode(value, Base64.NO_WRAP)))
    }

    private fun sha256(value: String): String {
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString(separator = "") { "%02x".format(it) }
    }

    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREFS,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
}
