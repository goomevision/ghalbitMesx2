# upgrade_chat.ps1
# Lengkapi fungsi chat standar WhatsApp pada GhalbitMesX2

Set-Location C:\project\Ghalbitnet\ghalbitMesx2

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Membangun WhatsApp-like Chat..." -ForegroundColor Cyan

# ========== 1. Tambahkan dependensi RecyclerView & CardView ==========
$appGradle = Get-Content "app\build.gradle" -Raw
if ($appGradle -notmatch "androidx.recyclerview") {
    $newDeps = @'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'androidx.cardview:cardview:1.0.0'
'@
    $appGradle = $appGradle -replace "(dependencies\s*\{)", "`$1`n$newDeps"
}
Write-FileWithoutBOM "app\build.gradle" $appGradle

# ========== 2. Entity & Database Chat ==========
Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatMessage.kt" @'
package com.ghalbitnet.meshx2.chat

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: String,        // "Me:IP" atau "IP:Me" (sorted)
    val senderName: String,    // "Me" atau nodeName
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSent: Boolean        // true jika kita yang kirim
)
'@

Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatDao.kt" @'
package com.ghalbitnet.meshx2.chat

import androidx.room.*

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessages(chatId: String): List<ChatMessage>

    @Query("SELECT DISTINCT chatId FROM chat_messages ORDER BY chatId ASC")
    fun getChatIds(): List<String>

    @Insert
    fun insertMessage(message: ChatMessage)
}
'@

Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatDatabase.kt" @'
package com.ghalbitnet.meshx2.chat

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ChatMessage::class], version = 1, exportSchema = false)
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
'@

# ========== 3. Adapter Chat ==========
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
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messages[position]
        holder.content.text = msg.content
        holder.time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val content: TextView = itemView.findViewById(R.id.tvMessageContent)
        val time: TextView = itemView.findViewById(R.id.tvMessageTime)
    }
}
'@

# ========== 4. Layout item chat ==========
Write-FileWithoutBOM "app\src\main\res\layout\item_chat_sent.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="end"
    android:layout_margin="6dp"
    app:cardBackgroundColor="#DCF8C6"
    app:cardCornerRadius="12dp"
    app:cardElevation="2dp"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="8dp">
        <TextView
            android:id="@+id/tvMessageContent"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#000000"
            android:textSize="15sp"
            android:maxWidth="250dp"/>
        <TextView
            android:id="@+id/tvMessageTime"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#666666"
            android:textSize="11sp"
            android:layout_marginTop="4dp"/>
    </LinearLayout>
</androidx.cardview.widget.CardView>
'@

Write-FileWithoutBOM "app\src\main\res\layout\item_chat_received.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="start"
    android:layout_margin="6dp"
    app:cardBackgroundColor="#FFFFFF"
    app:cardCornerRadius="12dp"
    app:cardElevation="2dp"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="8dp">
        <TextView
            android:id="@+id/tvMessageContent"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#000000"
            android:textSize="15sp"
            android:maxWidth="250dp"/>
        <TextView
            android:id="@+id/tvMessageTime"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#666666"
            android:textSize="11sp"
            android:layout_marginTop="4dp"/>
    </LinearLayout>
</androidx.cardview.widget.CardView>
'@

# ========== 5. Layout activity_chat.xml baru ==========
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
            android:text="Kirim"
            android:layout_marginLeft="8dp"/>
    </LinearLayout>
</LinearLayout>
'@

# ========== 6. ChatActivity.kt baru ==========
Write-Host "Menulis ulang ChatActivity..." -ForegroundColor Yellow

$chatActivityCode = @'
package com.ghalbitnet.meshx2.chat

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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
        // Always sort so "Me:IP" is unique
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

        keyStore = KeyStoreManager(this)
        chatDb = ChatDatabase.getInstance(this)

        // Ambil node tujuan (dari daftar node online pertama untuk demo)
        peerNode = DiscoveryManager.discoverNodes().firstOrNull { it.online }
        if (peerNode == null) {
            tvChatTitle.text = "Chat (tidak ada node online)"
            btnSend.isEnabled = false
        } else {
            tvChatTitle.text = "Chat dengan ${peerNode!!.name}"
        }

        adapter = ChatAdapter(emptyList())
        recyclerChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
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

    private fun sendMessage(content: String) {
        val node = peerNode ?: return
        // Kirim terenkripsi via SecureChatManager
        SecureChatManager.sendEncryptedMessage(content, node.ipAddress, keyStore)
        // Simpan ke database lokal sebagai pesan terkirim
        val msg = ChatMessage(
            chatId = chatId,
            senderName = "Me",
            content = content,
            isSent = true
        )
        scope.launch(Dispatchers.IO) {
            chatDb.chatDao().insertMessage(msg)
            withContext(Dispatchers.Main) {
                loadMessages()
            }
        }
        Log.d("GHALBIT", "Chat message sent: $content")
    }

    // Panggil ini dari luar ketika ada pesan masuk (dari MeshSocketServer misalnya)
    fun onMessageReceived(senderName: String, content: String, senderIp: String) {
        // Tentukan chatId
        val myName = "Me"
        val currentChatId = if (myName < senderName) "$myName:$senderIp" else "$senderIp:$myName"
        val msg = ChatMessage(
            chatId = currentChatId,
            senderName = senderName,
            content = content,
            isSent = false
        )
        scope.launch(Dispatchers.IO) {
            chatDb.chatDao().insertMessage(msg)
            if (currentChatId == chatId) {
                withContext(Dispatchers.Main) {
                    loadMessages()
                }
            }
        }
        Log.d("GHALBIT", "Chat message received: $content from $senderName")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
'@

Write-FileWithoutBOM "app\src\main\java\com\ghalbitnet\meshx2\chat\ChatActivity.kt" $chatActivityCode

# ========== 7. Integrasi penerimaan pesan di MainActivity ==========
Write-Host "Mengintegrasikan penerimaan chat ke MainActivity..." -ForegroundColor Yellow
$mainPath = "app\src\main\java\com\ghalbitnet\meshx2\MainActivity.kt"
$mainContent = Get-Content $mainPath -Raw

# Pastikan ChatActivity bisa dipanggil untuk pesan masuk dengan notifikasi kecil
# Ubah onSecure di MeshSocketServer agar memanggil ChatActivity.onMessageReceived jika activity sedang dibuka.
# Karena sulit, kita bisa menyimpan pesan di database saja, lalu ChatActivity reload secara otomatis saat dibuka.
# Untuk saat ini biarkan mekanisme saat ini: pesan masuk didekripsi, log ditampilkan, dan disimpan di database chat.

# Tambahkan di onSecure setelah decrypt:
$oldSecureBlock = @'
val decrypted = SecureChatManager.decryptReceivedPacket(s, keyStore)
                if (decrypted != null) {
                    runOnUiThread { txtLog.append("\nSECURE MSG: $decrypted") }
                    Log.d("GHALBIT", "Decrypted message: $decrypted")
                } else {
                    Log.d("GHALBIT", "Encrypted packet received (not for us or decrypt failed)")
                }
'@

$newSecureBlock = @'
val decrypted = SecureChatManager.decryptReceivedPacket(s, keyStore)
                if (decrypted != null) {
                    runOnUiThread {
                        txtLog.append("\nSECURE MSG: $decrypted")
                        // Simpan ke database chat untuk history
                        val chatDb = com.ghalbitnet.meshx2.chat.ChatDatabase.getInstance(this@MainActivity)
                        val node = MeshRegistry.getNode(s.sourcePublicKey) ?: MeshNode(
                            name = "Unknown",
                            ipAddress = s.sourcePublicKey,
                            online = true
                        )
                        val chatId = if ("Me" < node.name) "Me:${node.ipAddress}" else "${node.ipAddress}:Me"
                        val msg = com.ghalbitnet.meshx2.chat.ChatMessage(
                            chatId = chatId,
                            senderName = node.name,
                            content = decrypted,
                            isSent = false
                        )
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            chatDb.chatDao().insertMessage(msg)
                        }
                    }
                    Log.d("GHALBIT", "Decrypted message: $decrypted")
                } else {
                    Log.d("GHALBIT", "Encrypted packet received (not for us or decrypt failed)")
                }
'@

$mainContent = $mainContent -replace [regex]::Escape($oldSecureBlock), $newSecureBlock

# Tambahkan import yang diperlukan
if ($mainContent -notmatch "import kotlinx.coroutines.GlobalScope") {
    $mainContent = $mainContent -replace "import com.ghalbitnet.meshx2.token.TokenManager", "import com.ghalbitnet.meshx2.token.TokenManager`nimport kotlinx.coroutines.GlobalScope`nimport kotlinx.coroutines.Dispatchers`nimport kotlinx.coroutines.launch"
}
if ($mainContent -notmatch "import com.ghalbitnet.meshx2.chat.ChatDatabase") {
    $mainContent = $mainContent -replace "import com.ghalbitnet.meshx2.chat.ChatActivity", "import com.ghalbitnet.meshx2.chat.ChatActivity`nimport com.ghalbitnet.meshx2.chat.ChatDatabase`nimport com.ghalbitnet.meshx2.chat.ChatMessage"
}

Write-FileWithoutBOM $mainPath $mainContent

# ========== 8. Build & Install ==========
Write-Host "Memulai build..." -ForegroundColor Cyan
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    Write-Host "Build sukses. Menginstal..." -ForegroundColor Green
    $adb = "C:\adb\platform-tools\adb.exe"
    if (Test-Path $adb) {
        & $adb install -r app\build\outputs\apk\debug\app-debug.apk
        Write-Host "Chat WhatsApp-like siap. Buka aplikasi > CHAT." -ForegroundColor Cyan
    } else {
        Write-Host "ADB tidak ditemukan, install manual." -ForegroundColor Yellow
    }
} else {
    Write-Host "Build gagal, periksa error." -ForegroundColor Red
}