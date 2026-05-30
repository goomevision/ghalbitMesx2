package com.ghalbitnet.meshx2.security
import java.security.spec.ECGenParameterSpec
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ghalbitnet.meshx2.core.network.TransportPreference
import java.security.*
import java.util.concurrent.ConcurrentHashMap

class KeyStoreManager(context: Context) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val alias = "ghalbit_mesh_key"
    private val prefs: SharedPreferences
    private val prefsName = "ghalbit_keystore_prefs"

    init {
        prefs = createPrefsWithRecovery(context)
    }

    val publicKeyBase64: String
        get() {
            val entry = ensureSigningKeyEntry()
            return Base64.encodeToString(entry.certificate.publicKey.encoded, Base64.NO_WRAP)
        }
    
    val privateKey: PrivateKey
        get() = ensureSigningKeyEntry().privateKey

    fun signPayload(payload: String): String {
        return CryptoEngine.sign(privateKey, payload)
    }

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
        val current =
            getPeerAddress(peerId)

        if (!TransportPreference.shouldPreferAddress(current, ip)) {
            return
        }

        prefs.edit().putString("addr_$peerId", ip).apply()
    }

    fun getPeerAddress(peerId: String): String? {
        return prefs.getString("addr_$peerId", null)
    }

    private fun ensureSigningKeyEntry(): KeyStore.PrivateKeyEntry {
        val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
        if (entry != null && canSign(entry.privateKey)) {
            return entry
        }
        if (entry != null) {
            runCatching { keyStore.deleteEntry(alias) }
        }
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )
        val spec =
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_AGREE_KEY or
                    KeyProperties.PURPOSE_SIGN or
                    KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(
                    KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA512
                )
                .setUserAuthenticationRequired(false)
                .build()
        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
        return keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
    }

    private fun canSign(privateKey: PrivateKey): Boolean {
        return runCatching {
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKey)
            signature.update("ghalbit-sign-check".toByteArray())
            signature.sign().isNotEmpty()
        }.getOrDefault(false)
    }

    private fun createPrefsWithRecovery(context: Context): SharedPreferences {
        return try {
            createEncryptedPrefs(context)
        } catch (firstError: Exception) {
            Log.w(TAG, "encrypted prefs failed, resetting", firstError)
            context.deleteSharedPreferences(prefsName)
            try {
                createEncryptedPrefs(context)
            } catch (secondError: Exception) {
                Log.e(TAG, "encrypted prefs recovery failed, using ephemeral store", secondError)
                EphemeralSharedPreferences()
            }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey =
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        return EncryptedSharedPreferences.create(
            context,
            prefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private class EphemeralSharedPreferences : SharedPreferences {
        private val values = ConcurrentHashMap<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            values[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (values[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = (values[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (values[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (values[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (values[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = key != null && values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(values)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private class Editor(
            private val values: ConcurrentHashMap<String, Any?>
        ) : SharedPreferences.Editor {
            private val staged = mutableMapOf<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) staged[key] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) staged[key] = values
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) staged[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) staged[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) staged[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) staged[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) staged[key] = null
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearRequested = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearRequested) values.clear()
                staged.forEach { (key, value) ->
                    if (value == null) {
                        values.remove(key)
                    } else {
                        values[key] = value
                    }
                }
                staged.clear()
                clearRequested = false
            }
        }
    }

    companion object {
        private const val TAG = "GHALBIT-KEYSTORE"
    }
}
