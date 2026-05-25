# fix_chat_crash.ps1
Set-Location C:\project\Ghalbitnet\ghalbitMesx2

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Perbaiki crash ChatActivity..." -ForegroundColor Cyan

# 1. Tambahkan FileProvider di AndroidManifest.xml
$manifest = Get-Content "app\src\main\AndroidManifest.xml" -Raw
if ($manifest -notmatch "<provider") {
    $providerBlock = @'

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
'@
    $manifest = $manifest -replace "(</application>)", "$providerBlock`n    `$1"
    Write-FileWithoutBOM "app\src\main\AndroidManifest.xml" $manifest
}

# 2. Buat file_paths.xml
$resDir = "app\src\main\res\xml"
if (-not (Test-Path $resDir)) { New-Item -ItemType Directory -Path $resDir -Force }
Write-FileWithoutBOM "app\src\main\res\xml\file_paths.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-files-path name="external_files" path="." />
    <cache-path name="cache" path="." />
</paths>
'@

# 3. Perbaiki ChatActivity.kt (versi aman)
$safeChatActivity = @'
package com.ghalbitnet.meshx2.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.network.FileTransferManager
import com.ghalbitnet.meshx2.security.KeyStoreManager
import kotlinx.coroutines.*
import java.io.File

class ChatActivity : AppCompatActivity() {
    private lateinit var recyclerChat: RecyclerView
    private lateinit var edtMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnAttach: Button
    private lateinit var btnRecord: Button
    private lateinit var btnCall: Button
    private lateinit var tvChatTitle: TextView
    private lateinit var adapter: ChatAdapter
    private lateinit var chatDb: ChatDatabase
    private lateinit var keyStore: KeyStoreManager
    private var peerNode: MeshNode? = null
    private val chatId: String by lazy {
        val peerIp = peerNode?.ipAddress ?: "unknown"
        val myName = "Me"
        val peerName = peerNode?.name ?: peerIp
        if (myName < peerName) "$myName:$peerIp" else "$peerIp:$myName"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var audioRecorder: AudioRecorder? = null
    private var isRecording = false
    private var isCallActive = false

    // Inisialisasi aman
    private val cameraFile by lazy {
        File(getExternalFilesDir(null), "photo_${System.currentTimeMillis()}.jpg")
    }
    private val cameraUri by lazy {
        FileProvider.getUriForFile(this, "$packageName.fileprovider", cameraFile)
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { sendFile(it) }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) sendFile(Uri.fromFile(cameraFile))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        recyclerChat = findViewById(R.id.recyclerChat)
        edtMessage = findViewById(R.id.edtMessage)
        btnSend = findViewById(R.id.btnSend)
        btnAttach = findViewById(R.id.btnAttach)
        btnRecord = findViewById(R.id.btnRecord)
        btnCall = findViewById(R.id.btnCall)
        tvChatTitle = findViewById(R.id.tvChatTitle)

        keyStore = KeyStoreManager(this)
        chatDb = ChatDatabase.getInstance(this)

        peerNode = DiscoveryManager.discoverNodes().firstOrNull { it.online }
        if (peerNode == null) {
            tvChatTitle.text = "Chat (tidak ada node online)"
            btnSend.isEnabled = false
        } else {
            tvChatTitle.text = "Chat dengan ${peerNode!!.name}"
        }

        adapter = ChatAdapter(emptyList())
        recyclerChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recyclerChat.adapter = adapter
        loadMessages()

        btnSend.setOnClickListener {
            val text = edtMessage.text.toString().trim()
            if (text.isNotEmpty() && peerNode != null) {
                sendMessage(text)
                edtMessage.text.clear()
            }
        }

        btnAttach.setOnClickListener { showAttachmentOptions() }
        btnRecord.setOnClickListener { toggleRecording() }
        btnCall.setOnClickListener { startVoipCall() }
    }

    private fun showAttachmentOptions() {
        val items = arrayOf("Galeri", "Kamera")
        android.app.AlertDialog.Builder(this)
            .setTitle("Lampirkan")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> imagePicker.launch("image/* video/* audio/*")
                    1 -> {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            cameraLauncher.launch(cameraUri)
                        } else {
                            requestPermissions(arrayOf(Manifest.permission.CAMERA), 200)
                        }
                    }
                }
            }
            .show()
    }

    private fun sendFile(uri: Uri) {
        val node = peerNode ?: return
        FileTransferManager.sendFile(this, uri, node.ipAddress, node.name, keyStore)
        Toast.makeText(this, "Mengirim file...", Toast.LENGTH_SHORT).show()
        loadMessages()
    }

    private fun toggleRecording() {
        if (isRecording) {
            audioRecorder?.stop()
            audioRecorder = null
            btnRecord.text = "Rekam"
            isRecording = false
            // Kirim file rekaman
            val dir = getExternalFilesDir(null)
            if (dir != null) {
                val files = dir.listFiles()?.sortedByDescending { it.lastModified() }
                if (!files.isNullOrEmpty()) {
                    sendFile(Uri.fromFile(files.first()))
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 201)
                return
            }
            val outputFile = File(getExternalFilesDir(null), "voice_${System.currentTimeMillis()}.m4a")
            audioRecorder = AudioRecorder(outputFile)
            audioRecorder?.start()
            btnRecord.text = "STOP"
            isRecording = true
        }
    }

    private fun startVoipCall() {
        val node = peerNode ?: return
        if (!isCallActive) {
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
        }
    }

    private fun sendMessage(content: String) {
        val node = peerNode ?: return
        SecureChatManager.sendEncryptedMessage(content, node.ipAddress, keyStore)
        val msg = ChatMessage(
            chatId = chatId,
            senderName = "Me",
            content = content,
            isSent = true
        )
        scope.launch(Dispatchers.IO) {
            chatDb.chatDao().insertMessage(msg)
            withContext(Dispatchers.Main) { loadMessages() }
        }
    }

    private fun loadMessages() {
        scope.launch(Dispatchers.IO) {
            val messages = chatDb.chatDao().getMessages(chatId)
            withContext(Dispatchers.Main) {
                adapter = ChatAdapter(messages)
                recyclerChat.adapter = adapter
                recyclerChat.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(cameraUri)
        } else if (requestCode == 201 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toggleRecording()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        audioRecorder?.stop()
        VoipEngine.stop()
    }
}
'@

Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatActivity.kt" $safeChatActivity

# Build & install
Write-Host "Build ulang..." -ForegroundColor Cyan
.\gradlew assembleDebug
if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    & "C:\adb\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
    Write-Host "ChatActivity diperbaiki. Silakan buka Chat." -ForegroundColor Green
} else {
    Write-Host "Build gagal. Cek error." -ForegroundColor Red
}