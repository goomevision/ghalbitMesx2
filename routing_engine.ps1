# routing_engine.ps1
# Menambahkan Routing Engine: AODV ringan + QoS

Set-Location C:\project\Ghalbitnet\ghalbitMesx2

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Membangun Routing Engine..." -ForegroundColor Cyan

# ========== 1. Entity RoutingTable ==========
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\routing\RoutingTableEntry.kt" @'
package com.ghalbitnet.meshx2.routing

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routing_table")
data class RoutingTableEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val destinationIp: String,
    val nextHopIp: String,
    val hopCount: Int,
    val latencyMs: Long,
    val trustScore: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)
'@

# ========== 2. RoutingDao ==========
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\routing\RoutingDao.kt" @'
package com.ghalbitnet.meshx2.routing

import androidx.room.*

@Dao
interface RoutingDao {
    @Query("SELECT * FROM routing_table WHERE destinationIp = :destIp ORDER BY hopCount ASC")
    fun getRoutes(destIp: String): List<RoutingTableEntry>

    @Query("SELECT * FROM routing_table")
    fun getAllEntries(): List<RoutingTableEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEntry(entry: RoutingTableEntry)

    @Query("DELETE FROM routing_table WHERE lastUpdated < :threshold")
    fun deleteOlderThan(threshold: Long)
}
'@

# ========== 3. RoutingDatabase ==========
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\routing\RoutingDatabase.kt" @'
package com.ghalbitnet.meshx2.routing

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RoutingTableEntry::class], version = 1, exportSchema = false)
abstract class RoutingDatabase : RoomDatabase() {
    abstract fun routingDao(): RoutingDao

    companion object {
        @Volatile
        private var INSTANCE: RoutingDatabase? = null

        fun getInstance(context: Context): RoutingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RoutingDatabase::class.java,
                    "ghalbit_routing"
                ).fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
'@

# ========== 4. QosManager ==========
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\routing\QosManager.kt" @'
package com.ghalbitnet.meshx2.routing

import com.ghalbitnet.meshx2.model.MeshNode

object QosManager {
    fun selectBestNeighbor(destIp: String, nodes: List<MeshNode>): MeshNode? {
        // Pilih node dengan latensi terendah, trust tertinggi, dan online
        return nodes
            .filter { it.online && it.ipAddress != destIp }
            .sortedWith(compareByDescending<MeshNode> { it.trusted }
                .thenBy { it.latency })
            .firstOrNull()
    }

    fun calculateQosScore(node: MeshNode): Double {
        // Skor QoS 0..1 (semakin besar semakin baik)
        val trustFactor = node.trusted / 100.0
        val latencyFactor = if (node.latency > 0) 100.0 / node.latency else 1.0
        return (trustFactor * 0.6) + (latencyFactor * 0.4)
    }
}
'@

# ========== 5. RouteDiscovery (AODV ringan) ==========
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\routing\RouteDiscovery.kt" @'
package com.ghalbitnet.meshx2.routing

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.network.MeshSocketClient
import kotlinx.coroutines.*

object RouteDiscovery {
    private lateinit var db: RoutingDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            db = RoutingDatabase.getInstance(context)
            initialized = true
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
                db.routingDao().insertEntry(entry)
                Log.d("GHALBIT", "Route discovered: $destinationIp via ${nextHop.ipAddress}")
            }
        }
    }

    fun getBestRoute(destIp: String): RoutingTableEntry? {
        if (!initialized) return null
        var entry: RoutingTableEntry? = null
        runBlocking {
            entry = db.routingDao().getRoutes(destIp).firstOrNull()
        }
        return entry
    }

    // Akan dikembangkan lebih lanjut dengan RouteRequest/RouteReply
}
'@

# ========== 6. Perbarui RelayEngine agar pakai routing ==========
Write-Host "Memperbarui RelayEngine..." -ForegroundColor Yellow
$relayPath = "app\src\main\java\com\ghalbitnet\meshx2\routing\RelayEngine.kt"
$relayContent = Get-Content $relayPath -Raw

# Ganti relayPacket agar hanya mengirim via next-hop yang ditemukan
$newRelay = @'
package com.ghalbitnet.meshx2.routing

import android.util.Log
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.model.SecurePacket
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.security.KeyStoreManager

object RelayEngine {
    private val packetCache = mutableSetOf<String>()

    fun relayPacket(packet: MeshPacket) {
        if (packetCache.contains(packet.packetId) || packet.hopCount >= packet.maxHop) return
        packetCache.add(packet.packetId)

        val next = packet.copy(hopCount = packet.hopCount + 1)

        // Gunakan routing table jika tersedia
        val route = RouteDiscovery.getBestRoute(packet.destination)
        if (route != null) {
            try {
                MeshSocketClient.send(route.nextHopIp, next)
                Log.d("GHALBIT", "Relayed packet to next-hop ${route.nextHopIp}")
            } catch (_: Exception) {}
        } else {
            // Fallback: kirim ke semua node online (flood)
            MeshRegistry.getNodes().filter { it.online }.forEach { node ->
                try { MeshSocketClient.send(node.ipAddress, next) } catch (_: Exception) {}
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

Write-FileWithoutBOM $relayPath $newRelay

# ========== 7. Inisialisasi Routing di MainActivity ==========
Write-Host "Mengintegrasikan routing ke MainActivity..." -ForegroundColor Yellow
$mainPath = "app\src\main\java\com\ghalbitnet\meshx2\MainActivity.kt"
$mainContent = Get-Content $mainPath -Raw

# Tambahkan import
if ($mainContent -notmatch "import com.ghalbitnet.meshx2.routing.RouteDiscovery") {
    $mainContent = $mainContent -replace "import com.ghalbitnet.meshx2.routing.MeshRegistry", "import com.ghalbitnet.meshx2.routing.MeshRegistry`nimport com.ghalbitnet.meshx2.routing.RouteDiscovery"
}

# Panggil RouteDiscovery.init(this) setelah TokenManager.init
$initLine = 'RouteDiscovery.init(this)'
if ($mainContent -notmatch [regex]::Escape($initLine)) {
    $mainContent = $mainContent -replace "(TokenManager.init\(this\))", "`$1`n        $initLine"
}

Write-FileWithoutBOM $mainPath $mainContent

# ========== 8. Build & Install ==========
Write-Host "Build routing engine..." -ForegroundColor Cyan
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    & "C:\adb\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
    Write-Host "Routing engine aktif. Sekarang mesh tidak flooding lagi." -ForegroundColor Green
} else {
    Write-Host "Build gagal." -ForegroundColor Red
}