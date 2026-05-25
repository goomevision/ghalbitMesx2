# stability_hotfix.ps1
# Perbaikan Kritis: GlobalScope, SocketServer stop, Thread Safety, AES Key Derivation

Set-Location C:\project\Ghalbitnet\ghalbitMesx2

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Menerapkan Stability Hotfix..." -ForegroundColor Cyan

# ==============================================
# 1. PERBAIKI CryptoEngine: Derivasi Kunci AES dengan SHA-256
# ==============================================
Write-Host "1/4 Memperbaiki derivasi kunci AES..." -ForegroundColor Yellow

$cryptoEngineFix = @'
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

    // Fungsi baru untuk derivasi kunci yang aman
    private fun deriveAesKey(sharedSecret: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(sharedSecret) // Selalu 256-bit (32 byte)
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
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\security\CryptoEngine.kt" $cryptoEngineFix

# ==============================================
# 2. PERBAIKI MeshRegistry: Thread-safe dengan ConcurrentHashMap
# ==============================================
Write-Host "2/4 Mengamankan MeshRegistry..." -ForegroundColor Yellow

$meshRegistryFix = @'
package com.ghalbitnet.meshx2.routing

import com.ghalbitnet.meshx2.model.MeshNode
import java.util.concurrent.ConcurrentHashMap

object MeshRegistry {
    private val nodes = ConcurrentHashMap<String, MeshNode>()

    fun updateNode(node: MeshNode) {
        nodes[node.ipAddress] = node
    }

    fun getNodes(): List<MeshNode> = nodes.values.toList()

    fun getNode(ip: String): MeshNode? = nodes[ip]

    fun clear() = nodes.clear()
}
'@
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\routing\MeshRegistry.kt" $meshRegistryFix

# ==============================================
# 3. PERBAIKI MeshSocketServer: Stop yang Aman
# ==============================================
Write-Host "3/4 Memperbaiki MeshSocketServer..." -ForegroundColor Yellow

$meshSocketServerFix = @'
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
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("GHALBIT", "Error closing server socket", e)
        }
        serverSocket = null
    }
}
'@
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\network\MeshSocketServer.kt" $meshSocketServerFix

# ==============================================
# 4. PERBAIKI MainActivity: Hapus GlobalScope
# ==============================================
Write-Host "4/4 Menghapus GlobalScope dari MainActivity..." -ForegroundColor Yellow

$mainPath = "app\src\main\java\com\ghalbitnet\meshx2\MainActivity.kt"
$mainContent = Get-Content $mainPath -Raw

# Hapus blok GlobalScope dan ganti dengan penyimpanan langsung (tanpa scope khusus)
$oldGlobalScopeBlock = @'
kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            chatDb.chatDao().insertMessage(msg)
                        }
'@
$newSafeBlock = 'kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            chatDb.chatDao().insertMessage(msg)
                        }'

$mainContent = $mainContent -replace [regex]::Escaped($oldGlobalScopeBlock), $newSafeBlock

# Hapus import GlobalScope jika masih ada
$mainContent = $mainContent -replace "import kotlinx.coroutines.GlobalScope", ""

Write-FileWithoutBOM $mainPath $mainContent

# ==============================================
# BUILD & INSTALL
# ==============================================
Write-Host "Membangun ulang dengan perbaikan stabilitas..." -ForegroundColor Cyan
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    Write-Host "Build sukses. Menginstal..." -ForegroundColor Green
    $adb = "C:\adb\platform-tools\adb.exe"
    if (Test-Path $adb) {
        & $adb install -r app\build\outputs\apk\debug\app-debug.apk
        Write-Host "Hotfix stabilitas terpasang!" -ForegroundColor Cyan
    } else {
        Write-Host "ADB tidak ditemukan, install manual." -ForegroundColor Yellow
    }
} else {
    Write-Host "Build gagal. Periksa error di atas." -ForegroundColor Red
}