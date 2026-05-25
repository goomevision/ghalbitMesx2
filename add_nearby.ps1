# nearby_integration.ps1
# Skrip untuk menambahkan Nearby Connections Manager ke GhalbitMesX2
# Pastikan dijalankan dari root proyek (tempat build.gradle berada)

Set-Location C:\project\Ghalbitnet\ghalbitMesx2

# Fungsi tulis file UTF-8 tanpa BOM
function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Menambahkan Nearby Connections Manager..." -ForegroundColor Cyan

# 1. Pastikan dependensi play-services-nearby (sudah ada di app/build.gradle, tapi kita cek)
$appGradle = Get-Content "app\build.gradle" -Raw
if ($appGradle -notmatch "play-services-nearby") {
    Write-Host "Menambahkan dependensi play-services-nearby..." -ForegroundColor Yellow
    $newDep = "`n    implementation 'com.google.android.gms:play-services-nearby:18.3.0'"
    $appGradle = $appGradle -replace "(dependencies\s*\{)", "`$1$newDep"
    Write-FileWithoutBOM "app\build.gradle" $appGradle
    Write-Host "Dependensi ditambahkan." -ForegroundColor Green
} else {
    Write-Host "Dependensi play-services-nearby sudah ada." -ForegroundColor Green
}

# 2. Perbarui AndroidManifest.xml (tambahkan izin BLUETOOTH_ADVERTISE untuk Nearby)
$manifest = Get-Content "app\src\main\AndroidManifest.xml" -Raw
$neededPermissions = @(
    'android.permission.BLUETOOTH_ADVERTISE',
    'android.permission.BLUETOOTH_SCAN',
    'android.permission.BLUETOOTH_CONNECT' # sudah ada? cek
)
foreach ($perm in $neededPermissions) {
    if ($manifest -notmatch $perm) {
        $manifest = $manifest -replace "(<application)", "    <uses-permission android:name=`"$perm`" />`n`$1"
    }
}
Write-FileWithoutBOM "app\src\main\AndroidManifest.xml" $manifest
Write-Host "Izin Nearby diperbarui." -ForegroundColor Green

# 3. Buat NearbyManager.kt yang sudah diperbaiki (tidak ada error)
Write-Host "Membuat NearbyManager.kt..." -ForegroundColor Yellow

$nearbyCode = @'
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

    // Callback siklus hidup koneksi (digunakan untuk advertising dan request)
    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Otomatis terima koneksi
            Log.d("GHALBIT", "Nearby connection initiated from ${info.endpointName}")
            connectionClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.d("GHALBIT", "Nearby connected to $endpointId")
                // Daftarkan sebagai node mesh
                val nodeName = "Nearby-$endpointId"
                DiscoveryManager.addNode(MeshNode(
                    name = nodeName,
                    ipAddress = "nearby:$endpointId",
                    online = true,
                    publicKey = "" // akan diisi setelah menerima payload
                ))
                // Kirim kunci publik kita
                val pubKey = keyStore.publicKeyBase64
                connectionClient.sendPayload(endpointId, Payload.fromBytes(pubKey.toByteArray()))
            } else {
                Log.e("GHALBIT", "Nearby connection failed")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d("GHALBIT", "Nearby disconnected from $endpointId")
            DiscoveryManager.addNode(MeshNode(
                name = "Nearby-$endpointId",
                ipAddress = "nearby:$endpointId",
                online = false
            ))
        }
    }

    // Payload callback untuk menerima data
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val receivedBytes = payload.asBytes()!!
                val receivedText = String(receivedBytes)
                Log.d("GHALBIT", "Nearby payload received from $endpointId ($receivedText.length bytes)")
                // Cek apakah ini kunci publik (panjang > 30, format Base64)
                if (receivedText.length > 30 && receivedText.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
                    // Simpan sebagai kunci publik peer
                    val ip = "nearby:$endpointId"
                    keyStore.storePeerKey(ip, receivedText)
                    DiscoveryManager.addNode(MeshNode(
                        name = "Nearby-$endpointId",
                        ipAddress = ip,
                        online = true,
                        publicKey = receivedText
                    ))
                    Log.d("GHALBIT", "Public key stored for $ip")
                } else {
                    // Bisa jadi pesan terenkripsi (didekripsi nanti)
                    // Untuk saat ini, log saja
                    Log.d("GHALBIT", "Non-key payload from $endpointId: $receivedText")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Tidak diperlukan untuk streaming kecil
        }
    }

    init {
        startAdvertising()
        startDiscovery()
    }

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()
        connectionClient.startAdvertising(
            localEndpointName,
            serviceId,
            lifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            Log.d("GHALBIT", "Nearby advertising started as $localEndpointName")
        }.addOnFailureListener { e ->
            Log.e("GHALBIT", "Nearby advertising failed", e)
        }
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()
        connectionClient.startDiscovery(
            serviceId,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    Log.d("GHALBIT", "Nearby endpoint found: ${info.endpointName}")
                    // Otomatis minta koneksi
                    connectionClient.requestConnection(
                        localEndpointName,
                        endpointId,
                        lifecycleCallback
                    )
                }

                override fun onEndpointLost(endpointId: String) {
                    Log.d("GHALBIT", "Nearby endpoint lost: $endpointId")
                }
            },
            discoveryOptions
        ).addOnSuccessListener {
            Log.d("GHALBIT", "Nearby discovery started")
        }.addOnFailureListener { e ->
            Log.e("GHALBIT", "Nearby discovery failed", e)
        }
    }

    fun stop() {
        connectionClient.stopAllEndpoints()
        Log.d("GHALBIT", "Nearby stopped")
    }
}
'@
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\nearby\NearbyManager.kt" $nearbyCode
Write-Host "NearbyManager.kt berhasil dibuat." -ForegroundColor Green

# 4. Tambahkan inisialisasi di MainActivity.kt
Write-Host "Mengintegrasikan NearbyManager ke MainActivity..." -ForegroundColor Yellow
$mainActivityPath = "app\src\main\java\com\ghalbitnet\meshx2\MainActivity.kt"
$mainContent = Get-Content $mainActivityPath -Raw

# Cek apakah NearbyManager sudah diimpor, kalau belum tambahkan
if ($mainContent -notmatch "import com.ghalbitnet.meshx2.nearby.NearbyManager") {
    $mainContent = $mainContent -replace "import com.ghalbitnet.meshx2.wifi.WifiDirectManager", "import com.ghalbitnet.meshx2.wifi.WifiDirectManager`nimport com.ghalbitnet.meshx2.nearby.NearbyManager"
}

# Tambahkan properti nearbyManager di kelas
if ($mainContent -notmatch "private var nearbyManager: NearbyManager") {
    $mainContent = $mainContent -replace "private var wifiDirect: WifiDirectManager\? = null", "private var wifiDirect: WifiDirectManager? = null`n    private var nearby: NearbyManager? = null"
}

# Tambahkan inisialisasi di startMesh() setelah WiFi Direct
if ($mainContent -notmatch "nearby = NearbyManager") {
    $insertNearbyInit = @'
        // Inisialisasi Nearby Connections
        nearby = NearbyManager(this, keyStore)
'@
    $mainContent = $mainContent -replace "(wifiDirect = WifiDirectManager\(this\))", "`$1`n$insertNearbyInit"
}

# Tambahkan pembersihan di onDestroy()
if ($mainContent -notmatch "nearby\?.stop\(\)") {
    $mainContent = $mainContent -replace "(wifiDirect\?.cleanup\(\))", "`$1`n        nearby?.stop()"
}

Write-FileWithoutBOM $mainActivityPath $mainContent
Write-Host "MainActivity.kt diperbarui." -ForegroundColor Green

# 5. Jalankan build
Write-Host "Memulai build ulang..." -ForegroundColor Cyan
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    Write-Host "Build sukses. Menginstal ke perangkat..." -ForegroundColor Cyan
    $adb = "C:\adb\platform-tools\adb.exe"
    if (Test-Path $adb) {
        & $adb install -r app\build\outputs\apk\debug\app-debug.apk
        Write-Host "Instalasi selesai. Pantau log dengan:" -ForegroundColor Green
        Write-Host "& '$adb' logcat -v time -s GHALBIT" -ForegroundColor White
    } else {
        Write-Host "ADB tidak ditemukan di $adb. Install APK secara manual dari:" -ForegroundColor Yellow
        Write-Host "app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor White
    }
} else {
    Write-Host "Build gagal. Periksa error di atas." -ForegroundColor Red
}