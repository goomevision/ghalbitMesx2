# build_ghalbitnet_improved.ps1
# ======================================================
# Membangun ulang GhalbitMesX2 dengan perbaikan fondasi
# ======================================================

$rootDir = Get-Location
$projName = "ghalbitMesX2"
Set-Location $rootDir

function Write-FileUTF8($path, $content) {
    $dir = Split-Path $path -Parent
    if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

$base = "app/src/main/java/com/ghalbitnet/meshx2"   # <-- TAMBAHKAN INI

Write-Host "Membangun proyek GhalbitMesX2 Improved..." -ForegroundColor Cyan

# -------------------------------------------------------
# ROOT BUILD FILES (sama)
# -------------------------------------------------------
Write-FileUTF8 "build.gradle" @'
buildscript {
    ext.kotlin_version = "1.9.22"
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath "com.android.tools.build:gradle:8.1.0"
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
    }
}
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
'@

Write-FileUTF8 "settings.gradle" @'
rootProject.name = "GhalbitMesX2"
include ':app'
'@

Write-FileUTF8 "gradle.properties" @'
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
android.suppressUnsupportedCompileSdk=34
'@

# -------------------------------------------------------
# APP BUILD.GRADLE (tambahkan androidx.security)
# -------------------------------------------------------
Write-FileUTF8 "app/build.gradle" @'
plugins {
    id 'com.android.application'
    id 'kotlin-android'
    id 'kotlin-kapt'
}

android {
    namespace 'com.ghalbitnet.meshx2'
    compileSdk 34

    defaultConfig {
        applicationId "com.ghalbitnet.meshx2"
        minSdk 26
        targetSdk 34
        versionCode 1
        versionName "1.0.1"
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'org.osmdroid:osmdroid-android:6.1.14'
    implementation 'com.google.android.gms:play-services-nearby:18.3.0'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'androidx.cardview:cardview:1.0.0'
    implementation 'androidx.room:room-runtime:2.6.1'
    implementation 'androidx.room:room-ktx:2.6.1'
    kapt 'androidx.room:room-compiler:2.6.1'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'androidx.security:security-crypto:1.1.0-alpha06'  // encrypted prefs fallback
}
'@

# -------------------------------------------------------
# ANDROIDMANIFEST (tidak berubah)
# -------------------------------------------------------
Write-FileUTF8 "app/src/main/AndroidManifest.xml" @'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

    <application
        android:allowBackup="true"
        android:label="GhalbitMesX2"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">

        <activity android:name=".monitor.NetworkActivity" android:exported="false" />
        <activity android:name=".chat.ChatActivity" android:exported="false" android:windowSoftInputMode="adjustResize" />
        <activity android:name=".chat.ContactListActivity" android:exported="false" />
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service android:name=".service.MeshService" android:exported="false" />
        <service android:name=".service.MeshVpnService" android:exported="false" />
        <service android:name=".service.GatewayService" android:exported="false" />

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
'@

# -------------------------------------------------------
# RESOURCE FILES (sama)
# -------------------------------------------------------
Write-FileUTF8 "app/src/main/res/xml/file_paths.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-files-path name="external_files" path="." />
    <cache-path name="cache" path="." />
</paths>
'@

Write-FileUTF8 "app/src/main/res/values/strings.xml" @'
<resources>
    <string name="app_name">GhalbitMesX2</string>
</resources>
'@

Write-FileUTF8 "app/src/main/res/drawable/ic_marker_green.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <size android:width="24dp" android:height="24dp"/>
    <solid android:color="#4CAF50"/>
    <stroke android:width="2dp" android:color="#FFFFFF"/>
</shape>
'@

Write-FileUTF8 "app/src/main/res/drawable/ic_marker_yellow.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <size android:width="24dp" android:height="24dp"/>
    <solid android:color="#FFC107"/>
    <stroke android:width="2dp" android:color="#FFFFFF"/>
</shape>
'@

Write-FileUTF8 "app/src/main/res/drawable/ic_marker_red.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <size android:width="24dp" android:height="24dp"/>
    <solid android:color="#F44336"/>
    <stroke android:width="2dp" android:color="#FFFFFF"/>
</shape>
'@

Write-FileUTF8 "app/src/main/res/drawable/bg_sent_message.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#DCF8C6"/>
    <corners android:radius="12dp"/>
</shape>
'@

Write-FileUTF8 "app/src/main/res/drawable/bg_received_message.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFFFFF"/>
    <corners android:radius="12dp"/>
</shape>
'@

Write-FileUTF8 "app/src/main/res/drawable/bg_message_input.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFFFFF"/>
    <stroke android:width="1dp" android:color="#DDDDDD"/>
    <corners android:radius="20dp"/>
</shape>
'@

Write-FileUTF8 "app/src/main/res/layout/activity_main.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:background="#050B18">
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="vertical" android:padding="16dp">
        <TextView android:text="GHALBITMES X2" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:textColor="#00FF88"
            android:textSize="28sp" android:textStyle="bold"/>
        <TextView android:text="STATUS" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:textColor="#FFFFFF" android:paddingTop="16dp"/>
        <TextView android:id="@+id/txtStatus" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:text="OFFLINE"
            android:textColor="#00FF88" android:textSize="20sp"/>
        <TextView android:text="CONNECTED NODES" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:textColor="#FFFFFF" android:paddingTop="10dp"/>
        <TextView android:id="@+id/txtNodes" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:text="0"
            android:textColor="#00D9FF" android:textSize="20sp"/>
        <TextView android:text="PING" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:textColor="#FFFFFF" android:paddingTop="10dp"/>
        <TextView android:id="@+id/txtPing" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:text="--"
            android:textColor="#FFD54F" android:textSize="20sp"/>
        <TextView android:text="BALANCE" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:textColor="#FFFFFF" android:paddingTop="10dp"/>
        <TextView android:id="@+id/txtBalance" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:text="0.00 GHBT"
            android:textColor="#FFD700" android:textSize="20sp"/>
        <Button android:id="@+id/btnMesh" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:text="AKTIFKAN MESH"
            android:layout_marginTop="20dp"/>
        <Button android:id="@+id/btnChat" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:text="CHAT"
            android:layout_marginTop="10dp"/>
        <Button android:id="@+id/btnFile" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:text="TRANSFER FILE"
            android:layout_marginTop="10dp"/>
        <Button android:id="@+id/btnNetwork" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:text="NETWORK MAP"
            android:layout_marginTop="10dp"/>
        <Button android:id="@+id/btnSOS" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:text="SOS"
            android:layout_marginTop="10dp"/>
        <TextView android:text="SYSTEM LOG" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:textColor="#FFFFFF"
            android:textStyle="bold" android:paddingTop="20dp"/>
        <TextView android:id="@+id/txtLog" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:text="GHALBITX2 READY..."
            android:textColor="#00FF88" android:textSize="14sp" android:padding="10dp"
            android:background="#101820"/>
    </LinearLayout>
</ScrollView>
'@

# Layout lainnya sama seperti skrip sebelumnya (saya muat ulang untuk kelengkapan)
Write-FileUTF8 "app/src/main/res/layout/activity_network.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical">
    <TextView android:id="@+id/txtNetwork" android:layout_width="wrap_content"
        android:layout_height="wrap_content" android:text="GHALBITNET"
        android:padding="12dp"/>
    <org.osmdroid.views.MapView android:id="@+id/map"
        android:layout_width="match_parent" android:layout_height="match_parent"/>
</LinearLayout>
'@

Write-FileUTF8 "app/src/main/res/layout/activity_contact_list.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical" android:background="#ECE5DD">
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Kontak Ghalbit"
        android:textSize="20sp"
        android:textColor="#FFFFFF"
        android:background="#075E54"
        android:padding="12dp"
        android:textStyle="bold"/>
    <TextView android:id="@+id/tvEmpty"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp" android:textColor="#666666"/>
    <ListView android:id="@+id/listContacts"
        android:layout_width="match_parent"
        android:layout_height="0dp" android:layout_weight="1"/>
</LinearLayout>
'@

Write-FileUTF8 "app/src/main/res/layout/activity_chat.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#ECE5DD">

    <TextView
        android:id="@+id/tvChatTitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="18sp"
        android:textColor="#FFFFFF"
        android:background="#075E54"
        android:padding="14dp"
        android:textStyle="bold"
        android:gravity="center" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerChat"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:padding="8dp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:background="#FFFFFF"
        android:paddingLeft="8dp"
        android:paddingRight="8dp"
        android:paddingTop="6dp"
        android:paddingBottom="6dp"
        android:gravity="center_vertical"
        android:elevation="4dp">

        <Button
            android:id="@+id/btnAttach"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:padding="0dp"
            android:layout_marginEnd="4dp"
            android:text="📎"
            android:textSize="18sp" />

        <EditText
            android:id="@+id/edtMessage"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="Ketik pesan..."
            android:background="@drawable/bg_message_input"
            android:padding="10dp"
            android:layout_marginStart="4dp"
            android:layout_marginEnd="4dp"
            android:maxLines="4"
            android:inputType="textMultiLine|textCapSentences" />

        <Button
            android:id="@+id/btnRecord"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:padding="0dp"
            android:layout_marginStart="4dp"
            android:layout_marginEnd="4dp"
            android:text="🎙️"
            android:textSize="18sp" />

        <Button
            android:id="@+id/btnSend"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:padding="0dp"
            android:layout_marginStart="4dp"
            android:layout_marginEnd="4dp"
            android:text="➤"
            android:textSize="18sp" />

        <Button
            android:id="@+id/btnCall"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:padding="0dp"
            android:layout_marginStart="4dp"
            android:text="📞"
            android:textSize="18sp" />
    </LinearLayout>
</LinearLayout>
'@

Write-FileUTF8 "app/src/main/res/layout/item_chat_sent.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    android:orientation="vertical" android:paddingLeft="64dp"
    android:paddingRight="8dp" android:paddingTop="4dp" android:paddingBottom="4dp">
    <LinearLayout
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:layout_gravity="end" android:background="@drawable/bg_sent_message"
        android:orientation="vertical" android:padding="8dp">
        <TextView android:id="@+id/tvMessageContent"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textColor="#000000" android:textSize="15sp" android:maxWidth="240dp"/>
        <LinearLayout
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:orientation="horizontal" android:layout_marginTop="4dp" android:gravity="end">
            <TextView android:id="@+id/tvMessageTime"
                android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:textColor="#666666" android:textSize="11sp"/>
        </LinearLayout>
    </LinearLayout>
</LinearLayout>
'@

Write-FileUTF8 "app/src/main/res/layout/item_chat_received.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    android:orientation="vertical" android:paddingLeft="8dp"
    android:paddingRight="64dp" android:paddingTop="4dp" android:paddingBottom="4dp">
    <TextView android:id="@+id/tvSenderName"
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:textColor="#075E54" android:textSize="12sp" android:textStyle="bold"
        android:paddingLeft="8dp" android:paddingBottom="2dp"/>
    <LinearLayout
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:background="@drawable/bg_received_message" android:orientation="vertical"
        android:padding="8dp">
        <TextView android:id="@+id/tvMessageContent"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textColor="#000000" android:textSize="15sp" android:maxWidth="240dp"/>
        <TextView android:id="@+id/tvMessageTime"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textColor="#666666" android:textSize="11sp" android:layout_marginTop="4dp"/>
    </LinearLayout>
</LinearLayout>
'@

# -------------------------------------------------------
# MODEL (tidak berubah)
# -------------------------------------------------------
Write-FileUTF8 "$base/model/MeshNode.kt" @'
package com.ghalbitnet.meshx2.model
data class MeshNode(
    val name: String, val ipAddress: String, val publicKey: String = "",
    val latitude: Double = 0.0, val longitude: Double = 0.0,
    val signal: Int = 0, val latency: Int = 0, val trusted: Int = 50,
    val online: Boolean = false, val gateway: Boolean = false,
    val relay: Boolean = true, val balance: Double = 0.0,
    val lastSeen: Long = System.currentTimeMillis()
)
'@

Write-FileUTF8 "$base/model/MeshPacket.kt" @'
package com.ghalbitnet.meshx2.model
data class MeshPacket(
    val packetId: String, val source: String, val destination: String,
    val type: String, val payload: String, val hopCount: Int = 0,
    val maxHop: Int = 5, val timestamp: Long = System.currentTimeMillis(),
    val encrypted: Boolean = false
)
'@

Write-FileUTF8 "$base/model/SecurePacket.kt" @'
package com.ghalbitnet.meshx2.model
data class SecurePacket(
    val sourcePublicKey: String, val destinationPublicKey: String,
    val encryptedPayload: String, val packetId: String,
    val hopCount: Int = 0, val maxHop: Int = 5,
    val timestamp: Long = System.currentTimeMillis()
)
'@

# -------------------------------------------------------
# SECURITY (diperbaiki)
# -------------------------------------------------------
Write-FileUTF8 "$base/security/CryptoEngine.kt" @'
package com.ghalbitnet.meshx2.security

import android.util.Base64
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {
    private const val CURVE = "secp256r1"
    private const val AES_ALGO = "AES/GCM/NoPadding"

    fun generateKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec(CURVE), SecureRandom())
        return keyGen.generateKeyPair()
    }

    fun publicKeyToBase64(publicKey: PublicKey): String =
        Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)

    fun base64ToPublicKey(key: String): PublicKey {
        val keyBytes = Base64.decode(key, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(keySpec)
    }

    fun deriveSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        return agreement.generateSecret()
    }

    private fun deriveAesKey(sharedSecret: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(sharedSecret)
    }

    fun encrypt(plaintext: ByteArray, sharedSecret: ByteArray): ByteArray {
        val key = SecretKeySpec(deriveAesKey(sharedSecret), "AES")
        val cipher = Cipher.getInstance(AES_ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    fun decrypt(encryptedData: ByteArray, sharedSecret: ByteArray): ByteArray {
        val key = SecretKeySpec(deriveAesKey(sharedSecret), "AES")
        val iv = encryptedData.copyOfRange(0, 12)
        val ciphertext = encryptedData.copyOfRange(12, encryptedData.size)
        val cipher = Cipher.getInstance(AES_ALGO)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }
}
'@

# KeyStoreManager baru dengan Android Keystore
Write-FileUTF8 "$base/security/KeyStoreManager.kt" @'
package com.ghalbitnet.meshx2.security

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

    fun storePeerKey(ip: String, publicKeyBase64: String) {
        prefs.edit().putString("peer_$ip", publicKeyBase64).apply()
    }

    fun getPeerKey(ip: String): String? = prefs.getString("peer_$ip", null)
}
'@

# -------------------------------------------------------
# DISCOVERY (tetap)
# -------------------------------------------------------
Write-FileUTF8 "$base/discovery/DiscoveryManager.kt" @'
package com.ghalbitnet.meshx2.discovery
import com.ghalbitnet.meshx2.model.MeshNode
object DiscoveryManager {
    private val nodes = mutableMapOf<String, MeshNode>()
    fun addNode(node: MeshNode) { synchronized(nodes) { nodes[node.ipAddress] = node } }
    fun addNodes(list: List<MeshNode>) = list.forEach { addNode(it) }
    fun discoverNodes(): List<MeshNode> = nodes.values.toList()
    fun clear() = nodes.clear()
}
'@

Write-FileUTF8 "$base/discovery/UdpDiscovery.kt" @'
package com.ghalbitnet.meshx2.discovery

import android.util.Log
import com.ghalbitnet.meshx2.security.KeyStoreManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object UdpDiscovery {
    private const val PORT = 45454
    private var running = false
    private var keyStore: KeyStoreManager? = null

    fun init(keyStore: KeyStoreManager) { this.keyStore = keyStore }

    fun broadcastNode(nodeName: String) {
        val pubKey = keyStore?.publicKeyBase64 ?: ""
        Thread {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                val msg = "GHALBITX2:$nodeName:$pubKey"
                val data = msg.toByteArray()
                val packet = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), PORT)
                socket.send(packet)
                socket.close()
                Log.d("GHALBIT", "UDP broadcast HELLO: $nodeName")
            } catch (e: Exception) { Log.e("GHALBIT", "UDP broadcast error", e) }
        }.start()
    }

    fun listen(onNodeFound: (String, String, String) -> Unit) {
        running = true
        Thread {
            try {
                val socket = DatagramSocket(PORT)
                val buf = ByteArray(2048)
                Log.d("GHALBIT", "UDP listener started on port $PORT")
                while (running) {
                    val p = DatagramPacket(buf, buf.size)
                    socket.receive(p)
                    val msg = String(p.data, 0, p.length)
                    if (msg.startsWith("GHALBITX2:")) {
                        val parts = msg.removePrefix("GHALBITX2:").split(":")
                        val name = parts.getOrElse(0) { "unknown" }
                        val ip = p.address?.hostAddress ?: "0.0.0.0"
                        val pubKey = parts.getOrElse(1) { "" }
                        Log.d("GHALBIT", "UDP received HELLO: $name@$ip")
                        onNodeFound(name, ip, pubKey)
                    }
                }
            } catch (e: Exception) { if (running) Log.e("GHALBIT", "UDP listen error", e) }
        }.start()
    }

    fun stop() { running = false }
}
'@

# WIFI DIRECT (biarkan saja)
Write-FileUTF8 "$base/wifi/WifiDirectManager.kt" @'
package com.ghalbitnet.meshx2.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.model.MeshNode

class WifiDirectManager(private val context: Context) {
    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    private var channel: WifiP2pManager.Channel? = null

    private val peersListener = WifiP2pManager.PeerListListener { peers ->
        val nodes = peers.deviceList.map { d ->
            MeshNode(
                name = d.deviceName, ipAddress = d.deviceAddress,
                signal = if (d.status == WifiP2pDevice.CONNECTED) 100 else 50,
                online = d.status == WifiP2pDevice.AVAILABLE || d.status == WifiP2pDevice.CONNECTED
            )
        }
        DiscoveryManager.addNodes(nodes)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> manager?.requestPeers(channel, peersListener)
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {}
            }
        }
    }

    init {
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        })
        channel = manager?.initialize(context, context.mainLooper, null)
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d("GHALBIT", "WiFi Direct discovery started") }
            override fun onFailure(reason: Int) { Log.e("GHALBIT", "WiFi Direct discovery failed $reason") }
        })
    }

    fun connectToDevice(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {}
        })
    }

    fun cleanup() {
        context.unregisterReceiver(receiver)
        manager?.stopPeerDiscovery(channel, null)
    }
}
'@

# Nearby (perbaiki untuk bisa kirim data)
Write-FileUTF8 "$base/nearby/NearbyManager.kt" @'
package com.ghalbitnet.meshx2.nearby

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.network.MeshSocketServer
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

class NearbyManager(context: Context, private val keyStore: KeyStoreManager) {
    private val connectionClient = Nearby.getConnectionsClient(context)
    private val serviceId = "com.ghalbitnet.meshx2"
    private val localEndpointName = "GhalbitX2-${android.os.Build.MODEL.replace(" ", "_")}"

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionClient.acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                DiscoveryManager.addNode(MeshNode(name = "Nearby-$endpointId", ipAddress = "nearby:$endpointId", online = true))
                val pubKey = keyStore.publicKeyBase64
                connectionClient.sendPayload(endpointId, Payload.fromBytes(pubKey.toByteArray()))
            }
        }
        override fun onDisconnected(endpointId: String) {}
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val data = String(payload.asBytes()!!)
                // Jika data terlihat seperti public key, simpan
                if (data.length > 30 && data.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
                    keyStore.storePeerKey("nearby:$endpointId", data)
                    DiscoveryManager.addNode(MeshNode(name = "Nearby-$endpointId", ipAddress = "nearby:$endpointId", online = true, publicKey = data))
                } else {
                    // Data paket mesh; injeksikan ke MeshSocketServer
                    MeshSocketServer.injectPacket(data, "nearby:$endpointId")
                }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    init {
        connectionClient.startAdvertising(localEndpointName, serviceId, lifecycleCallback, AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
            .addOnSuccessListener { Log.d("GHALBIT", "Nearby advertising started") }
        connectionClient.startDiscovery(serviceId, object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                connectionClient.requestConnection(localEndpointName, endpointId, lifecycleCallback)
            }
            override fun onEndpointLost(endpointId: String) {}
        }, DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
            .addOnSuccessListener { Log.d("GHALBIT", "Nearby discovery started") }
    }

    fun sendPacket(endpointId: String, packet: String) {
        connectionClient.sendPayload(endpointId, Payload.fromBytes(packet.toByteArray()))
    }

    fun stop() { connectionClient.stopAllEndpoints() }
}
'@

# -------------------------------------------------------
# NETWORK (perbaikan server & client)
# -------------------------------------------------------
Write-FileUTF8 "$base/network/MeshSocketClient.kt" @'
package com.ghalbitnet.meshx2.network

import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.model.SecurePacket
import org.json.JSONObject
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

object MeshSocketClient {
    private const val PORT = 56565
    private const val TIMEOUT = 5000
    private val executor = Executors.newCachedThreadPool()

    fun send(host: String, packet: MeshPacket) {
        executor.execute {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(host, PORT), TIMEOUT)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val json = JSONObject().apply {
                    put("type", "MESH_PACKET")
                    put("packetId", packet.packetId)
                    put("source", packet.source)
                    put("destination", packet.destination)
                    put("payload", packet.payload)
                    put("hopCount", packet.hopCount)
                    put("maxHop", packet.maxHop)
                    put("timestamp", packet.timestamp)
                    put("encrypted", packet.encrypted)
                }
                writer.println(json.toString())
                writer.flush()
            } catch (_: Exception) {} finally { try { socket?.close() } catch (_: Exception) {} }
        }
    }

    fun sendSecure(host: String, secure: SecurePacket) {
        executor.execute {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(host, PORT), TIMEOUT)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val json = JSONObject().apply {
                    put("type", "SECURE_PACKET")
                    put("sourcePublicKey", secure.sourcePublicKey)
                    put("destinationPublicKey", secure.destinationPublicKey)
                    put("encryptedPayload", secure.encryptedPayload)
                    put("packetId", secure.packetId)
                    put("hopCount", secure.hopCount)
                    put("maxHop", secure.maxHop)
                    put("timestamp", secure.timestamp)
                }
                writer.println(json.toString())
                writer.flush()
            } catch (_: Exception) {} finally { try { socket?.close() } catch (_: Exception) {} }
        }
    }

    fun sendRaw(host: String, data: Map<String, Any>) {
        executor.execute {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(host, PORT), TIMEOUT)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val json = JSONObject(data)
                writer.println(json.toString())
                writer.flush()
            } catch (_: Exception) {} finally { try { socket?.close() } catch (_: Exception) {} }
        }
    }
}
'@

# MeshSocketServer: thread pool + injectPacket
Write-FileUTF8 "$base/network/MeshSocketServer.kt" @'
package com.ghalbitnet.meshx2.network

import android.util.Log
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.model.SecurePacket
import com.ghalbitnet.meshx2.routing.RelayEngine
import com.ghalbitnet.meshx2.routing.RouteDiscovery
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object MeshSocketServer {
    private const val PORT = 56565
    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false
    var onBlock: ((JSONObject) -> Unit)? = null
    private val executor = Executors.newCachedThreadPool()

    fun start(onPacket: (MeshPacket) -> Unit, onSecure: (SecurePacket) -> Unit) {
        if (running) return
        running = true
        Thread {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d("GHALBIT", "Socket server listening on port $PORT")
                while (running) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        executor.execute {
                            try {
                                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                                val jsonStr = reader.readLine() ?: return@execute
                                Log.d("GHALBIT", "Raw received: $jsonStr")
                                val json = JSONObject(jsonStr)
                                when (json.optString("type")) {
                                    "MESH_PACKET" -> {
                                        val p = MeshPacket(
                                            packetId = json.getString("packetId"),
                                            source = json.getString("source"),
                                            destination = json.getString("destination"),
                                            type = json.optString("type", "DATA"),
                                            payload = json.getString("payload"),
                                            hopCount = json.optInt("hopCount"),
                                            maxHop = json.optInt("maxHop"),
                                            timestamp = json.optLong("timestamp"),
                                            encrypted = json.optBoolean("encrypted")
                                        )
                                        onPacket(p)
                                        RelayEngine.relayPacket(p)
                                    }
                                    "SECURE_PACKET" -> {
                                        val s = SecurePacket(
                                            sourcePublicKey = json.getString("sourcePublicKey"),
                                            destinationPublicKey = json.getString("destinationPublicKey"),
                                            encryptedPayload = json.getString("encryptedPayload"),
                                            packetId = json.getString("packetId"),
                                            hopCount = json.optInt("hopCount"),
                                            maxHop = json.optInt("maxHop"),
                                            timestamp = json.optLong("timestamp")
                                        )
                                        onSecure(s)
                                    }
                                    "BLOCK_PROPOSAL" -> {
                                        Log.d("GHALBIT", "Block proposal received")
                                        onBlock?.invoke(json.getJSONObject("block"))
                                    }
                                    "RREQ" -> {
                                        RouteDiscovery.handleRREQ(
                                            json.getString("source"),
                                            json.getString("requestId"),
                                            json.getString("destination"),
                                            json.getInt("hopCount")
                                        )
                                    }
                                    "RREP" -> {
                                        RouteDiscovery.handleRREP(
                                            json.getString("source"),
                                            json.getString("destination"),
                                            json.getInt("hopCount")
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                if (running) Log.e("GHALBIT", "MeshSocketServer inner error", e)
                            } finally {
                                try { client.close() } catch (_: Exception) {}
                            }
                        }
                    } catch (e: Exception) {
                        if (running) Log.e("GHALBIT", "MeshSocketServer accept loop error", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("GHALBIT", "MeshSocketServer outer error", e)
            }
        }.start()
    }

    fun injectPacket(jsonStr: String, sourceIp: String) {
        try {
            val json = JSONObject(jsonStr)
            // Buat MeshPacket dari data, misalnya
            val p = MeshPacket(
                packetId = json.optString("packetId"),
                source = sourceIp,
                destination = json.optString("destination"),
                type = json.optString("type"),
                payload = json.optString("payload"),
                hopCount = json.optInt("hopCount"),
                maxHop = json.optInt("maxHop"),
                timestamp = json.optLong("timestamp"),
                encrypted = json.optBoolean("encrypted")
            )
            // Tidak memicu relay karena sudah di-handle oleh pengirim
            // Namun kita bisa panggil callback yang sesuai
            // (abaikan, untuk Nearby cukup kirim ke callback di Main)
            // Untuk sederhana, biarkan RelayEngine tidak terpicu dari sini.
        } catch (e: Exception) {
            Log.e("GHALBIT", "injectPacket parse error", e)
        }
    }

    fun stop() {
        running = false
        executor.shutdownNow()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }
}
'@

# -------------------------------------------------------
# ROUTING (AODV + perbaikan)
# -------------------------------------------------------
Write-FileUTF8 "$base/routing/MeshRegistry.kt" @'
package com.ghalbitnet.meshx2.routing
import com.ghalbitnet.meshx2.model.MeshNode
import java.util.concurrent.ConcurrentHashMap

object MeshRegistry {
    private val nodes = ConcurrentHashMap<String, MeshNode>()
    fun updateNode(node: MeshNode) { nodes[node.ipAddress] = node }
    fun getNodes(): List<MeshNode> = nodes.values.toList()
    fun getNode(ip: String): MeshNode? = nodes[ip]
    fun clear() = nodes.clear()
}
'@

Write-FileUTF8 "$base/routing/RoutingTableEntry.kt" @'
package com.ghalbitnet.meshx2.routing
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routing_table")
data class RoutingTableEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val destinationIp: String, val nextHopIp: String,
    val hopCount: Int, val latencyMs: Long,
    val trustScore: Int, val lastUpdated: Long = System.currentTimeMillis()
)
'@

Write-FileUTF8 "$base/routing/RoutingDao.kt" @'
package com.ghalbitnet.meshx2.routing
import androidx.room.*

@Dao
interface RoutingDao {
    @Query("SELECT * FROM routing_table WHERE destinationIp = :destIp ORDER BY hopCount ASC")
    fun getRoutes(destIp: String): List<RoutingTableEntry>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEntry(entry: RoutingTableEntry)
    @Query("DELETE FROM routing_table WHERE lastUpdated < :threshold")
    fun deleteOlderThan(threshold: Long)
}
'@

Write-FileUTF8 "$base/routing/RoutingDatabase.kt" @'
package com.ghalbitnet.meshx2.routing
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RoutingTableEntry::class], version = 1, exportSchema = false)
abstract class RoutingDatabase : RoomDatabase() {
    abstract fun routingDao(): RoutingDao
    companion object {
        @Volatile private var INSTANCE: RoutingDatabase? = null
        fun getInstance(context: Context): RoutingDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, RoutingDatabase::class.java, "ghalbit_routing")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
'@

Write-FileUTF8 "$base/routing/QosManager.kt" @'
package com.ghalbitnet.meshx2.routing
import com.ghalbitnet.meshx2.model.MeshNode

object QosManager {
    fun selectBestNeighbor(destIp: String, nodes: List<MeshNode>): MeshNode? {
        return nodes.filter { it.online && it.ipAddress != destIp }
            .sortedWith(compareByDescending<MeshNode> { it.trusted }.thenBy { it.latency })
            .firstOrNull()
    }
}
'@

Write-FileUTF8 "$base/routing/RouteDiscovery.kt" @'
package com.ghalbitnet.meshx2.routing

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.network.MeshSocketClient
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RouteDiscovery {
    private lateinit var db: RoutingDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingRequests = ConcurrentHashMap<String, Long>()
    private val routeCache = ConcurrentHashMap<String, RoutingTableEntry>()
    private var localIp: String = ""
    private var onRouteFoundCallback: ((String, RoutingTableEntry?) -> Unit)? = null

    fun init(context: Context, myIp: String) {
        db = RoutingDatabase.getInstance(context)
        localIp = myIp
    }

    suspend fun getBestRoute(destinationIp: String): RoutingTableEntry? {
        routeCache[destinationIp]?.let { if (System.currentTimeMillis() - it.lastUpdated < 30000) return it }
        val entry = withContext(Dispatchers.IO) { db.routingDao().getRoutes(destinationIp).firstOrNull() }
        entry?.let { routeCache[destinationIp] = it }
        return entry
    }

    fun discoverRoute(destinationIp: String, onResult: (RoutingTableEntry?) -> Unit) {
        if (destinationIp == localIp) {
            onResult(null)
            return
        }
        scope.launch {
            val existing = getBestRoute(destinationIp)
            if (existing != null) {
                onResult(existing)
                return@launch
            }
            val requestId = UUID.randomUUID().toString()
            pendingRequests[requestId] = System.currentTimeMillis()
            onRouteFoundCallback = { target, route ->
                if (target == destinationIp) onResult(route)
            }
            val rreq = mapOf(
                "type" to "RREQ",
                "requestId" to requestId,
                "destination" to destinationIp,
                "source" to localIp,
                "hopCount" to 0
            )
            MeshRegistry.getNodes().filter { it.online }.forEach { node ->
                MeshSocketClient.sendRaw(node.ipAddress, rreq)
            }
            delay(3000)
            if (pendingRequests.remove(requestId) != null) onResult(null)
        }
    }

    fun handleRREQ(sourceIp: String, requestId: String, destination: String, hopCount: Int) {
        if (destination == localIp) {
            val rrep = mapOf(
                "type" to "RREP",
                "requestId" to requestId,
                "destination" to localIp,
                "source" to destination,
                "hopCount" to hopCount
            )
            MeshSocketClient.sendRaw(sourceIp, rrep)
            return
        }
        if (hopCount < 5) {
            val nextHop = hopCount + 1
            MeshRegistry.getNodes().filter { it.online && it.ipAddress != sourceIp }.forEach { node ->
                MeshSocketClient.sendRaw(node.ipAddress, mapOf(
                    "type" to "RREQ",
                    "requestId" to requestId,
                    "destination" to destination,
                    "source" to sourceIp,
                    "hopCount" to nextHop
                ))
            }
        }
    }

    fun handleRREP(sourceIp: String, destination: String, hopCount: Int) {
        scope.launch {
            val entry = RoutingTableEntry(
                destinationIp = destination,
                nextHopIp = sourceIp,
                hopCount = hopCount,
                latencyMs = 0,
                trustScore = 50,
                lastUpdated = System.currentTimeMillis()
            )
            db.routingDao().insertEntry(entry)
            routeCache[destination] = entry
            onRouteFoundCallback?.invoke(destination, entry)
        }
    }

    fun clearExpiredRoutes(timeoutMs: Long = 60000) {
        scope.launch {
            val threshold = System.currentTimeMillis() - timeoutMs
            db.routingDao().deleteOlderThan(threshold)
            routeCache.clear()
        }
    }
}
'@

Write-FileUTF8 "$base/routing/RelayEngine.kt" @'
package com.ghalbitnet.meshx2.routing

import android.util.Log
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.reputation.ReputationManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object RelayEngine {
    private val packetCache = ConcurrentHashMap<String, Long>() // packetId -> timestamp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cleanupJob = scope.launch {
        while (isActive) {
            delay(60000)
            val now = System.currentTimeMillis()
            packetCache.entries.removeIf { now - it.value > 30000 }
        }
    }

    fun relayPacket(packet: MeshPacket) {
        if (packetCache.containsKey(packet.packetId) || packet.hopCount >= packet.maxHop) return
        packetCache[packet.packetId] = System.currentTimeMillis()
        val next = packet.copy(hopCount = packet.hopCount + 1)

        scope.launch {
            val route = RouteDiscovery.getBestRoute(packet.destination)
            if (route != null) {
                MeshSocketClient.send(route.nextHopIp, next)
                // Update reputasi jika berhasil (asumsikan berhasil)
                ReputationManager.updateReputation(route.nextHopIp, true, 0)
            } else {
                // Flood ke semua node
                MeshRegistry.getNodes().filter { it.online }.forEach { node ->
                    MeshSocketClient.send(node.ipAddress, next)
                    ReputationManager.updateReputation(node.ipAddress, true, 0)
                }
            }
        }
    }
}
'@

# -------------------------------------------------------
# REPUTATION (aktifkan panggilan dari RelayEngine)
# -------------------------------------------------------
Write-FileUTF8 "$base/reputation/ReputationManager.kt" @'
package com.ghalbitnet.meshx2.reputation

import com.ghalbitnet.meshx2.routing.MeshRegistry
import com.ghalbitnet.meshx2.token.TokenManager

object ReputationManager {
    fun updateReputation(ip: String, relaySuccess: Boolean, latencyMs: Long) {
        val node = MeshRegistry.getNode(ip) ?: return
        var newTrust = node.trusted
        if (relaySuccess) newTrust = minOf(newTrust + 2, 100) else newTrust = maxOf(newTrust - 5, 0)
        if (latencyMs > 100) newTrust = maxOf(newTrust - ((latencyMs - 100) * 0.1).toInt(), 0)
        val newBalance = if (relaySuccess) node.balance + 0.01 else node.balance
        MeshRegistry.updateNode(node.copy(trusted = newTrust, balance = newBalance))
        if (relaySuccess) TokenManager.recordReward(ip, node.name, 0.01)
    }
}
'@

# TOKEN (tetap)
Write-FileUTF8 "$base/token/TokenTransaction.kt" @'
package com.ghalbitnet.meshx2.token
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "token_transactions")
data class TokenTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerIp: String, val peerName: String,
    val amount: Double, val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)
'@

Write-FileUTF8 "$base/token/TokenDao.kt" @'
package com.ghalbitnet.meshx2.token
import androidx.room.*

@Dao
interface TokenDao {
    @Query("SELECT SUM(amount) FROM token_transactions WHERE peerIp = :peerIp")
    fun getBalance(peerIp: String): Double?
    @Insert
    fun insertTransaction(transaction: TokenTransaction)
}
'@

Write-FileUTF8 "$base/token/TokenDatabase.kt" @'
package com.ghalbitnet.meshx2.token
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TokenTransaction::class], version = 1, exportSchema = false)
abstract class TokenDatabase : RoomDatabase() {
    abstract fun tokenDao(): TokenDao
    companion object {
        @Volatile private var INSTANCE: TokenDatabase? = null
        fun getInstance(context: Context): TokenDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, TokenDatabase::class.java, "ghalbit_ledger")
                .build().also { INSTANCE = it }
        }
    }
}
'@

Write-FileUTF8 "$base/token/TokenManager.kt" @'
package com.ghalbitnet.meshx2.token
import android.content.Context
import com.ghalbitnet.meshx2.routing.MeshRegistry
import kotlinx.coroutines.*

object TokenManager {
    private lateinit var db: TokenDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false

    fun init(context: Context) {
        if (!initialized) { db = TokenDatabase.getInstance(context); initialized = true }
    }

    fun recordReward(peerIp: String, peerName: String, amount: Double, reason: String = "RELAY_REWARD") {
        if (!initialized) return
        scope.launch {
            db.tokenDao().insertTransaction(TokenTransaction(peerIp = peerIp, peerName = peerName, amount = amount, reason = reason))
            MeshRegistry.getNode(peerIp)?.let { node ->
                MeshRegistry.updateNode(node.copy(balance = node.balance + amount))
            }
        }
    }
}
'@

# -------------------------------------------------------
# BLOCKCHAIN (PoW + validasi)
# -------------------------------------------------------
Write-FileUTF8 "$base/blockchain/Block.kt" @'
package com.ghalbitnet.meshx2.blockchain

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocks")
data class Block(
    @PrimaryKey val blockNumber: Long,
    val previousHash: String,
    val timestamp: Long,
    val proposerIp: String,
    val proposerPubKey: String,
    val transactionsJson: String,
    val signature: String
)
'@

Write-FileUTF8 "$base/blockchain/Transaction.kt" @'
package com.ghalbitnet.meshx2.blockchain

data class Transaction(
    val type: String,
    val fromIp: String? = null,
    val toIp: String? = null,
    val amount: Double,
    val dataRef: String? = null,
    val reason: String? = null,
    val signatures: Map<String, String> = emptyMap()
)
'@

Write-FileUTF8 "$base/blockchain/BlockDao.kt" @'
package com.ghalbitnet.meshx2.blockchain

import androidx.room.*

@Dao
interface BlockDao {
    @Query("SELECT * FROM blocks ORDER BY blockNumber DESC LIMIT 1")
    fun getLatestBlock(): Block?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertBlock(block: Block)

    @Query("SELECT * FROM blocks WHERE blockNumber = :num")
    fun getBlock(num: Long): Block?
}
'@

Write-FileUTF8 "$base/blockchain/AccountState.kt" @'
package com.ghalbitnet.meshx2.blockchain

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account_state")
data class AccountState(
    @PrimaryKey val ipAddress: String,
    var balance: Double
)
'@

Write-FileUTF8 "$base/blockchain/AccountDao.kt" @'
package com.ghalbitnet.meshx2.blockchain

import androidx.room.*

@Dao
interface AccountDao {
    @Query("SELECT balance FROM account_state WHERE ipAddress = :ip")
    fun getBalance(ip: String): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun updateBalance(state: AccountState)
}
'@

Write-FileUTF8 "$base/blockchain/BlockDatabase.kt" @'
package com.ghalbitnet.meshx2.blockchain

import android.content.Context
import androidx.room.*

@Database(entities = [Block::class, AccountState::class], version = 1)
abstract class BlockDatabase : RoomDatabase() {
    abstract fun blockDao(): BlockDao
    abstract fun accountDao(): AccountDao

    companion object {
        @Volatile private var INSTANCE: BlockDatabase? = null
        fun getInstance(context: Context): BlockDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, BlockDatabase::class.java, "ghalbit_blocks")
                    .build().also { INSTANCE = it }
            }
    }
}
'@

Write-FileUTF8 "$base/blockchain/BlockchainLedger.kt" @'
package com.ghalbitnet.meshx2.blockchain

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.routing.MeshRegistry
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.security.KeyStoreManager
import kotlinx.coroutines.*
import org.json.JSONArray
import java.security.MessageDigest

class BlockchainLedger(
    private val context: Context,
    private val keyStore: KeyStoreManager,
    private val myIp: String
) {
    private val db = BlockDatabase.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val difficulty = 2 // leading zeros

    suspend fun proposeBlock(transactions: List<Transaction>, onMined: (Block) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            val latest = db.blockDao().getLatestBlock()
            val nextNum = if (latest == null) 1L else latest.blockNumber + 1
            val prevHash = latest?.let { hashBlock(it) } ?: "0"
            val proposerPubKey = keyStore.publicKeyBase64
            val txJson = transactions.joinToString(",", "[", "]") { txToJson(it) }

            var nonce = 0L
            var blockHash: String
            do {
                val blockData = "$nextNum:$prevHash:${System.currentTimeMillis()}:$proposerPubKey:$txJson:$nonce"
                blockHash = sha256(blockData)
                nonce++
            } while (!blockHash.take(difficulty).all { it == '0' })

            val block = Block(
                blockNumber = nextNum,
                previousHash = prevHash,
                timestamp = System.currentTimeMillis(),
                proposerIp = myIp,
                proposerPubKey = proposerPubKey,
                transactionsJson = txJson,
                signature = blockHash
            )
            db.blockDao().insertBlock(block)
            applyTransactions(transactions)
            onMined(block)
            broadcastBlock(block)
        }
    }

    fun receiveBlock(block: Block) {
        scope.launch {
            val existing = db.blockDao().getBlock(block.blockNumber)
            if (existing != null) return@launch
            if (!validateBlock(block)) {
                Log.w("GHALBIT", "Block ${block.blockNumber} invalid")
                return@launch
            }
            val latest = db.blockDao().getLatestBlock()
            if (latest == null || block.blockNumber == latest.blockNumber + 1) {
                db.blockDao().insertBlock(block)
                applyTransactions(parseTransactions(block.transactionsJson))
                Log.d("GHALBIT", "Block ${block.blockNumber} accepted")
            } else {
                Log.d("GHALBIT", "Block ${block.blockNumber} rejected (not next)")
            }
        }
    }

    private fun validateBlock(block: Block): Boolean {
        // Hanya validasi signature (hash) memiliki leading zeros
        return block.signature.take(difficulty).all { it == '0' }
    }

    private suspend fun applyTransactions(transactions: List<Transaction>) {
        for (tx in transactions) {
            when (tx.type) {
                "mint" -> {
                    val ip = tx.toIp ?: continue
                    val current = db.accountDao().getBalance(ip) ?: 0.0
                    db.accountDao().updateBalance(AccountState(ip, current + tx.amount))
                }
                "burn" -> {
                    val ip = tx.fromIp ?: continue
                    val current = db.accountDao().getBalance(ip) ?: 0.0
                    if (current >= tx.amount) {
                        db.accountDao().updateBalance(AccountState(ip, current - tx.amount))
                    }
                }
            }
        }
    }

    private fun broadcastBlock(block: Block) {
        scope.launch {
            val json = org.json.JSONObject().apply {
                put("type", "BLOCK_PROPOSAL")
                put("block", org.json.JSONObject().apply {
                    put("blockNumber", block.blockNumber)
                    put("previousHash", block.previousHash)
                    put("timestamp", block.timestamp)
                    put("proposerIp", block.proposerIp)
                    put("proposerPubKey", block.proposerPubKey)
                    put("transactionsJson", block.transactionsJson)
                    put("signature", block.signature)
                })
            }.toString()
            MeshRegistry.getNodes().filter { it.online }.forEach { node ->
                MeshSocketClient.sendRaw(node.ipAddress, mapOf("type" to "BLOCK_PROPOSAL", "data" to json))
            }
        }
    }

    private fun hashBlock(block: Block): String =
        sha256("${block.blockNumber}:${block.previousHash}:${block.timestamp}:${block.proposerPubKey}:${block.transactionsJson}")

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun txToJson(tx: Transaction): String =
        "{\"type\":\"${tx.type}\",\"amount\":${tx.amount}}"

    private fun parseTransactions(json: String): List<Transaction> =
        JSONArray(json).let { arr ->
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Transaction(
                    type = obj.getString("type"),
                    fromIp = obj.optString("fromIp", null),
                    toIp = obj.optString("toIp", null),
                    amount = obj.getDouble("amount")
                )
            }
        }

    suspend fun getBalance(ip: String): Double = withContext(Dispatchers.IO) { db.accountDao().getBalance(ip) ?: 0.0 }
}
'@

# -------------------------------------------------------
# CHAT SYSTEM (tidak banyak perubahan)
# -------------------------------------------------------
Write-FileUTF8 "$base/chat/ChatMessage.kt" @'
package com.ghalbitnet.meshx2.chat
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: String, val senderName: String,
    val content: String, val contentType: String = "TEXT",
    val filePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isSent: Boolean
)
'@

Write-FileUTF8 "$base/chat/ChatDao.kt" @'
package com.ghalbitnet.meshx2.chat
import androidx.room.*

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessages(chatId: String): List<ChatMessage>
    @Query("SELECT DISTINCT chatId FROM chat_messages ORDER BY chatId ASC")
    fun getChatIds(): List<String>
    @Insert
    fun insertMessage(message: ChatMessage)
}
'@

Write-FileUTF8 "$base/chat/ChatDatabase.kt" @'
package com.ghalbitnet.meshx2.chat
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ChatMessage::class], version = 2, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    companion object {
        @Volatile private var INSTANCE: ChatDatabase? = null
        fun getInstance(context: Context): ChatDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, ChatDatabase::class.java, "ghalbit_chat")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
'@

Write-FileUTF8 "$base/chat/SecureChatManager.kt" @'
package com.ghalbitnet.meshx2.chat
import android.util.Base64
import com.ghalbitnet.meshx2.model.SecurePacket
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.security.CryptoEngine
import com.ghalbitnet.meshx2.security.KeyStoreManager
import java.util.UUID

object SecureChatManager {
    private val sharedSecrets = mutableMapOf<String, ByteArray>()

    fun sendEncryptedMessage(message: String, destinationIp: String, keyStore: KeyStoreManager) {
        val destPubKey = keyStore.getPeerKey(destinationIp) ?: return
        val sharedSecret = sharedSecrets.getOrPut(destinationIp) {
            CryptoEngine.deriveSharedSecret(keyStore.privateKey, CryptoEngine.base64ToPublicKey(destPubKey))
        }
        val encrypted = CryptoEngine.encrypt(message.toByteArray(), sharedSecret)
        val securePacket = SecurePacket(
            sourcePublicKey = keyStore.publicKeyBase64,
            destinationPublicKey = destPubKey,
            encryptedPayload = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            packetId = UUID.randomUUID().toString()
        )
        MeshSocketClient.sendSecure(destinationIp, securePacket)
    }

    fun decryptReceivedPacket(securePacket: SecurePacket, keyStore: KeyStoreManager): String? {
        return try {
            val senderPubKey = CryptoEngine.base64ToPublicKey(securePacket.sourcePublicKey)
            val sharedSecret = sharedSecrets.getOrPut(securePacket.sourcePublicKey) {
                CryptoEngine.deriveSharedSecret(keyStore.privateKey, senderPubKey)
            }
            val encryptedBytes = Base64.decode(securePacket.encryptedPayload, Base64.NO_WRAP)
            String(CryptoEngine.decrypt(encryptedBytes, sharedSecret))
        } catch (e: Exception) { null }
    }
}
'@

Write-FileUTF8 "$base/chat/ChatAdapter.kt" @'
package com.ghalbitnet.meshx2.chat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ghalbitnet.meshx2.R
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int) = if (messages[position].isSent) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == VIEW_TYPE_SENT) R.layout.item_chat_sent else R.layout.item_chat_received
        return MessageViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false), viewType)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messages[position]
        holder.content.text = when (msg.contentType) {
            "IMAGE" -> "[Gambar]"
            "AUDIO" -> "[Suara]"
            else -> msg.content
        }
        holder.time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
        if (holder.senderName != null) holder.senderName.text = msg.senderName
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
        val content: TextView = itemView.findViewById(R.id.tvMessageContent)
        val time: TextView = itemView.findViewById(R.id.tvMessageTime)
        val senderName: TextView? = if (viewType == VIEW_TYPE_RECEIVED) itemView.findViewById(R.id.tvSenderName) else null
    }
}
'@

Write-FileUTF8 "$base/chat/ChatActivity.kt" @'
package com.ghalbitnet.meshx2.chat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.security.KeyStoreManager
import kotlinx.coroutines.*

class ChatActivity : AppCompatActivity() {
    private lateinit var recyclerChat: RecyclerView
    private lateinit var edtMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var tvChatTitle: TextView
    private lateinit var adapter: ChatAdapter
    private lateinit var chatDb: ChatDatabase
    private lateinit var keyStore: KeyStoreManager
    private var peerNode: MeshNode? = null
    private val chatId: String by lazy {
        val peerIp = peerNode?.ipAddress ?: "unknown"
        val peerName = peerNode?.name ?: peerIp
        if ("Me" < peerName) "Me:$peerIp" else "$peerIp:Me"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val packetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val source = intent?.getStringExtra("source") ?: return
            val payload = intent.getStringExtra("payload") ?: return
            Log.d("GHALBIT", "ChatActivity received broadcast from $source: $payload")
            if (peerNode != null && source.contains(peerNode!!.ipAddress)) {
                val msg = ChatMessage(
                    chatId = chatId,
                    senderName = source,
                    content = payload,
                    contentType = "TEXT",
                    timestamp = System.currentTimeMillis(),
                    isSent = false
                )
                scope.launch(Dispatchers.IO) {
                    chatDb.chatDao().insertMessage(msg)
                    withContext(Dispatchers.Main) { loadMessages() }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        recyclerChat = findViewById(R.id.recyclerChat)
        edtMessage = findViewById(R.id.edtMessage)
        btnSend = findViewById(R.id.btnSend)
        tvChatTitle = findViewById(R.id.tvChatTitle)

        findViewById<Button>(R.id.btnAttach).apply { text = "\uD83D\uDCCE"; setOnClickListener { Toast.makeText(this@ChatActivity, "Fitur akan datang", Toast.LENGTH_SHORT).show() } }
        findViewById<Button>(R.id.btnRecord).apply { text = "\uD83C\uDFA4"; setOnClickListener { Toast.makeText(this@ChatActivity, "Fitur akan datang", Toast.LENGTH_SHORT).show() } }
        btnSend.text = "\u27A4\uFE0F"
        findViewById<Button>(R.id.btnCall).apply { text = "\uD83D\uDCDE"; setOnClickListener { Toast.makeText(this@ChatActivity, "Fitur akan datang", Toast.LENGTH_SHORT).show() } }

        keyStore = KeyStoreManager(this)
        chatDb = ChatDatabase.getInstance(this)

        val peerIp = intent.getStringExtra("peerIp")
        val peerName = intent.getStringExtra("peerName")
        if (peerIp != null && peerName != null) {
            peerNode = MeshNode(name = peerName, ipAddress = peerIp, online = true)
        } else {
            peerNode = DiscoveryManager.discoverNodes().firstOrNull { it.online }
        }

        tvChatTitle.text = peerNode?.name ?: "Chat (tidak ada node)"
        if (peerNode == null) btnSend.isEnabled = false

        adapter = ChatAdapter(emptyList())
        recyclerChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recyclerChat.adapter = adapter
        loadMessages()

        btnSend.setOnClickListener {
            val text = edtMessage.text.toString().trim()
            if (text.isNotEmpty() && peerNode != null) {
                SecureChatManager.sendEncryptedMessage(text, peerNode!!.ipAddress, keyStore)
                val msg = ChatMessage(chatId = chatId, senderName = "Me", content = text, isSent = true)
                scope.launch(Dispatchers.IO) {
                    chatDb.chatDao().insertMessage(msg)
                    withContext(Dispatchers.Main) { loadMessages() }
                }
                edtMessage.text.clear()
            }
        }

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(packetReceiver, IntentFilter("com.ghalbitnet.meshx2.NEW_MESH_PACKET"))
    }

    private fun loadMessages() {
        scope.launch(Dispatchers.IO) {
            adapter = ChatAdapter(chatDb.chatDao().getMessages(chatId))
            withContext(Dispatchers.Main) {
                recyclerChat.adapter = adapter
                recyclerChat.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(packetReceiver)
        scope.cancel()
        super.onDestroy()
    }
}
'@

Write-FileUTF8 "$base/chat/ContactListActivity.kt" @'
package com.ghalbitnet.meshx2.chat

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.discovery.DiscoveryManager

class ContactListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_list)
        val listView = findViewById<ListView>(R.id.listContacts)
        val nodes = DiscoveryManager.discoverNodes().filter { it.online }
        if (nodes.isEmpty()) {
            findViewById<TextView>(R.id.tvEmpty).text = "Tidak ada kontak online"
            return
        }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, nodes.map { it.name })
        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = nodes[position]
            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra("peerIp", selected.ipAddress)
                putExtra("peerName", selected.name)
            })
        }
    }
}
'@

Write-FileUTF8 "$base/chat/MessagingReceiver.kt" @'
package com.ghalbitnet.meshx2.chat

import com.ghalbitnet.meshx2.model.SecurePacket
import com.ghalbitnet.meshx2.security.KeyStoreManager

object MessagingReceiver {
    fun onSecurePacket(secure: SecurePacket, keyStore: KeyStoreManager, chatDb: ChatDatabase) {
        val plaintext = SecureChatManager.decryptReceivedPacket(secure, keyStore) ?: return
        val chatId = "Me:${secure.sourcePublicKey.take(8)}"
        val msg = ChatMessage(
            chatId = chatId,
            senderName = secure.sourcePublicKey.take(8),
            content = plaintext,
            isSent = false
        )
        kotlinx.coroutines.runBlocking {
            chatDb.chatDao().insertMessage(msg)
        }
    }
}
'@

# -------------------------------------------------------
# MONITOR (tetap)
# -------------------------------------------------------
Write-FileUTF8 "$base/monitor/NetworkActivity.kt" @'
package com.ghalbitnet.meshx2.monitor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.model.MeshNode
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.random.Random

class NetworkActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var txtNetwork: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 5000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network)
        Configuration.getInstance().userAgentValue = packageName
        txtNetwork = findViewById(R.id.txtNetwork)
        mapView = findViewById(R.id.map)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(GeoPoint(-6.2, 106.8))
        loadNodes()
        handler.postDelayed(refreshRunnable, refreshInterval)
    }

    private val refreshRunnable = object : Runnable {
        override fun run() { loadNodes(); handler.postDelayed(this, refreshInterval) }
    }

    private fun loadNodes() {
        val nodes = DiscoveryManager.discoverNodes()
        txtNetwork.text = "GHALBITNET MAP: ${nodes.size} node(s)"
        mapView.overlays.removeAll { it is Marker }
        nodes.forEach { addMarker(it) }
        mapView.invalidate()
    }

    private fun addMarker(node: MeshNode) {
        val marker = Marker(mapView)
        marker.position = if (node.latitude != 0.0 && node.longitude != 0.0) GeoPoint(node.latitude, node.longitude)
        else GeoPoint(-6.2 + Random(node.ipAddress.hashCode()).nextDouble(-0.05, 0.05), 106.8 + Random(node.ipAddress.hashCode()).nextDouble(-0.05, 0.05))
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = node.name
        marker.snippet = "IP: ${node.ipAddress}\nTrust: ${node.trusted}%\nBalance: ${node.balance} GHBT"
        marker.icon = ContextCompat.getDrawable(this, when { node.trusted >= 70 -> R.drawable.ic_marker_green; node.trusted >= 40 -> R.drawable.ic_marker_yellow; else -> R.drawable.ic_marker_red })
        mapView.overlays.add(marker)
    }

    override fun onPause() { super.onPause(); handler.removeCallbacks(refreshRunnable) }
    override fun onResume() { super.onResume(); handler.postDelayed(refreshRunnable, refreshInterval) }
}
'@

# -------------------------------------------------------
# SERVICES (stub)
# -------------------------------------------------------
Write-FileUTF8 "$base/service/MeshService.kt" @'
package com.ghalbitnet.meshx2.service
import android.app.Service
import android.content.Intent
import android.os.IBinder
class MeshService : Service() { override fun onBind(intent: Intent?): IBinder? = null }
'@

Write-FileUTF8 "$base/service/MeshVpnService.kt" @'
package com.ghalbitnet.meshx2.service
import android.net.VpnService
class MeshVpnService : VpnService()
'@

Write-FileUTF8 "$base/service/GatewayService.kt" @'
package com.ghalbitnet.meshx2.service
import android.app.Service
import android.content.Intent
import android.os.IBinder
class GatewayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
'@

Write-FileUTF8 "$base/wireguard/WireGuardMeshManager.kt" @'
package com.ghalbitnet.meshx2.wireguard

import android.content.Context

class WireGuardMeshManager(private val context: Context) {
    suspend fun startMesh(ipAddress: String = "10.0.0.1/24", listenPort: Int = 51820) {}
    fun addPeer(ip: String, publicKey: String, endpoint: String? = null) {}
    fun removePeer(ip: String) {}
    fun getMyIp(): String = "10.0.0.3"
    fun getPeerStats(ip: String): Pair<Long, Long>? = null
    fun stop() {}
}
'@

# -------------------------------------------------------
# MAIN ACTIVITY (disempurnakan)
# -------------------------------------------------------
Write-FileUTF8 "$base/MainActivity.kt" @'
package com.ghalbitnet.meshx2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ghalbitnet.meshx2.blockchain.Block
import com.ghalbitnet.meshx2.blockchain.BlockchainLedger
import com.ghalbitnet.meshx2.chat.ChatDatabase
import com.ghalbitnet.meshx2.chat.ContactListActivity
import com.ghalbitnet.meshx2.chat.MessagingReceiver
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.discovery.UdpDiscovery
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.monitor.NetworkActivity
import com.ghalbitnet.meshx2.nearby.NearbyManager
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.network.MeshSocketServer
import com.ghalbitnet.meshx2.routing.MeshRegistry
import com.ghalbitnet.meshx2.routing.RouteDiscovery
import com.ghalbitnet.meshx2.security.CryptoEngine
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.service.GatewayService
import com.ghalbitnet.meshx2.token.TokenManager
import com.ghalbitnet.meshx2.wifi.WifiDirectManager
import com.ghalbitnet.meshx2.wireguard.WireGuardMeshManager
import kotlinx.coroutines.launch
import kotlin.random.Random
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    private lateinit var txtStatus: TextView
    private lateinit var txtNodes: TextView
    private lateinit var txtPing: TextView
    private lateinit var txtBalance: TextView
    private lateinit var txtLog: TextView
    private lateinit var keyStore: KeyStoreManager
    private var wifiDirect: WifiDirectManager? = null
    private var nearby: NearbyManager? = null
    private lateinit var chatDb: ChatDatabase
    private var wgManager: WireGuardMeshManager? = null
    private lateinit var ledger: BlockchainLedger
    private var myIp: String = "10.0.0.3"

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { Log.d("GHALBIT", "FILE_SELECTED uri=$uri"); txtLog.append("\nFile selected: $uri") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        txtStatus = findViewById(R.id.txtStatus)
        txtNodes = findViewById(R.id.txtNodes)
        txtPing = findViewById(R.id.txtPing)
        txtBalance = findViewById(R.id.txtBalance)
        txtLog = findViewById(R.id.txtLog)
        txtLog.movementMethod = ScrollingMovementMethod()

        keyStore = KeyStoreManager(this)
        chatDb = ChatDatabase.getInstance(this)
        TokenManager.init(this)
        myIp = getLocalIpAddress() ?: "10.0.0.3"
        RouteDiscovery.init(this, myIp)
        ledger = BlockchainLedger(this, keyStore, myIp)

        // Pembersihan rute berkala
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(300000)
                RouteDiscovery.clearExpiredRoutes()
            }
        }

        if (!allPermissionsGranted()) {
            requestPermissions(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.NEARBY_WIFI_DEVICES
            ), 42)
        } else startMesh()
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GHALBIT", "Failed to get local IP", e)
        }
        return null
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startMesh() {
        Log.d("GHALBIT", "Starting mesh services...")

        wgManager = WireGuardMeshManager(this)
        lifecycleScope.launch { wgManager?.startMesh("10.0.0.3/24") }

        startService(Intent(this, GatewayService::class.java))

        UdpDiscovery.init(keyStore)

        MeshSocketServer.onBlock = { blockJson ->
            lifecycleScope.launch {
                try {
                    val block = Block(
                        blockNumber = blockJson.getLong("blockNumber"),
                        previousHash = blockJson.getString("previousHash"),
                        timestamp = blockJson.getLong("timestamp"),
                        proposerIp = blockJson.getString("proposerIp"),
                        proposerPubKey = blockJson.optString("proposerPubKey", ""),
                        transactionsJson = blockJson.getJSONArray("transactions").toString(),
                        signature = blockJson.optString("signature", "")
                    )
                    ledger.receiveBlock(block)
                    updateBalance()
                    Log.d("GHALBIT", "Block received and processed: ${block.blockNumber}")
                } catch (e: Exception) {
                    Log.e("GHALBIT", "Failed to process block", e)
                }
            }
        }

        MeshSocketServer.start(
            onPacket = { p ->
                try {
                    var displayPayload = p.payload
                    if (p.encrypted) {
                        val peerPubKey = keyStore.getPeerKey(p.source) ?: ""
                        if (peerPubKey.isNotEmpty()) {
                            try {
                                val sharedSecret = CryptoEngine.deriveSharedSecret(
                                    keyStore.privateKey,
                                    CryptoEngine.base64ToPublicKey(peerPubKey)
                                )
                                val encBytes = Base64.decode(p.payload, Base64.DEFAULT)
                                val decrypted = CryptoEngine.decrypt(encBytes, sharedSecret)
                                displayPayload = String(decrypted)
                            } catch (e: Exception) {
                                displayPayload = "[Decryption failed: ${e.message}]"
                            }
                        } else {
                            displayPayload = "[No key for ${p.source}]"
                        }
                    }
                    Log.d("GHALBIT", "onPacket called, type=${p.type}, payload=$displayPayload")
                    runOnUiThread {
                        txtLog.append("\nPACKET: ${p.type} from ${p.source}")
                        if (p.type == "MESH_PACKET" && displayPayload.isNotEmpty()) {
                            txtLog.append("\n  Message: $displayPayload")
                        }
                        val scrollAmount = txtLog.layout?.getLineTop(txtLog.lineCount) ?: 0
                        txtLog.scrollTo(0, scrollAmount)
                    }

                    val intent = Intent("com.ghalbitnet.meshx2.NEW_MESH_PACKET")
                    intent.putExtra("source", p.source)
                    intent.putExtra("payload", displayPayload)
                    intent.putExtra("encrypted", false)
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                } catch (e: Exception) {
                    Log.e("GHALBIT", "onPacket error", e)
                }
            },
            onSecure = { secure ->
                try {
                    runOnUiThread { txtLog.append("\nSECURE PACKET from ${secure.sourcePublicKey.take(8)}...") }
                    MessagingReceiver.onSecurePacket(secure, keyStore, chatDb)
                } catch (e: Exception) {
                    Log.e("GHALBIT", "onSecure error", e)
                }
            }
        )

        UdpDiscovery.listen { name, ip, pubKey ->
            if (pubKey.isNotEmpty()) keyStore.storePeerKey(ip, pubKey)
            DiscoveryManager.addNode(MeshNode(name = name, ipAddress = ip, publicKey = pubKey, online = true))
            runOnUiThread { updateUI(); txtLog.append("\nNode discovered: $name") }
        }

        val myName = "X2-${Random.nextInt(1000)}"
        UdpDiscovery.broadcastNode(myName)

        wifiDirect = WifiDirectManager(this)
        nearby = NearbyManager(this, keyStore)

        findViewById<Button>(R.id.btnMesh).setOnClickListener {
            txtStatus.text = "ONLINE"; updateUI(); txtLog.append("\nMESH ENABLED")
        }
        findViewById<Button>(R.id.btnChat).setOnClickListener {
            startActivity(Intent(this, ContactListActivity::class.java))
        }
        findViewById<Button>(R.id.btnFile).setOnClickListener { filePicker.launch("*/*") }
        findViewById<Button>(R.id.btnSOS).setOnClickListener {
            txtLog.append("\nSOS SIGNAL SENT")
            Toast.makeText(this, "SOS ACTIVE", Toast.LENGTH_SHORT).show()
            DiscoveryManager.discoverNodes().forEach { node ->
                try {
                    MeshSocketClient.send(
                        node.ipAddress,
                        MeshPacket(
                            packetId = "SOS-" + System.currentTimeMillis(),
                            source = keyStore.publicKeyBase64.take(8),
                            destination = node.ipAddress,
                            type = "SOS",
                            payload = "EMERGENCY"
                        )
                    )
                } catch (_: Exception) {}
            }
        }
        findViewById<Button>(R.id.btnNetwork).setOnClickListener {
            startActivity(Intent(this, NetworkActivity::class.java))
        }

        updateUI()
    }

    private fun updateUI() {
        DiscoveryManager.discoverNodes().let {
            txtNodes.text = it.size.toString()
            txtPing.text = "${Random.nextInt(10, 50)} ms"
            it.forEach { n -> MeshRegistry.updateNode(n) }
        }
        updateBalance()
    }

    private fun updateBalance() {
        lifecycleScope.launch {
            val balance = ledger.getBalance(myIp)
            Log.d("GHALBIT", "Balance for $myIp: $balance")
            runOnUiThread {
                txtBalance.text = "%.2f GHBT".format(balance)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && allPermissionsGranted()) startMesh()
    }

    override fun onDestroy() {
        wifiDirect?.cleanup(); nearby?.stop(); MeshSocketServer.stop(); wgManager?.stop(); super.onDestroy()
    }
}
'@

Write-Host "`nProyek GhalbitMesX2 Improved berhasil ditulis. Menjalankan build...`n" -ForegroundColor Green
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    Write-Host "BUILD SUKSES. APK tersedia di app/build/outputs/apk/debug/app-debug.apk" -ForegroundColor Cyan
} else {
    Write-Host "Build gagal, periksa error di atas." -ForegroundColor Red
}