# fix_anr.ps1
Set-Location C:\project\Ghalbitnet\ghalbitMesx2

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Memperbaiki ANR / hang..." -ForegroundColor Cyan

# 1. Perbaiki RouteDiscovery – jangan pakai runBlocking, semua async
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\routing\RouteDiscovery.kt" @'
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
            scope.launch {
                db = RoutingDatabase.getInstance(context)
                initialized = true
            }
        }
    }

    fun discoverAndUpdate(destinationIp: String, knownNodes: List<MeshNode>) {
        if (!initialized) return
        scope.launch {
            val nextHop = QosManager.selectBestNeighbor(destinationIp, knownNodes)
            if (nextHop != null) {
                val entry = RoutingTableEntry(
                    destinationIp = destinationIp,
                    nextHopIp = nextHop.ipAddress,
                    hopCount = 1,
                    latencyMs = nextHop.latency.toLong(),
                    trustScore = nextHop.trusted
                )
                db?.routingDao()?.insertEntry(entry)
                Log.d("GHALBIT", "Route discovered: $destinationIp via ${nextHop.ipAddress}")
            }
        }
    }

    suspend fun getBestRoute(destIp: String): RoutingTableEntry? {
        return try {
            withTimeout(1000L) {
                db?.routingDao()?.getRoutes(destIp)?.firstOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }
}
'@

# 2. Perbaiki RelayEngine – gunakan runBlocking dengan timeout pendek, atau coroutine
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\routing\RelayEngine.kt" @'
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

        // Cari rute di background, lalu kirim
        CoroutineScope(Dispatchers.IO).launch {
            val route = RouteDiscovery.getBestRoute(packet.destination)
            if (route != null) {
                try {
                    MeshSocketClient.send(route.nextHopIp, next)
                    Log.d("GHALBIT", "Relayed packet to next-hop ${route.nextHopIp}")
                } catch (_: Exception) {}
            } else {
                // Fallback flood
                MeshRegistry.getNodes().filter { it.online }.forEach { node ->
                    try { MeshSocketClient.send(node.ipAddress, next) } catch (_: Exception) {}
                }
            }
        }
        Log.d("GHALBIT", "Relayed packet: ${packet.type}")
    }

    fun relaySecurePacket(secure: SecurePacket, keyStore: KeyStoreManager) {
        if (packetCache.contains(secure.packetId) || secure.hopCount >= secure.maxHop) return
        packetCache.add(secure.packetId)
        val next = secure.copy(hopCount = secure.hopCount + 1)
        MeshRegistry.getNodes().filter { it.online }.forEach { node ->
            MeshSocketClient.sendSecure(node.ipAddress, next)
        }
        Log.d("GHALBIT", "Relayed secure packet")
    }
}
'@

# 3. Perbaiki MainActivity – jalankan startMesh di thread background
Write-Host "Mengamankan MainActivity..." -ForegroundColor Yellow
$mainPath = "app\src\main\java\com\ghalbitnet\meshx2\MainActivity.kt"
$mainContent = Get-Content $mainPath -Raw

# Ganti pemanggilan startMesh() agar dijalankan di coroutine
$oldStartMesh = 'private fun startMesh()'
$newStartMesh = @'
private fun startMesh() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            startMeshInternal()
        }
    }

    private suspend fun startMeshInternal() {
'@

$mainContent = $mainContent -replace [regex]::Escape($oldStartMesh), $newStartMesh

# Pastikan import CoroutineScope ada
if ($mainContent -notmatch "import kotlinx.coroutines.CoroutineScope") {
    $mainContent = $mainContent -replace "import kotlinx.coroutines.launch", "import kotlinx.coroutines.CoroutineScope`nimport kotlinx.coroutines.Dispatchers`nimport kotlinx.coroutines.launch"
}

Write-FileWithoutBOM $mainPath $mainContent

# 4. Build & Install
Write-Host "Build..." -ForegroundColor Cyan
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    & "C:\adb\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
    Write-Host "Perbaikan ANR terpasang." -ForegroundColor Green
} else {
    Write-Host "Build gagal." -ForegroundColor Red
}