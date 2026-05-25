# add_token_ledger.ps1
# Menambahkan sistem token ledger ke GhalbitMesX2

Set-Location C:\project\Ghalbitnet\ghalbitMesx2

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Menambahkan Token Ledger System..." -ForegroundColor Cyan

# ================================================
# 1. Perbarui app/build.gradle (tambah Room + coroutine)
# ================================================
Write-Host "Memperbarui dependensi..." -ForegroundColor Yellow

$appGradle = Get-Content "app\build.gradle" -Raw

# Tambahkan dependensi Room jika belum ada
if ($appGradle -notmatch "androidx.room") {
    $roomDeps = @'
    // Room for token ledger
    implementation 'androidx.room:room-runtime:2.6.1'
    annotationProcessor 'androidx.room:room-compiler:2.6.1'
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
'@
    $appGradle = $appGradle -replace "(dependencies\s*\{)", "`$1`n$roomDeps"
}

# Pastikan apply plugin: 'kotlin-kapt' jika belum ada (untuk Room)
if ($appGradle -notmatch "kotlin-kapt") {
    $appGradle = $appGradle -replace "(id 'kotlin-android')", "`$1`n    id 'kotlin-kapt'"
}

Write-FileWithoutBOM "app\build.gradle" $appGradle

# ================================================
# 2. Buat package token & file Room
# ================================================
Write-Host "Membuat database Room..." -ForegroundColor Yellow

# TokenTransaction entity
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\token\TokenTransaction.kt" @'
package com.ghalbitnet.meshx2.token

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "token_transactions")
data class TokenTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerIp: String,
    val peerName: String,
    val amount: Double,
    val reason: String, // "RELAY_REWARD", "TRANSFER", "INITIAL"
    val timestamp: Long = System.currentTimeMillis()
)
'@

# TokenDao
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\token\TokenDao.kt" @'
package com.ghalbitnet.meshx2.token

import androidx.room.*

@Dao
interface TokenDao {
    @Query("SELECT SUM(amount) FROM token_transactions WHERE peerIp = :peerIp")
    fun getBalance(peerIp: String): Double?

    @Query("SELECT * FROM token_transactions ORDER BY timestamp DESC LIMIT 20")
    fun getRecentTransactions(): List<TokenTransaction>

    @Insert
    fun insertTransaction(transaction: TokenTransaction)
}
'@

# TokenDatabase
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\token\TokenDatabase.kt" @'
package com.ghalbitnet.meshx2.token

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TokenTransaction::class], version = 1, exportSchema = false)
abstract class TokenDatabase : RoomDatabase() {
    abstract fun tokenDao(): TokenDao

    companion object {
        @Volatile
        private var INSTANCE: TokenDatabase? = null

        fun getInstance(context: Context): TokenDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TokenDatabase::class.java,
                    "ghalbit_ledger"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
'@

# ================================================
# 3. TokenManager singleton
# ================================================
Write-Host "Membuat TokenManager..." -ForegroundColor Yellow

Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\token\TokenManager.kt" @'
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
        if (!initialized) {
            db = TokenDatabase.getInstance(context)
            initialized = true
        }
    }

    fun recordReward(peerIp: String, peerName: String, amount: Double, reason: String = "RELAY_REWARD") {
        if (!initialized) return
        scope.launch {
            db.tokenDao().insertTransaction(
                TokenTransaction(peerIp = peerIp, peerName = peerName, amount = amount, reason = reason)
            )
            Log.d("GHALBIT", "Token reward $amount to $peerName ($peerIp)")
            // Perbarui saldo di MeshRegistry supaya UI ikut update
            MeshRegistry.getNode(peerIp)?.let { node ->
                val newBalance = node.balance + amount
                MeshRegistry.updateNode(node.copy(balance = newBalance))
            }
        }
    }

    fun transfer(fromIp: String, toIp: String, amount: Double) {
        // Akan diimplementasi nanti dengan validasi saldo
    }

    fun getBalance(peerIp: String): Double {
        if (!initialized) return 0.0
        var balance = 0.0
        runBlocking {
            balance = db.tokenDao().getBalance(peerIp) ?: 0.0
        }
        return balance
    }
}
'@

# ================================================
# 4. Integrasi dengan ReputationManager
# ================================================
Write-Host "Mengintegrasikan TokenManager ke ReputationManager..." -ForegroundColor Yellow

$repPath = "app\src\main\java\com\ghalbitnet\meshx2\reputation\ReputationManager.kt"
$repContent = Get-Content $repPath -Raw

# Pastikan import TokenManager ada
if ($repContent -notmatch "import com.ghalbitnet.meshx2.token.TokenManager") {
    $repContent = $repContent -replace "import com.ghalbitnet.meshx2.routing.MeshRegistry", "import com.ghalbitnet.meshx2.routing.MeshRegistry`nimport com.ghalbitnet.meshx2.token.TokenManager"
}

# Di dalam updateReputation, setelah mengubah balance, panggil TokenManager.recordReward
$oldUpdate = @'
if (relaySuccess) newTrust = minOf(newTrust + 2, 100) else newTrust = maxOf(newTrust - 5, 0)
        if (latencyMs > 100) newTrust = maxOf(newTrust - ((latencyMs - 100) * 0.1).toInt(), 0)
        val newBalance = if (relaySuccess) node.balance + 0.01 else node.balance
        MeshRegistry.updateNode(node.copy(trusted = newTrust, balance = newBalance))
'@

$newUpdate = @'
if (relaySuccess) newTrust = minOf(newTrust + 2, 100) else newTrust = maxOf(newTrust - 5, 0)
        if (latencyMs > 100) newTrust = maxOf(newTrust - ((latencyMs - 100) * 0.1).toInt(), 0)
        val newBalance = if (relaySuccess) node.balance + 0.01 else node.balance
        MeshRegistry.updateNode(node.copy(trusted = newTrust, balance = newBalance))
        if (relaySuccess) {
            TokenManager.recordReward(ip, node.name, 0.01, "RELAY_REWARD")
        }
'@

$repContent = $repContent -replace [regex]::Escape($oldUpdate), $newUpdate

Write-FileWithoutBOM $repPath $repContent

# ================================================
# 5. Inisialisasi TokenManager di MainActivity
# ================================================
Write-Host "Memperbarui MainActivity..." -ForegroundColor Yellow

$mainPath = "app\src\main\java\com\ghalbitnet\meshx2\MainActivity.kt"
$mainContent = Get-Content $mainPath -Raw

# Tambahkan import TokenManager
if ($mainContent -notmatch "import com.ghalbitnet.meshx2.token.TokenManager") {
    $mainContent = $mainContent -replace "import com.ghalbitnet.meshx2.security.KeyStoreManager", "import com.ghalbitnet.meshx2.security.KeyStoreManager`nimport com.ghalbitnet.meshx2.token.TokenManager"
}

# Inisialisasi TokenManager di awal startMesh()
$initLine = 'TokenManager.init(this)'
if ($mainContent -notmatch [regex]::Escape($initLine)) {
    $mainContent = $mainContent -replace "(UdpDiscovery.init\(keyStore\))", "`$1`n        $initLine"
}

Write-FileWithoutBOM $mainPath $mainContent

# ================================================
# 6. Tampilkan saldo di info window peta
# ================================================
Write-Host "Memperbarui NetworkActivity untuk saldo..." -ForegroundColor Yellow

$netPath = "app\src\main\java\com\ghalbitnet\meshx2\monitor\NetworkActivity.kt"
$netContent = Get-Content $netPath -Raw

# Ubah snippet agar menyertakan saldo
$oldSnippet = 'marker.snippet = "IP: ${node.ipAddress}\nTrust: ${node.trusted}%\nLatency: ${node.latency} ms\n" +'
$newSnippet = 'marker.snippet = "IP: ${node.ipAddress}\nTrust: ${node.trusted}%\nBalance: ${node.balance} GHBT\nLatency: ${node.latency} ms\n" +'

$netContent = $netContent -replace [regex]::Escape($oldSnippet), $newSnippet

Write-FileWithoutBOM $netPath $netContent

# ================================================
# 7. Build & Install
# ================================================
Write-Host "Memulai build..." -ForegroundColor Cyan
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    Write-Host "Build sukses. Menginstal ke perangkat..." -ForegroundColor Green
    $adb = "C:\adb\platform-tools\adb.exe"
    if (Test-Path $adb) {
        & $adb install -r app\build\outputs\apk\debug\app-debug.apk
        Write-Host "Instalasi selesai. Saldo token akan muncul di log dan UI." -ForegroundColor Cyan
        Write-Host "Pantau log: & '$adb' logcat -v time -s GHALBIT" -ForegroundColor White
    } else {
        Write-Host "ADB tidak ditemukan di $adb. Install manual APK dari:" -ForegroundColor Yellow
        Write-Host "app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor White
    }
} else {
    Write-Host "Build gagal. Periksa error di atas." -ForegroundColor Red
}