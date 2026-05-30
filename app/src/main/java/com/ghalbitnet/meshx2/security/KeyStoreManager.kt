package com.ghalbitnet.meshx2.security
import java.security.spec.ECGenParameterSpec
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.*

class KeyStoreManager(context: Context) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val alias = "ghalbit_mesh_key"
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "ghalbit_keystore_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val publicKeyBase64: String
        get() {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            if (entry != null) {
                return Base64.encodeToString(entry.certificate.publicKey.encoded, Base64.NO_WRAP)
            }
            // Generate baru
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
            )
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_AGREE_KEY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setUserAuthenticationRequired(false)
                .build()
            keyPairGenerator.initialize(spec)
            val keyPair = keyPairGenerator.generateKeyPair()
            return Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        }
    
    val privateKey: PrivateKey
        get() = (keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry).privateKey

    fun storePeerKey(peerId: String, publicKeyBase64: String) {
        prefs.edit().putString("peer_$peerId", publicKeyBase64).apply()
    }

    

    fun getPeerKey(peerId: String): String? = prefs.getString("peer_$peerId", null)

    fun isPeerKeyChanged(
        peerId: String,
        newPublicKeyBase64: String
    ): Boolean {
        val existing =
            getPeerKey(peerId)

        return !existing.isNullOrBlank() &&
            existing != newPublicKeyBase64
    }

    fun storePeerAddress(peerId: String, ip: String) {
    prefs.edit().putString("addr_$peerId", ip).apply()
}

fun getPeerAddress(peerId: String): String? {
    return prefs.getString("addr_$peerId", null)
}
}
