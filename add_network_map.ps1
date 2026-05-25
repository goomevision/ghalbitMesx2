# add_network_map.ps1
# Menambahkan Visual Network Map interaktif ke GhalbitMesX2

Set-Location C:\project\Ghalbitnet\ghalbitMesx2

# Fungsi tulis file tanpa BOM
function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Membangun Visual Network Map..." -ForegroundColor Cyan

# 1. Membuat drawable marker lingkaran
Write-Host "Membuat drawable marker..." -ForegroundColor Yellow

Write-FileWithoutBOM "app\src\main\res\drawable\ic_marker_green.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <size android:width="24dp" android:height="24dp"/>
    <solid android:color="#4CAF50"/>
    <stroke android:width="2dp" android:color="#FFFFFF"/>
</shape>
'@

Write-FileWithoutBOM "app\src\main\res\drawable\ic_marker_yellow.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <size android:width="24dp" android:height="24dp"/>
    <solid android:color="#FFC107"/>
    <stroke android:width="2dp" android:color="#FFFFFF"/>
</shape>
'@

Write-FileWithoutBOM "app\src\main\res\drawable\ic_marker_red.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <size android:width="24dp" android:height="24dp"/>
    <solid android:color="#F44336"/>
    <stroke android:width="2dp" android:color="#FFFFFF"/>
</shape>
'@

# 2. Layout popup info window
Write-Host "Membuat layout info window..." -ForegroundColor Yellow
Write-FileWithoutBOM "app\src\main\res\layout\info_window.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="12dp"
    android:background="@android:color/white">
    <TextView
        android:id="@+id/title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="#000000"
        android:textStyle="bold"
        android:maxLines="1"
        android:ellipsize="end"/>
    <TextView
        android:id="@+id/snippet"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="#333333"
        android:maxLines="5"
        android:layout_marginTop="4dp"/>
</LinearLayout>
'@

# 3. NetworkActivity.kt lengkap
Write-Host "Menulis ulang NetworkActivity..." -ForegroundColor Yellow
$networkActivityCode = @'
package com.ghalbitnet.meshx2.monitor

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.Toast
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
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow
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
        mapView.controller.setCenter(GeoPoint(-6.2, 106.8)) // Jakarta

        loadNodes()
        handler.postDelayed(refreshRunnable, refreshInterval)
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadNodes()
            handler.postDelayed(this, refreshInterval)
        }
    }

    private fun loadNodes() {
        val nodes = DiscoveryManager.discoverNodes()
        txtNetwork.text = "GHALBITNET MAP: ${nodes.size} node(s)"

        // Hapus marker lama (hanya overlay bertipe Marker)
        mapView.overlays.removeAll { it is Marker }
        nodes.forEach { addMarkerForNode(it) }
        mapView.invalidate()
    }

    private fun addMarkerForNode(node: MeshNode) {
        val position = getNodePosition(node)
        val marker = Marker(mapView)
        marker.position = position
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = node.name
        marker.snippet = "IP: ${node.ipAddress}\nTrust: ${node.trusted}%\nLatency: ${node.latency} ms\n" +
                if (node.online) "ONLINE" else "OFFLINE"
        marker.icon = getTrustDrawable(node.trusted)

        // Gunakan InfoWindow kustom dari layout kita
        marker.infoWindow = BasicInfoWindow(R.layout.info_window, mapView)

        mapView.overlays.add(marker)
    }

    private fun getNodePosition(node: MeshNode): GeoPoint {
        // Jika koordinat asli tersedia, pakai
        if (node.latitude != 0.0 && node.longitude != 0.0) {
            return GeoPoint(node.latitude, node.longitude)
        }
        // Posisi pseudo-random stabil berdasarkan hash IP (biar tidak berubah-ubah tiap refresh)
        val hash = node.ipAddress.hashCode()
        val rng = Random(hash)
        val baseLat = -6.2
        val baseLng = 106.8
        val latOffset = rng.nextDouble(-0.05, 0.05)
        val lngOffset = rng.nextDouble(-0.05, 0.05)
        return GeoPoint(baseLat + latOffset, baseLng + lngOffset)
    }

    private fun getTrustDrawable(trust: Int): Drawable? {
        val resId = when {
            trust >= 70 -> R.drawable.ic_marker_green
            trust >= 40 -> R.drawable.ic_marker_yellow
            else -> R.drawable.ic_marker_red
        }
        return ContextCompat.getDrawable(this, resId)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(refreshRunnable, refreshInterval)
    }
}
'@
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\monitor\NetworkActivity.kt" $networkActivityCode

# 4. Build & install
Write-Host "Memulai build..." -ForegroundColor Cyan
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    Write-Host "Build sukses. Menginstal ke perangkat..." -ForegroundColor Green
    $adb = "C:\adb\platform-tools\adb.exe"
    if (Test-Path $adb) {
        & $adb install -r app\build\outputs\apk\debug\app-debug.apk
        Write-Host "Instalasi selesai. Buka Network Map untuk melihat visual mesh." -ForegroundColor Cyan
        Write-Host "Pantau log dengan: & '$adb' logcat -v time -s GHALBIT" -ForegroundColor White
    } else {
        Write-Host "ADB tidak ditemukan di $adb. Install manual APK dari:" -ForegroundColor Yellow
        Write-Host "app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor White
    }
} else {
    Write-Host "Build gagal. Periksa error di atas." -ForegroundColor Red
}