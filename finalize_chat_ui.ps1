# finalize_chat_ui.ps1
Set-Location C:\project\Ghalbitnet\ghalbitMesx2

function Write-FileWithoutBOM($path, $content) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content.Trim(), (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "Menyempurnakan UI chat ke standar profesional..." -ForegroundColor Cyan

# ================================================
# 1. Update activity_chat.xml – tombol dengan ikon
# ================================================
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
        android:textSize="18sp"
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
        android:padding="8dp"
        android:gravity="center_vertical">

        <Button
            android:id="@+id/btnAttach"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:text="📎"
            android:textSize="22sp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:padding="0dp"/>

        <EditText
            android:id="@+id/edtMessage"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="Ketik pesan..."
            android:background="@android:drawable/edit_text"
            android:padding="10dp"
            android:layout_marginLeft="4dp"
            android:layout_marginRight="4dp"/>

        <Button
            android:id="@+id/btnRecord"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:text="🎤"
            android:textSize="22sp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:padding="0dp"/>

        <Button
            android:id="@+id/btnSend"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:text="➤"
            android:textColor="#075E54"
            android:textSize="22sp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:padding="0dp"/>

        <Button
            android:id="@+id/btnCall"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:text="📞"
            android:textSize="22sp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:padding="0dp"/>
    </LinearLayout>
</LinearLayout>
'@

# ================================================
# 2. Update ChatAdapter – tampilkan ikon untuk gambar/suara
# ================================================
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
        val context = holder.itemView.context

        // Tampilkan konten sesuai tipe
        val displayText = when (msg.contentType) {
            "IMAGE" -> "🖼️ Gambar"
            "AUDIO" -> "🔊 Pesan Suara"
            else -> msg.content
        }
        holder.content.text = displayText

        holder.time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))

        if (holder.senderName != null) {
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

# ================================================
# 3. Pastikan item_chat_sent dan item_chat_received sudah ok (tidak diubah)
# ================================================
Write-Host "Item layout sudah sesuai, melewati..." -ForegroundColor Yellow

# ================================================
# 4. Build & Install
# ================================================
Write-Host "Build..." -ForegroundColor Cyan
.\gradlew assembleDebug

if (Test-Path "app\build\outputs\apk\debug\app-debug.apk") {
    Write-Host "Build sukses. Menginstal..." -ForegroundColor Green
    $adb = "C:\adb\platform-tools\adb.exe"
    if (Test-Path $adb) {
        & $adb install -r app\build\outputs\apk\debug\app-debug.apk
        Write-Host "Chat profesional siap. Nikmati tampilan baru!" -ForegroundColor Cyan
    } else {
        Write-Host "ADB tidak ditemukan, install manual." -ForegroundColor Yellow
    }
} else {
    Write-Host "Build gagal." -ForegroundColor Red
}