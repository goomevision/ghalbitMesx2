# build_ghalbitnet_complete_v2.ps1
# Enhanced complete builder for GhalbitMesX2 mesh app
# Includes: wrapper gen, chat receive integration, token UI, latest deps

$rootDir = Get-Location
$projName = "ghalbitMesX2"
Set-Location $rootDir

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Membangun proyek GhalbitMesX2 v2 (enhanced)..." -ForegroundColor Cyan

# Generate Gradle Wrapper if missing
if (-not (Test-Path "gradlew")) {
    Write-Host "Gradle wrapper not found. Creating wrapper..." -ForegroundColor Yellow
    if (Get-Command gradle -ErrorAction SilentlyContinue) {
        gradle wrapper --gradle-version 8.7 | Out-Null
    } else {
        Write-Host "Gradle not installed globally. Please install Gradle or Android Studio, then run again." -ForegroundColor Red
        exit 1
    }
}

# ------------------- ROOT BUILD FILES (updated versions) ------------------
Write-FileWithoutBOM "build.gradle" @'
buildscript {
    ext.kotlin_version = "2.0.0"
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath "com.android.tools.build:gradle:8.5.0"
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

Write-FileWithoutBOM "settings.gradle" @'
rootProject.name = "GhalbitMesX2"
include ':app'
'@

Write-FileWithoutBOM "gradle.properties" @'
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
# Suppress warnings for newer compileSdk
android.suppressUnsupportedCompileSdk=35
'@

# Pastikan wrapper properties memakai Gradle 8.7
Set-Content -Path "gradle\wrapper\gradle-wrapper.properties" -Value @'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
'@


# ------------------- APP BUILD.GRADLE (updated deps) ------------------
Write-FileWithoutBOM "app/build.gradle" @'
plugins {
    id 'com.android.application'
    id 'kotlin-android'
    id 'kotlin-kapt'
}

android {
    namespace 'com.ghalbitnet.meshx2'
    compileSdk 35

    defaultConfig {
        applicationId "com.ghalbitnet.meshx2"
        minSdk 26
        targetSdk 35
        versionCode 2
        versionName "2.0.0-enhanced"
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
    implementation 'androidx.core:core-ktx:1.13.1'
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'org.osmdroid:osmdroid-android:6.1.18'
    implementation 'com.google.android.gms:play-services-nearby:19.2.0'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'androidx.cardview:cardview:1.0.0'
    implementation 'androidx.room:room-runtime:2.6.1'
    implementation 'androidx.room:room-ktx:2.6.1'
    kapt 'androidx.room:room-compiler:2.6.1'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1'
}
'@

# ------------------- ANDROIDMANIFEST (additional permissions & activities) ------------------
Write-FileWithoutBOM "app/src/main/AndroidManifest.xml" @'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <!-- Android 12+ exact permissions -->
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
    <uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

    <application
        android:allowBackup="true"
        android:label="GhalbitMesX2 v2"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">

        <activity android:name=".monitor.NetworkActivity" android:exported="false" />
        <activity android:name=".chat.ChatActivity" android:exported="false"
            android:windowSoftInputMode="adjustResize"/>
        <activity android:name=".chat.ContactListActivity" android:exported="false" />
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service android:name=".service.MeshService" android:exported="false" />
        <service android:name=".service.MeshVpnService" android:permission="android.permission.BIND_VPN_SERVICE" />

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

# ------------------- RESOURCE FILES (unchanged mostly, but add ic_refresh) ------------------
Write-FileWithoutBOM "app/src/main/res/xml/file_paths.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-files-path name="external_files" path="." />
    <cache-path name="cache" path="." />
</paths>
'@

Write-FileWithoutBOM "app/src/main/res/values/strings.xml" @'
<resources>
    <string name="app_name">GhalbitMesX2</string>
</resources>
'@

# Existing drawables (ic_marker_green, yellow, red, bg_sent, bg_received, bg_message_input) ...
Write-FileWithoutBOM "app/src/main/res/drawable/ic_marker_green.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <size android:width="24dp" android:height="24dp"/>
    <solid android:color="#4CAF50"/>
    <stroke android:width="2dp" android:color="#FFFFFF"/>
</shape>
'@

Write-FileWithoutBOM "app/src/main/res/drawable/ic_marker_yellow.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <size android:width="24dp" android:height="24dp"/>
    <solid android:color="#FFC107"/>
    <stroke android:width="2dp" android:color="#FFFFFF"/>
</shape>
'@

Write-FileWithoutBOM "app/src/main/res/drawable/ic_marker_red.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <size android:width="24dp" android:height="24dp"/>
    <solid android:color="#F44336"/>
    <stroke android:width="2dp" android:color="#FFFFFF"/>
</shape>
'@

Write-FileWithoutBOM "app/src/main/res/drawable/bg_sent_message.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#DCF8C6"/>
    <corners android:radius="12dp"/>
</shape>
'@

Write-FileWithoutBOM "app/src/main/res/drawable/bg_received_message.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFFFFF"/>
    <corners android:radius="12dp"/>
</shape>
'@

Write-FileWithoutBOM "app/src/main/res/drawable/bg_message_input.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFFFFF"/>
    <stroke android:width="1dp" android:color="#DDDDDD"/>
    <corners android:radius="20dp"/>
</shape>
'@

# Add a refresh button icon (simple vector)
Write-FileWithoutBOM "app/src/main/res/drawable/ic_refresh.xml" @'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M17.65,6.35C16.2,4.9 14.2,4 12,4A8,8 0,0,0 4,12H2l3.3,3.3L8.6,12H6.6A6.4,6.4 0,0,1 12,6c2.2,0 4.1,1.4 4.8,3.4l1.8,-0.7C17.9,7.2 16.1,5.6 14,5.1V2.9L11,6.5l2.9,3.5V7.1C13.9,6.2 14.9,5.4 16.2,5.4c0.8,0 1.6,0.3 2.2,0.8z"/>
</vector>
'@

# Layouts with refresh buttons and token UI
Write-FileWithoutBOM "app/src/main/res/layout/activity_main.xml" @'
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
        <TextView android:text="TOKEN BALANCE" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:textColor="#FFFFFF" android:paddingTop="10dp"/>
        <TextView android:id="@+id/txtBalance" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:text="0.0 GHBT"
            android:textColor="#FF80AB" android:textSize="20sp"/>
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

Write-FileWithoutBOM "app/src/main/res/layout/activity_network.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical">
    <LinearLayout
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="horizontal" android:gravity="center_vertical"
        android:background="#1A1A2E" android:padding="8dp">
        <TextView android:id="@+id/txtNetwork" android:layout_width="0dp"
            android:layout_height="wrap_content" android:layout_weight="1"
            android:text="GHALBITNET" android:textColor="#FFFFFF" android:textSize="18sp"/>
        <ImageButton android:id="@+id/btnRefreshMap"
            android:layout_width="48dp" android:layout_height="48dp"
            android:src="@drawable/ic_refresh" android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Refresh"/>
    </LinearLayout>
    <org.osmdroid.views.MapView android:id="@+id/map"
        android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1"/>
</LinearLayout>
'@

Write-FileWithoutBOM "app/src/main/res/layout/activity_contact_list.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical" android:background="#ECE5DD">
    <LinearLayout
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:background="#075E54" android:orientation="horizontal"
        android:padding="8dp">
        <TextView
            android:layout_width="0dp" android:layout_height="wrap_content"
            android:layout_weight="1" android:text="Kontak Ghalbit"
            android:textSize="20sp" android:textColor="#FFFFFF"
            android:textStyle="bold" android:padding="4dp"/>
        <ImageButton android:id="@+id/btnRefreshContacts"
            android:layout_width="48dp" android:layout_height="48dp"
            android:src="@drawable/ic_refresh" android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Refresh" />
    </LinearLayout>
    <TextView android:id="@+id/tvEmpty"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:padding="16dp" android:textColor="#666666"/>
    <ListView android:id="@+id/listContacts"
        android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1"/>
</LinearLayout>
'@

# (activity_chat, item_chat_sent, item_chat_received recreated unchanged)
Write-FileWithoutBOM "app/src/main/res/layout/activity_chat.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical" android:background="#ECE5DD">
    <TextView android:id="@+id/tvChatTitle"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:textSize="18sp" android:textColor="#FFFFFF"
        android:background="#075E54" android:padding="14dp"
        android:textStyle="bold" android:gravity="center"/>
    <androidx.recyclerview.widget.RecyclerView android:id="@+id/recyclerChat"
        android:layout_width="match_parent" android:layout_height="0dp"
        android:layout_weight="1" android:padding="8dp"/>
    <LinearLayout
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="horizontal" android:background="#FFFFFF"
        android:paddingLeft="4dp" android:paddingRight="4dp"
        android:paddingTop="6dp" android:paddingBottom="6dp"
        android:gravity="center_vertical">
        <Button android:id="@+id/btnAttach"
            android:layout_width="36dp" android:layout_height="36dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:padding="0dp" android:layout_marginEnd="2dp" android:textSize="14sp"/>
        <EditText android:id="@+id/edtMessage"
            android:layout_width="0dp" android:layout_height="wrap_content"
            android:layout_weight="1" android:hint="Ketik pesan..."
            android:background="@drawable/bg_message_input" android:padding="10dp"
            android:layout_marginStart="2dp" android:layout_marginEnd="2dp"/>
        <Button android:id="@+id/btnRecord"
            android:layout_width="36dp" android:layout_height="36dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:padding="0dp" android:layout_marginStart="2dp"
            android:layout_marginEnd="2dp" android:textSize="14sp"/>
        <Button android:id="@+id/btnSend"
            android:layout_width="36dp" android:layout_height="36dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:padding="0dp" android:layout_marginStart="2dp"
            android:layout_marginEnd="2dp" android:textSize="14sp"/>
        <Button android:id="@+id/btnCall"
            android:layout_width="36dp" android:layout_height="36dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:padding="0dp" android:layout_marginStart="2dp" android:textSize="14sp"/>
    </LinearLayout>
</LinearLayout>
'@

Write-FileWithoutBOM "app/src/main/res/layout/item_chat_sent.xml" @'
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

Write-FileWithoutBOM "app/src/main/res/layout/item_chat_received.xml" @'
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

# ------------ KOTLIN SOURCE (enhanced) ------------
$base = "app/src/main/java/com/ghalbitnet/meshx2"

# Models (unchanged)
Write-FileWithoutBOM "$base/model/MeshNode.kt" @'
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
Write-FileWithoutBOM "$base/model/MeshPacket.kt" @'
package com.ghalbitnet.meshx2.model
data class MeshPacket(
    val packetId: String, val source: String, val destination: String,
    val type: String, val payload: String, val hopCount: Int = 0,
    val maxHop: Int = 5, val timestamp: Long = System.currentTimeMillis()
)
'@
Write-FileWithoutBOM "$base/model/SecurePacket.kt" @'
package com.ghalbitnet.meshx2.model
data class SecurePacket(
    val sourcePublicKey: String, val destinationPublicKey: String,
    val encryptedPayload: String, val packetId: String,
    val hopCount: Int = 0, val maxHop: Int = 5,
    val timestamp: Long = System.currentTimeMillis()
)
'@

# Security (unchanged)
Write-FileWithoutBOM "$base/security/CryptoEngine.kt" @'
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
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

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
        val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
        val cipher = Cipher.getInstance(AES_ALGO)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }
}
'@
Write-FileWithoutBOM "$base/security/KeyStoreManager.kt" @'
package com.ghalbitnet.meshx2.security
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.PrivateKey

class KeyStoreManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ghalbit_keys", Context.MODE_PRIVATE)

    val publicKeyBase64: String
        get() {
            var pub = prefs.getString("public_key", null)
            if (pub == null) {
                val kp = CryptoEngine.generateKeyPair()
                pub = CryptoEngine.publicKeyToBase64(kp.public)
                val priv = Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP)
                prefs.edit()
                    .putString("public_key", pub)
                    .putString("private_key", priv)
                    .apply()
            }
            return pub!!
        }

    val privateKey: PrivateKey
        get() {
            val privStr = prefs.getString("private_key", null)
                ?: throw IllegalStateException("No private key")
            val bytes = Base64.decode(privStr, Base64.NO_WRAP)
            val spec = java.security.spec.PKCS8EncodedKeySpec(bytes)
            val kf = java.security.KeyFactory.getInstance("EC")
            return kf.generatePrivate(spec)
        }

    fun storePeerKey(ip: String, publicKey: String) {
        prefs.edit().putString("peer_$ip", publicKey).apply()
    }

    fun getPeerKey(ip: String): String? = prefs.getString("peer_$ip", null)
}
'@

# Discovery (add accurate broadcast address calculation)
Write-FileWithoutBOM "$base/discovery/DiscoveryManager.kt" @'
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
Write-FileWithoutBOM "$base/discovery/UdpDiscovery.kt" @'
package com.ghalbitnet.meshx2.discovery
import android.util.Log
import com.ghalbitnet.meshx2.security.KeyStoreManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

object UdpDiscovery {
    private const val PORT = 45454
    private var running = false
    private var keyStore: KeyStoreManager? = null

    fun init(keyStore: KeyStoreManager) { this.keyStore = keyStore }

    private fun getBroadcastAddress(): InetAddress? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val netInt = interfaces.nextElement()
                if (!netInt.isLoopback && netInt.isUp) {
                    val addresses = netInt.interfaceAddresses
                    for (addr in addresses) {
                        val broadcast = addr.broadcast
                        if (broadcast != null) return broadcast
                    }
                }
            }
            InetAddress.getByName("255.255.255.255")
        } catch (e: Exception) {
            InetAddress.getByName("255.255.255.255")
        }
    }

    fun broadcastNode(nodeName: String) {
        val pubKey = keyStore?.publicKeyBase64 ?: ""
        Thread {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                val msg = "GHALBITX2:$nodeName:$pubKey"
                val data = msg.toByteArray()
                val broadcastAddr = getBroadcastAddress()
                val packet = DatagramPacket(data, data.size, broadcastAddr, PORT)
                socket.send(packet)
                socket.close()
                Log.d("GHALBIT", "UDP broadcast HELLO to ${broadcastAddr.hostAddress}: $nodeName")
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
                        val ip = p.address.hostAddress ?: "0.0.0.0"
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

# WiFi Direct (unchanged)
Write-FileWithoutBOM "$base/wifi/WifiDirectManager.kt" @'
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
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        manager?.stopPeerDiscovery(channel, null)
    }
}
'@
Write-FileWithoutBOM "$base/nearby/NearbyManager.kt" @'
package com.ghalbitnet.meshx2.nearby
import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.security.KeyStoreManager
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
                val text = String(payload.asBytes()!!)
                if (text.length > 30 && text.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
                    keyStore.storePeerKey("nearby:$endpointId", text)
                    DiscoveryManager.addNode(MeshNode(name = "Nearby-$endpointId", ipAddress = "nearby:$endpointId", online = true, publicKey = text))
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

    fun stop() { connectionClient.stopAllEndpoints() }
}
'@

# Network (unchanged)
Write-FileWithoutBOM "$base/network/MeshSocketClient.kt" @'
package com.ghalbitnet.meshx2.network
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.model.SecurePacket
import org.json.JSONObject
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

object MeshSocketClient {
    private const val PORT = 56565
    private const val TIMEOUT = 5000

    fun send(host: String, packet: MeshPacket) {
        Thread {
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
                }
                writer.println(json.toString())
                writer.flush()
            } catch (_: Exception) {} finally { try { socket?.close() } catch (_: Exception) {} }
        }.start()
    }

    fun sendSecure(host: String, secure: SecurePacket) {
        Thread {
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
        }.start()
    }
}
'@
Write-FileWithoutBOM "$base/network/MeshSocketServer.kt" @'
package com.ghalbitnet.meshx2.network
import android.util.Log
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.model.SecurePacket
import com.ghalbitnet.meshx2.routing.RelayEngine
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket

object MeshSocketServer {
    private const val PORT = 56565
    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false

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
                        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                        val line = reader.readLine() ?: continue
                        val json = JSONObject(line)
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
                                    timestamp = json.optLong("timestamp")
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
                        }
                        client.close()
                    } catch (e: Exception) {
                        if (running) Log.e("GHALBIT", "MeshSocketServer accept error", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("GHALBIT", "MeshSocketServer error", e)
            }
        }.start()
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }
}
'@

# Routing (unchanged)
Write-FileWithoutBOM "$base/routing/MeshRegistry.kt" @'
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
Write-FileWithoutBOM "$base/routing/RoutingTableEntry.kt" @'
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
Write-FileWithoutBOM "$base/routing/RoutingDao.kt" @'
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
Write-FileWithoutBOM "$base/routing/RoutingDatabase.kt" @'
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
Write-FileWithoutBOM "$base/routing/QosManager.kt" @'
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
Write-FileWithoutBOM "$base/routing/RouteDiscovery.kt" @'
package com.ghalbitnet.meshx2.routing
import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.model.MeshNode
import kotlinx.coroutines.*

object RouteDiscovery {
    private var db: RoutingDatabase? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            scope.launch { db = RoutingDatabase.getInstance(context); initialized = true }
        }
    }

    fun discoverAndUpdate(destinationIp: String, knownNodes: List<MeshNode>) {
        if (!initialized) return
        scope.launch {
            val nextHop = QosManager.selectBestNeighbor(destinationIp, knownNodes)
            if (nextHop != null) {
                db?.routingDao()?.insertEntry(RoutingTableEntry(
                    destinationIp = destinationIp, nextHopIp = nextHop.ipAddress,
                    hopCount = 1, latencyMs = nextHop.latency.toLong(), trustScore = nextHop.trusted
                ))
            }
        }
    }

    suspend fun getBestRoute(destIp: String): RoutingTableEntry? = try {
        withTimeout(1000L) { db?.routingDao()?.getRoutes(destIp)?.firstOrNull() }
    } catch (e: Exception) { null }
}
'@
Write-FileWithoutBOM "$base/routing/RelayEngine.kt" @'
package com.ghalbitnet.meshx2.routing
import android.util.Log
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.model.SecurePacket
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.security.KeyStoreManager
import kotlinx.coroutines.*

object RelayEngine {
    private val packetCache = mutableSetOf<String>()

    fun relayPacket(packet: MeshPacket) {
        if (packetCache.contains(packet.packetId) || packet.hopCount >= packet.maxHop) return
        packetCache.add(packet.packetId)
        val next = packet.copy(hopCount = packet.hopCount + 1)
        CoroutineScope(Dispatchers.IO).launch {
            val route = RouteDiscovery.getBestRoute(packet.destination)
            if (route != null) {
                try { MeshSocketClient.send(route.nextHopIp, next) } catch (_: Exception) {}
            } else {
                MeshRegistry.getNodes().filter { it.online }.forEach { node ->
                    try { MeshSocketClient.send(node.ipAddress, next) } catch (_: Exception) {}
                }
            }
        }
    }

    fun relaySecurePacket(secure: SecurePacket, keyStore: KeyStoreManager) {
        if (packetCache.contains(secure.packetId) || secure.hopCount >= secure.maxHop) return
        packetCache.add(secure.packetId)
        val next = secure.copy(hopCount = secure.hopCount + 1)
        MeshRegistry.getNodes().filter { it.online }.forEach { node ->
            MeshSocketClient.sendSecure(node.ipAddress, next)
        }
    }
}
'@

# Token (unchanged)
Write-FileWithoutBOM "$base/token/TokenTransaction.kt" @'
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
Write-FileWithoutBOM "$base/token/TokenDao.kt" @'
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
Write-FileWithoutBOM "$base/token/TokenDatabase.kt" @'
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
Write-FileWithoutBOM "$base/token/TokenManager.kt" @'
package com.ghalbitnet.meshx2.token
import android.content.Context
import android.util.Log
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
    fun getBalance(peerIp: String, callback: (Double) -> Unit) {
        if (!initialized) { callback(0.0); return }
        scope.launch {
            val balance = db.tokenDao().getBalance(peerIp) ?: 0.0
            withContext(Dispatchers.Main) { callback(balance) }
        }
    }
}
'@

# Reputation (unchanged)
Write-FileWithoutBOM "$base/reputation/ReputationManager.kt" @'
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

# ★ MESSAGING RECEIVER – integrasi chat ★
Write-FileWithoutBOM "$base/chat/MessagingReceiver.kt" @'
package com.ghalbitnet.meshx2.chat
import com.ghalbitnet.meshx2.model.SecurePacket
import com.ghalbitnet.meshx2.security.KeyStoreManager
import kotlinx.coroutines.*

object MessagingReceiver {
    private val listeners = mutableListOf<(chatId: String, msg: ChatMessage) -> Unit>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun registerListener(listener: (String, ChatMessage) -> Unit) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: (String, ChatMessage) -> Unit) {
        listeners.remove(listener)
    }

    fun onSecurePacket(packet: SecurePacket, keyStore: KeyStoreManager, chatDb: ChatDatabase) {
        scope.launch {
            val plaintext = SecureChatManager.decryptReceivedPacket(packet, keyStore)
            if (plaintext != null) {
                // Determine chatId: combination of my public key and sender's
                val myPub = keyStore.publicKeyBase64.take(8)
                val sender = packet.sourcePublicKey.take(8)
                val chatId = if (myPub < sender) "$myPub:$sender" else "$sender:$myPub"
                val senderName = "Node-${sender}" // Bisa diganti dengan lookup dari node registry
                val msg = ChatMessage(
                    chatId = chatId,
                    senderName = senderName,
                    content = plaintext,
                    isSent = false
                )
                chatDb.chatDao().insertMessage(msg)
                withContext(Dispatchers.Main) {
                    listeners.forEach { it(chatId, msg) }
                }
            }
        }
    }
}
'@

# Chat entities (ChatMessage, ChatDao, ChatDatabase) unchanged except version bump
Write-FileWithoutBOM "$base/chat/ChatMessage.kt" @'
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
Write-FileWithoutBOM "$base/chat/ChatDao.kt" @'
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
Write-FileWithoutBOM "$base/chat/ChatDatabase.kt" @'
package com.ghalbitnet.meshx2.chat
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ChatMessage::class], version = 3, exportSchema = false)
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

# SecureChatManager (unchanged, but now used by MessagingReceiver)
Write-FileWithoutBOM "$base/chat/SecureChatManager.kt" @'
package com.ghalbitnet.meshx2.chat
import android.util.Base64
import android.util.Log
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

# ChatAdapter (unchanged)
Write-FileWithoutBOM "$base/chat/ChatAdapter.kt" @'
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

# ChatActivity (updates: incoming listener, correct chatId)
Write-FileWithoutBOM "$base/chat/ChatActivity.kt" @'
package com.ghalbitnet.meshx2.chat
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
        val myPub = keyStore.publicKeyBase64.take(8)
        val peerPub = peerNode?.publicKey?.take(8) ?: "unknown"
        if (myPub < peerPub) "$myPub:$peerPub" else "$peerPub:$myPub"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val messageListener: (String, ChatMessage) -> Unit = { _, _ ->
        runOnUiThread { loadMessages() }
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
        MessagingReceiver.registerListener(messageListener)

        btnSend.setOnClickListener {
            val text = edtMessage.text.toString().trim()
            if (text.isNotEmpty() && peerNode != null) {
                sendMessage(text)
                edtMessage.text.clear()
            }
        }
    }

    private fun sendMessage(content: String) {
        val node = peerNode ?: return
        SecureChatManager.sendEncryptedMessage(content, node.ipAddress, keyStore)
        val msg = ChatMessage(chatId = chatId, senderName = "Me", content = content, isSent = true)
        scope.launch(Dispatchers.IO) {
            chatDb.chatDao().insertMessage(msg)
            withContext(Dispatchers.Main) { loadMessages() }
        }
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
        super.onDestroy()
        MessagingReceiver.unregisterListener(messageListener)
        scope.cancel()
    }
}
'@

# ContactListActivity (with refresh button)
Write-FileWithoutBOM "$base/chat/ContactListActivity.kt" @'
package com.ghalbitnet.meshx2.chat
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageButton
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
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
        val btnRefresh = findViewById<ImageButton>(R.id.btnRefreshContacts)
        refreshList(listView, tvEmpty)
        btnRefresh.setOnClickListener { refreshList(listView, tvEmpty) }
        listView.setOnItemClickListener { _, _, position, _ ->
            val nodes = DiscoveryManager.discoverNodes().filter { it.online }
            if (position < nodes.size) {
                val selected = nodes[position]
                startActivity(Intent(this, ChatActivity::class.java).apply {
                    putExtra("peerIp", selected.ipAddress)
                    putExtra("peerName", selected.name)
                })
            }
        }
    }

    private fun refreshList(listView: ListView, tvEmpty: TextView) {
        val nodes = DiscoveryManager.discoverNodes().filter { it.online }
        if (nodes.isEmpty()) {
            tvEmpty.text = "Tidak ada kontak online"
            listView.adapter = null
        } else {
            tvEmpty.text = ""
            listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, nodes.map { it.name })
        }
    }
}
'@

# NetworkActivity (with refresh)
Write-FileWithoutBOM "$base/monitor/NetworkActivity.kt" @'
package com.ghalbitnet.meshx2.monitor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
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

        findViewById<ImageButton>(R.id.btnRefreshMap).setOnClickListener { loadNodes() }
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

# Services (stubs, unchanged)
Write-FileWithoutBOM "$base/service/MeshService.kt" @'
package com.ghalbitnet.meshx2.service
import android.app.Service
import android.content.Intent
import android.os.IBinder

Write-FileWithoutBOM "$base/service/MeshService.kt" @'
package com.ghalbitnet.meshx2.service
import android.app.Service
import android.content.Intent
import android.os.IBinder

class MeshService : Service() {
    override fun onBind(p0: Intent?): IBinder? = null
}
'@

Write-FileWithoutBOM "$base/service/MeshVpnService.kt" @'
package com.ghalbitnet.meshx2.service
import android.net.VpnService

class MeshVpnService : VpnService()
'@

# ★ MainActivity (integrated with MessagingReceiver, token balance, SOS broadcast) ★
Write-FileWithoutBOM "$base/MainActivity.kt" @'
package com.ghalbitnet.meshx2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ghalbitnet.meshx2.chat.ChatDatabase
import com.ghalbitnet.meshx2.chat.ContactListActivity
import com.ghalbitnet.meshx2.chat.MessagingReceiver
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.discovery.UdpDiscovery
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.monitor.NetworkActivity
import com.ghalbitnet.meshx2.nearby.NearbyManager
import com.ghalbitnet.meshx2.network.MeshSocketServer
import com.ghalbitnet.meshx2.routing.MeshRegistry
import com.ghalbitnet.meshx2.routing.RouteDiscovery
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.token.TokenManager
import com.ghalbitnet.meshx2.wifi.WifiDirectManager
import kotlin.random.Random

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

        keyStore = KeyStoreManager(this)
        chatDb = ChatDatabase.getInstance(this)
        TokenManager.init(this)
        RouteDiscovery.init(this)

        if (!allPermissionsGranted()) {
            requestPermissions(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.NEARBY_WIFI_DEVICES
            ), 42)
        } else startMesh()
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startMesh() {
        Log.d("GHALBIT", "Starting mesh services...")
        UdpDiscovery.init(keyStore)
        MeshSocketServer.start(
            onPacket = { p -> runOnUiThread { txtLog.append("\nPACKET: ${p.type} from ${p.source}") } },
            onSecure = { secure ->
                runOnUiThread { txtLog.append("\nSECURE PACKET from ${secure.sourcePublicKey.take(8)}...") }
                MessagingReceiver.onSecurePacket(secure, keyStore, chatDb)
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

        // UI buttons
        findViewById<Button>(R.id.btnMesh).setOnClickListener { txtStatus.text = "ONLINE"; updateUI(); txtLog.append("\nMESH ENABLED") }
        findViewById<Button>(R.id.btnChat).setOnClickListener { startActivity(Intent(this, ContactListActivity::class.java)) }
        findViewById<Button>(R.id.btnFile).setOnClickListener { filePicker.launch("*/*") }
        findViewById<Button>(R.id.btnSOS).setOnClickListener {
            txtLog.append("\nSOS SIGNAL SENT TO ALL NODES")
            Toast.makeText(this, "SOS ACTIVE", Toast.LENGTH_SHORT).show()
            DiscoveryManager.discoverNodes().forEach { node ->
                try {
                    com.ghalbitnet.meshx2.network.MeshSocketClient.send(
                        node.ipAddress,
                        com.ghalbitnet.meshx2.model.MeshPacket(
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
        findViewById<Button>(R.id.btnNetwork).setOnClickListener { startActivity(Intent(this, NetworkActivity::class.java)) }
        updateBalance()
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
        TokenManager.getBalance("self") { balance -> txtBalance.text = "%.2f GHBT".format(balance) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && allPermissionsGranted()) startMesh()
    }

    override fun onDestroy() {
        wifiDirect?.cleanup(); nearby?.stop(); MeshSocketServer.stop(); super.onDestroy()
    }
}
'@

Write-Host "Proyek versi 2 selesai ditulis. Memulai build..." -ForegroundColor Green
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    Write-Host "BUILD SUKSES. APK tersedia di app/build/outputs/apk/debug/app-debug.apk" -ForegroundColor Cyan
} else {
    Write-Host "Build gagal, periksa error di atas." -ForegroundColor Red
}

$buildExit = $LASTEXITCODE
if ($buildExit -eq 0) {
    Write-Host "BUILD SUKSES. APK tersedia di app/build/outputs/apk/debug/app-debug.apk" -ForegroundColor Cyan
} else {
    Write-Host "Build gagal dengan exit code $buildExit. Periksa error di atas." -ForegroundColor Red
    exit $buildExit
}