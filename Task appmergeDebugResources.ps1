# fix_voip.ps1
Set-Location C:\project\Ghalbitnet\ghalbitMesx2

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Memperbaiki VoIP agar tidak macet..." -ForegroundColor Cyan

# Tulis ulang VoipEngine.kt dengan penanganan yang benar
$voipCode = @'
package com.ghalbitnet.meshx2.chat

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object VoipEngine {
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    private var udpSocket: DatagramSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startCall(targetIp: String, talkPort: Int = 56567, listenPort: Int = 56568) {
        if (isRunning) {
            Log.w("GHALBIT", "VoIP already running")
            return
        }
        isRunning = true

        // Start listener (receive audio)
        scope.launch {
            try {
                startReceiver(listenPort)
            } catch (e: Exception) {
                Log.e("GHALBIT", "VoIP receiver error", e)
            }
        }

        // Start sender (record and send)
        scope.launch {
            try {
                startSender(targetIp, talkPort)
            } catch (e: Exception) {
                Log.e("GHALBIT", "VoIP sender error", e)
            }
        }

        Log.d("GHALBIT", "VoIP call started")
    }

    private suspend fun startSender(targetIp: String, port: Int) {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e("GHALBIT", "Invalid audio buffer size")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("GHALBIT", "AudioRecord init failed")
            return
        }

        audioRecord?.startRecording()
        udpSocket = DatagramSocket()

        val buffer = ByteArray(minBufferSize)
        val address = InetAddress.getByName(targetIp)

        withContext(Dispatchers.IO) {
            while (isRunning) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    try {
                        val packet = DatagramPacket(buffer, read, address) 
                        packet.port = port
                        udpSocket?.send(packet)
                    } catch (e: Exception) {
                        Log.e("GHALBIT", "VoIP send failed", e)
                    }
                }
            }
        }
    }

    private suspend fun startReceiver(port: Int) {
        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
            Log.e("GHALBIT", "Invalid audio buffer size for track")
            return
        }

        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AUDIO_FORMAT)
                .setChannelMask(CHANNEL_CONFIG)
                .build(),
            minBufferSize * 2,
            AudioTrack.MODE_STREAM,
            android.os.Process.myUid()
        )

        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            Log.e("GHALBIT", "AudioTrack init failed")
            return
        }

        audioTrack?.play()

        val socket = DatagramSocket(port)
        val buffer = ByteArray(minBufferSize)

        withContext(Dispatchers.IO) {
            while (isRunning) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    audioTrack?.write(packet.data, 0, packet.length)
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e("GHALBIT", "VoIP receive error", e)
                    }
                }
            }
        }
    }

    fun stopCall() {
        isRunning = false
        scope.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (_: Exception) {}
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (_: Exception) {}
        try { udpSocket?.close() } catch (_: Exception) {}
        Log.d("GHALBIT", "VoIP call stopped")
    }
}
'@
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\chat\VoipEngine.kt" $voipCode

# Perbarui ChatActivity untuk menggunakan startCall/stopCall dan meminta izin
$chatPath = "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatActivity.kt"
$chatContent = Get-Content $chatPath -Raw

# Tambahkan izin RECORD_AUDIO sebelum memulai panggilan
$oldCall = 'if (!isCallActive) {
            VoipEngine.startTalk(node.ipAddress)
            VoipEngine.startListen()
            btnCall.text = "Stop"
            isCallActive = true
            Toast.makeText(this, "Telepon dimulai", Toast.LENGTH_SHORT).show()
        } else {
            VoipEngine.stop()
            btnCall.text = "Tel"
            isCallActive = false
            Toast.makeText(this, "Telepon diakhiri", Toast.LENGTH_SHORT).show()
        }'

$newCall = 'if (!isCallActive) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 202)
                return
            }
            VoipEngine.startCall(node.ipAddress)
            btnCall.text = "Stop"
            isCallActive = true
            Toast.makeText(this, "Telepon dimulai", Toast.LENGTH_SHORT).show()
        } else {
            VoipEngine.stopCall()
            btnCall.text = "Tel"
            isCallActive = false
            Toast.makeText(this, "Telepon diakhiri", Toast.LENGTH_SHORT).show()
        }'

$chatContent = $chatContent -replace [regex]::Escape($oldCall), $newCall

# Perbarui onRequestPermissionsResult untuk kode 202
$oldPerm = 'if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(cameraUri)
        } else if (requestCode == 201 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toggleRecording()
        }'

$newPerm = 'if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(cameraUri)
        } else if (requestCode == 201 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toggleRecording()
        } else if (requestCode == 202 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoipCall()
        }'

$chatContent = $chatContent -replace [regex]::Escape($oldPerm), $newPerm

# Ganti VoipEngine.stop() lama dengan VoipEngine.stopCall() di onDestroy
$chatContent = $chatContent -replace 'VoipEngine.stop\(\)', 'VoipEngine.stopCall()'

Write-FileWithoutBOM $chatPath $chatContent

# Build & install
.\gradlew assembleDebug
if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    & "C:\adb\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
    Write-Host "VoIP diperbaiki. Uji panggilan dengan 2 perangkat." -ForegroundColor Green
} else {
    Write-Host "Build gagal, periksa error." -ForegroundColor Red
}