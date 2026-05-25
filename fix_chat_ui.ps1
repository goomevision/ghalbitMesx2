# fix_chat_ui.ps1
# Perbaiki tampilan chat standar WhatsApp

Set-Location C:\project\Ghalbitnet\ghalbitMesx2

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Memperbaiki UI chat..." -ForegroundColor Cyan

# 1. Perbaiki item_chat_sent (WhatsApp hijau)
Write-FileWithoutBOM "app\src\main\res\layout\item_chat_sent.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingLeft="64dp"
    android:paddingRight="8dp"
    android:paddingTop="4dp"
    android:paddingBottom="4dp">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="end"
        android:background="@drawable/bg_sent_message"
        android:orientation="vertical"
        android:padding="8dp">

        <TextView
            android:id="@+id/tvMessageContent"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#000000"
            android:textSize="15sp"
            android:maxWidth="240dp"/>

        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginTop="4dp"
            android:gravity="end">

            <TextView
                android:id="@+id/tvMessageTime"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="#666666"
                android:textSize="11sp"/>
        </LinearLayout>
    </LinearLayout>
</LinearLayout>
'@

# background sent: rounded green
Write-FileWithoutBOM "app\src\main\res\drawable\bg_sent_message.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#DCF8C6"/>
    <corners android:radius="12dp"/>
</shape>
'@

# 2. Perbaiki item_chat_received (WhatsApp putih + nama pengirim)
Write-FileWithoutBOM "app\src\main\res\layout\item_chat_received.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingLeft="8dp"
    android:paddingRight="64dp"
    android:paddingTop="4dp"
    android:paddingBottom="4dp">

    <TextView
        android:id="@+id/tvSenderName"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="#075E54"
        android:textSize="12sp"
        android:textStyle="bold"
        android:paddingLeft="8dp"
        android:paddingBottom="2dp"/>

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:background="@drawable/bg_received_message"
        android:orientation="vertical"
        android:padding="8dp">

        <TextView
            android:id="@+id/tvMessageContent"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#000000"
            android:textSize="15sp"
            android:maxWidth="240dp"/>

        <TextView
            android:id="@+id/tvMessageTime"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#666666"
            android:textSize="11sp"
            android:layout_marginTop="4dp"/>
    </LinearLayout>
</LinearLayout>
'@

Write-FileWithoutBOM "app\src\main\res\drawable\bg_received_message.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFFFFF"/>
    <corners android:radius="12dp"/>
</shape>
'@

# 3. Perbaiki ChatAdapter supaya menampilkan nama pengirim
Write-Host "Memperbarui ChatAdapter..." -ForegroundColor Yellow
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatAdapter.kt" @'
package com.ghalbitnet.meshx2.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ghalbitnet.meshx2.R
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isSent) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == VIEW_TYPE_SENT) R.layout.item_chat_sent else R.layout.item_chat_received
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MessageViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messages[position]
        holder.content.text = msg.content
        holder.time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
        if (holder.senderName != null) {
            // Untuk pesan masuk, tampilkan nama pengirim
            holder.senderName.text = msg.senderName
        }
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
        val content: TextView = itemView.findViewById(R.id.tvMessageContent)
        val time: TextView = itemView.findViewById(R.id.tvMessageTime)
        val senderName: TextView? = if (viewType == VIEW_TYPE_RECEIVED) itemView.findViewById(R.id.tvSenderName) else null
    }
}
'@

# 4. Buat ContactListActivity untuk memilih room chat
Write-Host "Membuat ContactListActivity..." -ForegroundColor Yellow
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\chat\ContactListActivity.kt" @'
package com.ghalbitnet.meshx2.chat

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.discovery.DiscoveryManager

class ContactListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_list)

        val listView = findViewById<ListView>(R.id.listContacts)
        val nodes = DiscoveryManager.discoverNodes().filter { it.online }

        if (nodes.isEmpty()) {
            findViewById<TextView>(R.id.tvEmpty).text = "Tidak ada kontak online"
            return
        }

        val names = nodes.map { it.name }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = nodes[position]
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("peerIp", selected.ipAddress)
                putExtra("peerName", selected.name)
            }
            startActivity(intent)
        }
    }
}
'@

Write-FileWithoutBOM "app\src\main\res\layout\activity_contact_list.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#ECE5DD">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Kontak Ghalbit"
        android:textSize="20sp"
        android:textColor="#FFFFFF"
        android:background="#075E54"
        android:padding="12dp"
        android:textStyle="bold"/>

    <TextView
        android:id="@+id/tvEmpty"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp"
        android:textColor="#666666"/>

    <ListView
        android:id="@+id/listContacts"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"/>
</LinearLayout>
'@

# 5. Perbarui ChatActivity agar menerima intent (pilih kontak)
Write-Host "Memperbarui ChatActivity untuk multi kontakt..." -ForegroundColor Yellow
$chatActivityNew = @'
package com.ghalbitnet.meshx2.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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

        // Dapatkan data peer dari intent atau fallback
        val peerIp = intent.getStringExtra("peerIp")
        val peerName = intent.getStringExtra("peerName")
        if (peerIp != null && peerName != null) {
            peerNode = MeshNode(name = peerName, ipAddress = peerIp, online = true)
        } else {
            // fallback: node online pertama
            peerNode = DiscoveryManager.discoverNodes().firstOrNull { it.online }
        }

        if (peerNode == null) {
            tvChatTitle.text = "Chat (tidak ada node)"
            btnSend.isEnabled = false
        } else {
            tvChatTitle.text = peerNode!!.name
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
        if (getExternalFilesDir(null) == null) {
            Toast.makeText(this, "Penyimpanan tidak tersedia", Toast.LENGTH_SHORT).show()
            return
        }
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
            val dir = getExternalFilesDir(null) ?: run {
                Toast.makeText(this, "Penyimpanan tidak siap", Toast.LENGTH_SHORT).show()
                return
            }
            val files = dir.listFiles()?.sortedByDescending { it.lastModified() }
            if (!files.isNullOrEmpty()) {
                sendFile(Uri.fromFile(files.first()))
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
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatActivity.kt" $chatActivityNew

# 6. Tambahkan ContactListActivity ke AndroidManifest.xml
$manifest = Get-Content "app\src\main\AndroidManifest.xml" -Raw
if ($manifest -notmatch "ContactListActivity") {
    $manifest = $manifest -replace "(<activity android:name=`".chat.ChatActivity`")", '<activity android:name=".chat.ContactListActivity" android:exported="false" />'+"`n        `$1"
    Write-FileWithoutBOM "app\src\main\AndroidManifest.xml" $manifest
}

# 7. Ubah tombol Chat di MainActivity agar menuju ContactListActivity
$mainPath = "app\src\main\java\com\ghalbitnet\meshx2\MainActivity.kt"
$mainContent = Get-Content $mainPath -Raw
$mainContent = $mainContent -replace 'startActivity\(Intent\(this, ChatActivity::class\.java\)\)', 'startActivity(Intent(this, com.ghalbitnet.meshx2.chat.ContactListActivity::class.java))'
Write-FileWithoutBOM $mainPath $mainContent

# 8. Build & Install
Write-Host "Build..." -ForegroundColor Cyan
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    & "C:\adb\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
    Write-Host "UI Chat WhatsApp siap. Kini ada daftar kontak, nama pengirim, gelembung rapi." -ForegroundColor Green
} else {
    Write-Host "Build gagal, cek error." -ForegroundColor Red
}