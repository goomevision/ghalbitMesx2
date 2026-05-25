# stable_chat.ps1
Set-Location C:\project\Ghalbitnet\ghalbitMesx2

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Membuat ChatActivity stabil minimalis..." -ForegroundColor Cyan

$stableChatActivity = @'
package com.ghalbitnet.meshx2.chat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.discovery.DiscoveryManager
import com.ghalbitnet.meshx2.model.MeshNode
import com.ghalbitnet.meshx2.security.KeyStoreManager
import kotlinx.coroutines.*

class ChatActivity : AppCompatActivity() {
    private lateinit var recyclerChat: RecyclerView
    private lateinit var edtMessage: EditText
    private lateinit var btnSend: Button
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        recyclerChat = findViewById(R.id.recyclerChat)
        edtMessage = findViewById(R.id.edtMessage)
        btnSend = findViewById(R.id.btnSend)
        tvChatTitle = findViewById(R.id.tvChatTitle)

        // Nonaktifkan tombol lainnya untuk stabilitas
        findViewById<Button>(R.id.btnAttach).apply {
            setOnClickListener { Toast.makeText(this@ChatActivity, "Fitur akan datang", Toast.LENGTH_SHORT).show() }
        }
        findViewById<Button>(R.id.btnRecord).apply {
            setOnClickListener { Toast.makeText(this@ChatActivity, "Fitur akan datang", Toast.LENGTH_SHORT).show() }
        }
        findViewById<Button>(R.id.btnCall).apply {
            setOnClickListener { Toast.makeText(this@ChatActivity, "Fitur akan datang", Toast.LENGTH_SHORT).show() }
        }

        keyStore = KeyStoreManager(this)
        chatDb = ChatDatabase.getInstance(this)

        val peerIp = intent.getStringExtra("peerIp")
        val peerName = intent.getStringExtra("peerName")
        if (peerIp != null && peerName != null) {
            peerNode = MeshNode(name = peerName, ipAddress = peerIp, online = true)
        } else {
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
    }
}
'@

Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatActivity.kt" $stableChatActivity

# Build & Install
Write-Host "Build..." -ForegroundColor Cyan
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    & "C:\adb\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
    Write-Host "Chat stabil terpasang. Hanya kirim teks yang aktif." -ForegroundColor Green
} else {
    Write-Host "Build gagal." -ForegroundColor Red
}