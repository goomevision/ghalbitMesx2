# add_multimedia_chat.ps1
# Sempurnakan chat dengan gambar, suara, telepon

Set-Location C:\project\Ghalbitnet\ghalbitMesx2

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Menyempurnakan chat dengan multimedia..." -ForegroundColor Cyan

# ================================================
# 1. Perbarui izin di AndroidManifest.xml
# ================================================
Write-Host "Menambahkan izin audio & storage..." -ForegroundColor Yellow
$manifest = Get-Content "app\src\main\AndroidManifest.xml" -Raw
$newPerms = @(
    'android.permission.RECORD_AUDIO',
    'android.permission.READ_EXTERNAL_STORAGE',
    'android.permission.WRITE_EXTERNAL_STORAGE'
)
foreach ($perm in $newPerms) {
    if ($manifest -notmatch [regex]::Escape($perm)) {
        $manifest = $manifest -replace "(<application)", "    <uses-permission android:name=`"$perm`" />`n`$1"
    }
}
Write-FileWithoutBOM "app\src\main\AndroidManifest.xml" $manifest

# ================================================
# 2. Update ChatMessage entity (tambah contentType, filePath)
# ================================================
Write-Host "Memperbarui entity ChatMessage..." -ForegroundColor Yellow
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatMessage.kt" @'
package com.ghalbitnet.meshx2.chat

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: String,
    val senderName: String,
    val content: String,
    val contentType: String = "TEXT", // TEXT, IMAGE, AUDIO
    val filePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isSent: Boolean
)
'@

# ================================================
# 3. Update ChatDatabase (versi 2, destructive migration)
# ================================================
Write-Host "Memperbarui ChatDatabase..." -ForegroundColor Yellow
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatDatabase.kt" @'
package com.ghalbitnet.meshx2.chat

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ChatMessage::class], version = 2, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getInstance(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "ghalbit_chat"
                ).fallbackToDestructiveMigration() // agar praktis development
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
'@

# ================================================
# 4. FileTransferManager (via Nearby Payload.File atau chunk TCP)
# ================================================
Write-Host "Membuat FileTransferManager..." -ForegroundColor Yellow
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\network\FileTransferManager.kt" @'
package com.ghalbitnet.meshx2.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ghalbitnet.meshx2.routing.MeshRegistry
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.chat.ChatDatabase
import com.ghalbitnet.meshx2.chat.ChatMessage
import kotlinx.coroutines.*
import java.io.*
import java.net.Socket

object FileTransferManager {
    private const val FILE_PORT = 56566

    fun sendFile(context: Context, uri: Uri, receiverIp: String, receiverName: String, keyStore: KeyStoreManager) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val fileBytes = inputStream.readBytes()
                val fileName = uri.lastPathSegment ?: "file.bin"
                val extension = fileName.substringAfterLast('.', "").lowercase()
                val contentType = when (extension) {
                    "jpg", "jpeg", "png", "gif", "bmp", "webp" -> "IMAGE"
                    "mp3", "wav", "aac", "ogg", "m4a" -> "AUDIO"
                    else -> "FILE"
                }

                // Simpan file lokal untuk history
                val localFile = File(context.cacheDir, "sent_$fileName")
                localFile.writeBytes(fileBytes)

                // Simpan ke database chat
                val chatDb = ChatDatabase.getInstance(context)
                val chatId = if ("Me" < receiverName) "Me:$receiverIp" else "$receiverIp:Me"
                val msg = ChatMessage(
                    chatId = chatId,
                    senderName = "Me",
                    content = if (contentType == "IMAGE") "[Gambar: $fileName]" else if (contentType == "AUDIO") "[Suara: $fileName]" else "[File: $fileName]",
                    contentType = contentType,
                    filePath = localFile.absolutePath,
                    isSent = true
                )
                chatDb.chatDao().insertMessage(msg)

                // Kirim file via TCP
                withContext(Dispatchers.IO) {
                    sendFileViaSocket(receiverIp, fileName, fileBytes)
                }
                Log.d("GHALBIT", "File sent: $fileName")
            } catch (e: Exception) {
                Log.e("GHALBIT", "File send error", e)
            }
        }
    }

    private fun sendFileViaSocket(ip: String, fileName: String, fileBytes: ByteArray) {
        var socket: Socket? = null
        try {
            socket = Socket(ip, FILE_PORT)
            val out = DataOutputStream(socket.getOutputStream())
            out.writeUTF(fileName)
            out.writeInt(fileBytes.size)
            out.write(fileBytes)
            out.flush()
        } catch (e: Exception) {
            Log.e("GHALBIT", "File socket error", e)
        } finally {
            socket?.close()
        }
    }

    fun startFileServer(context: Context) {
        Thread {
            try {
                val server = java.net.ServerSocket(FILE_PORT)
                Log.d("GHALBIT", "File server started on port $FILE_PORT")
                while (true) {
                    val client = server.accept()
                    Thread {
                        try {
                            val input = DataInputStream(client.getInputStream())
                            val fileName = input.readUTF()
                            val size = input.readInt()
                            val fileBytes = ByteArray(size)
                            input.readFully(fileBytes)

                            // Simpan file lokal
                            val file = File(context.cacheDir, "received_$fileName")
                            file.writeBytes(fileBytes)

                            Log.d("GHALBIT", "File received: $fileName")
                            // Pemrosesan lebih lanjut (simpan ke database) akan ditangani oleh penerima
                        } catch (e: Exception) {
                            Log.e("GHALBIT", "File receive error", e)
                        }
                    }.start()
                }
            } catch (e: Exception) {
                Log.e("GHALBIT", "File server error", e)
            }
        }.start()
    }
}
'@

# ================================================
# 5. AudioRecorder & AudioPlayer untuk pesan suara
# ================================================
Write-Host "Membuat AudioRecorder..." -ForegroundColor Yellow
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\chat\AudioRecorder.kt" @'
package com.ghalbitnet.meshx2.chat

import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorder(private val outputFile: File) {
    private var recorder: MediaRecorder? = null

    fun start() {
        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
                Log.d("GHALBIT", "Audio recording started")
            }
        } catch (e: IOException) {
            Log.e("GHALBIT", "Audio start error", e)
        }
    }

    fun stop() {
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            Log.d("GHALBIT", "Audio recording stopped")
        } catch (e: Exception) {
            Log.e("GHALBIT", "Audio stop error", e)
        }
    }
}
'@

Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\chat\AudioPlayer.kt" @'
package com.ghalbitnet.meshx2.chat

import android.media.MediaPlayer
import android.util.Log
import java.io.File

object AudioPlayer {
    fun play(filePath: String) {
        val player = MediaPlayer()
        try {
            player.setDataSource(filePath)
            player.prepare()
            player.start()
            Log.d("GHALBIT", "Audio playback started")
        } catch (e: Exception) {
            Log.e("GHALBIT", "Audio playback error", e)
        }
    }
}
'@

# ================================================
# 6. VoipEngine push-to-talk via UDP
# ================================================
Write-Host "Membuat VoipEngine..." -ForegroundColor Yellow
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\chat\VoipEngine.kt" @'
package com.ghalbitnet.meshx2.chat

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket

object VoipEngine {
    private const val SAMPLE_RATE = 8000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    private var udpSocket: DatagramSocket? = null
    private var running = false

    fun startTalk(targetIp: String, targetPort: Int = 56567) {
        stop()
        running = true
        Thread {
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    1024
                )
                audioRecord?.startRecording()
                udpSocket = DatagramSocket()

                val buffer = ByteArray(1024)
                while (running) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                    if (read > 0) {
                        val packet = DatagramPacket(buffer, read)
                        packet.address = java.net.InetAddress.getByName(targetIp)
                        packet.port = targetPort
                        udpSocket?.send(packet)
                    }
                }
            } catch (e: Exception) {
                Log.e("GHALBIT", "VoIP talk error", e)
            }
        }.start()
    }

    fun startListen(port: Int = 56567) {
        stop()
        running = true
        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        audioTrack = AudioTrack(
            AudioManager.STREAM_VOICE_CALL,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize,
            AudioTrack.MODE_STREAM
        )
        audioTrack?.play()

        Thread {
            try {
                udpSocket = DatagramSocket(port)
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                while (running) {
                    udpSocket?.receive(packet)
                    audioTrack?.write(packet.data, 0, packet.length)
                }
            } catch (e: Exception) {
                Log.e("GHALBIT", "VoIP listen error", e)
            }
        }.start()
    }

    fun stop() {
        running = false
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
    }
}
'@

# ================================================
# 7. Update UI activity_chat.xml (tombol lampiran, rekam, panggil)
# ================================================
Write-Host "Memperbarui layout chat..." -ForegroundColor Yellow
Write-FileWithoutBOM "app\src\main\res\layout\activity_chat.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#ECE5DD">

    <TextView
        android:id="@+id/tvChatTitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="20sp"
        android:textColor="#FFFFFF"
        android:background="#075E54"
        android:padding="12dp"
        android:textStyle="bold"
        android:gravity="center"/>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerChat"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:padding="8dp"/>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:background="#FFFFFF"
        android:padding="8dp">

        <Button
            android:id="@+id/btnAttach"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Lampiran"/>

        <Button
            android:id="@+id/btnRecord"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Rekam"/>

        <EditText
            android:id="@+id/edtMessage"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="Ketik pesan..."
            android:background="@android:drawable/edit_text"
            android:padding="8dp"/>

        <Button
            android:id="@+id/btnSend"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Kirim"/>

        <Button
            android:id="@+id/btnCall"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Tel"/>
    </LinearLayout>
</LinearLayout>
'@

# ================================================
# 8. Update ChatActivity.kt
# ================================================
Write-Host "Memperbarui ChatActivity..." -ForegroundColor Yellow
$chatActivityCode = @'
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
import java.text.SimpleDateFormat
import java.util.*

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

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            sendFile(it)
        }
    }

    private val cameraUri = File(getExternalFilesDir(null), "photo_${System.currentTimeMillis()}.jpg")
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            sendFile(Uri.fromFile(cameraUri))
        }
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

        btnAttach.setOnClickListener {
            showAttachmentOptions()
        }

        btnRecord.setOnClickListener {
            toggleRecording()
        }

        btnCall.setOnClickListener {
            startVoipCall()
        }
    }

    private fun showAttachmentOptions() {
        val items = arrayOf("Galeri", "Kamera")
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Lampirkan")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> imagePicker.launch("image/* video/* audio/*")
                    1 -> launchCamera()
                }
            }
            .show()
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", cameraUri)
            cameraLauncher.launch(uri)
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 200)
        }
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
            Toast.makeText(this, "Rekaman disimpan", Toast.LENGTH_SHORT).show()
            // Kirim file rekaman
            val recentRecording = File(getExternalFilesDir(null)?.listFiles()?.lastOrNull()?.path ?: return)
            sendFile(Uri.fromFile(recentRecording))
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

    private var isCallActive = false
    private fun startVoipCall() {
        val node = peerNode ?: return
        if (!isCallActive) {
            VoipEngine.startTalk(node.ipAddress)
            VoipEngine.startListen()
            btnCall.text = "Stop"
            isCallActive = true
            Toast.makeText(this, "Telepon dimulai (push-to-talk)", Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        audioRecorder?.stop()
        VoipEngine.stop()
    }
}
'@
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatActivity.kt" $chatActivityCode

# ================================================
# 9. Build & Install
# ================================================
Write-Host "Build..." -ForegroundColor Cyan
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    & "C:\adb\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
    Write-Host "Chat multimedia siap. Kirim gambar, rekam suara, telepon!" -ForegroundColor Green
} else { Write-Host "Build gagal" -ForegroundColor Red }