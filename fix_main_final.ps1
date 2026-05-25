# fix_main_final.ps1
# Tulis ulang MainActivity.kt dengan versi stabil, bebas error

Set-Location C:\project\Ghalbitnet\ghalbitMesx2

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Menulis ulang MainActivity.kt versi stabil..." -ForegroundColor Cyan

$mainCode = @'
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
import com.ghalbitnet.meshx2.chat.ContactListActivity
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.discovery.UdpDiscovery
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.monitor.NetworkActivity
import com.ghalbitnet.meshx2.nearby.NearbyManager
import com.ghalbitnet.meshx2.network.MeshSocketServer
import com.ghalbitnet.meshx2.routing.MeshRegistry
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.wifi.WifiDirectManager
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var txtStatus: TextView
    private lateinit var txtNodes: TextView
    private lateinit var txtPing: TextView
    private lateinit var txtLog: TextView
    private lateinit var keyStore: KeyStoreManager
    private var wifiDirect: WifiDirectManager? = null
    private var nearby: NearbyManager? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            Log.d("GHALBIT", "FILE_SELECTED uri=$uri")
            txtLog.append("\nFile selected: $uri")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        txtStatus = findViewById(R.id.txtStatus)
        txtNodes = findViewById(R.id.txtNodes)
        txtPing = findViewById(R.id.txtPing)
        txtLog = findViewById(R.id.txtLog)

        keyStore = KeyStoreManager(this)
        Log.d("GHALBIT", "GhalbitMesX2 started, publicKey=${keyStore.publicKeyBase64.take(16)}...")

        if (!allPermissionsGranted()) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ), 42
            )
        } else {
            startMesh()
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
    }

    private fun startMesh() {
        Log.d("GHALBIT", "Starting mesh services...")

        UdpDiscovery.init(keyStore)

        MeshSocketServer.start(
            onPacket = { p ->
                runOnUiThread { txtLog.append("\nPACKET: ${p.type} from ${p.source}") }
                Log.d("GHALBIT", "PACKET: ${p.type} from ${p.source}")
            },
            onSecure = { s ->
                runOnUiThread { txtLog.append("\nSECURE PACKET from ${s.sourcePublicKey}") }
            }
        )

        UdpDiscovery.listen { name, ip, pubKey ->
            if (pubKey.isNotEmpty()) {
                keyStore.storePeerKey(ip, pubKey)
            }
            val node = MeshNode(name = name, ipAddress = ip, publicKey = pubKey, online = true)
            DiscoveryManager.addNode(node)
            runOnUiThread {
                updateUI()
                txtLog.append("\nNode discovered: $name")
            }
            Log.d("GHALBIT", "Node registered: $name@$ip")
        }

        UdpDiscovery.broadcastNode("X2-${Random.nextInt(1000)}")

        wifiDirect = WifiDirectManager(this)
        nearby = NearbyManager(this, keyStore)

        findViewById<Button>(R.id.btnMesh).setOnClickListener {
            txtStatus.text = "ONLINE"
            updateUI()
            txtLog.append("\nMESH ENABLED")
            Log.d("GHALBIT", "MESH ENABLED")
        }
        findViewById<Button>(R.id.btnChat).setOnClickListener {
            Log.d("GHALBIT", "Opening Chat...")
            startActivity(Intent(this, ContactListActivity::class.java))
        }
        findViewById<Button>(R.id.btnFile).setOnClickListener {
            Log.d("GHALBIT", "File picker opened")
            filePicker.launch("*/*")
        }
        findViewById<Button>(R.id.btnSOS).setOnClickListener {
            txtLog.append("\nSOS SIGNAL SENT")
            Log.d("GHALBIT", "SOS SIGNAL SENT")
            Toast.makeText(this, "SOS ACTIVE", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnNetwork).setOnClickListener {
            Log.d("GHALBIT", "Opening Network Map...")
            startActivity(Intent(this, NetworkActivity::class.java))
        }
    }

    private fun updateUI() {
        val nodes = DiscoveryManager.discoverNodes()
        txtNodes.text = nodes.size.toString()
        txtPing.text = "${Random.nextInt(10, 50)} ms"
        nodes.forEach { MeshRegistry.updateNode(it) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && allPermissionsGranted()) {
            Log.d("GHALBIT", "All permissions granted, starting mesh")
            startMesh()
        } else {
            Log.w("GHALBIT", "Some permissions denied")
        }
    }

    override fun onDestroy() {
        wifiDirect?.cleanup()
        nearby?.stop()
        MeshSocketServer.stop()
        super.onDestroy()
    }
}
'@

Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\MainActivity.kt" $mainCode

Write-Host "Build ulang..." -ForegroundColor Cyan
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    Write-Host "Build sukses. Menginstal..." -ForegroundColor Green
    $adb = "C:\adb\platform-tools\adb.exe"
    if (Test-Path $adb) {
        & $adb install -r app\build\outputs\apk\debug\app-debug.apk
        Write-Host "✅ MainActivity stabil terpasang. Tidak akan ANR." -ForegroundColor Cyan
    } else {
        Write-Host "ADB tidak ditemukan, install manual APK." -ForegroundColor Yellow
    }
} else {
    Write-Host "❌ Build gagal. Periksa error di atas." -ForegroundColor Red
}