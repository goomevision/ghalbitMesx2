package com.ghalbitnet.meshx2.chat

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.call.CallSessionActivity
import com.ghalbitnet.meshx2.call.VoiceCallRegistry
import com.ghalbitnet.meshx2.core.log.MeshLogger
import com.ghalbitnet.meshx2.core.utils.AppNotificationManager
import com.ghalbitnet.meshx2.file.FileTransferManager
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.network.ReliablePacketSender
import androidx.lifecycle.lifecycleScope
import android.util.Base64
import androidx.activity.result.contract.ActivityResultContracts
import com.ghalbitnet.meshx2.settings.ChatMediaSettingsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ghalbitnet.meshx2.routing.PacketTtlManager
import com.ghalbitnet.meshx2.security.CryptoEngine
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.core.utils.UiFeedbackManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class ChatActivity : AppCompatActivity() {
    companion object {
        private const val MAX_MESSAGE_LENGTH = 4096
        private const val MIN_VOICE_DURATION_MS = 700L

        @Volatile
        private var activePeerName: String? = null

        fun isViewingChatWith(peerName: String): Boolean {
            return activePeerName == peerName
        }
    }

    private lateinit var txtChat: TextView
    private lateinit var rvMessages: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var txtChatStatus: TextView
    private lateinit var edtMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnCall: Button
    private lateinit var btnAttach: Button
    private lateinit var btnCamera: Button
    private lateinit var btnVoice: Button
    private lateinit var btnRetryFailed: Button

    private var peerIp: String = ""
    private var peerName: String = ""
    private lateinit var chatDb: ChatDatabase
    private lateinit var keyStore: KeyStoreManager
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingFile: File? = null
    private var isRecording = false
    private var recordStartAt = 0L
    private var pendingVoiceStart = false
    private var recordingStatusJob: Job? = null

    private val filePickerLauncher =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                sendAttachment(
                    fileUri = uri,
                    contentType = resolveAttachmentContentType(uri),
                    displayName = readDisplayName(uri)
                )
            }
        }

    private val cameraPreviewLauncher =
        registerForActivityResult(
            ActivityResultContracts.TakePicturePreview()
        ) { bitmap ->
            if (bitmap != null) {
                sendCapturedPhoto(bitmap)
            } else {
                txtChatStatus.text = getString(R.string.chat_camera_cancelled)
            }
        }

    private val audioPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                if (pendingVoiceStart) {
                    startVoiceRecording()
                }
            } else {
                txtChatStatus.text = getString(R.string.chat_voice_permission)
                UiFeedbackManager.showToast(
                    this,
                    getString(R.string.chat_voice_permission)
                )
            }
            pendingVoiceStart = false
        }

    private val packetReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                val source =
                    intent?.getStringExtra("source") ?: "-"

                val rawPayload =
                    intent?.getStringExtra("payload") ?: ""

                val payload =
                    PacketTtlManager.extractMessage(rawPayload)

                val type =
                    intent?.getStringExtra("type") ?: ""

                if (source != peerName && type != "ACK") {
                    return
                }

                if (type == "FILE_CHUNK") {
                    return
                }

                if (payload.isNotEmpty()) {
                    if (type == "ACK") {
                        lifecycleScope.launch {
                            if (payload.isNotBlank()) {
                                withContext(Dispatchers.IO) {
                                    chatDb.chatDao().updateStatus(payload, "RECEIVED")
                                }
                            }

                            renderHistory("Pesan diterima oleh $source")
                        }
                    } else if (type == "AUDIO_RECEIVED") {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                chatDb.chatDao().updateStatus(
                                    payload,
                                    "RECEIVED"
                                )
                            }

                            renderHistory("Pesan suara diterima oleh $source")
                        }
                    } else if (type == "AUDIO_PLAYED") {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                chatDb.chatDao().updateStatus(
                                    payload,
                                    "PLAYED"
                                )
                            }

                            renderHistory("Pesan suara diputar oleh $source")
                        }
                    } else {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                val packetId =
                                    intent?.getStringExtra("packetId")
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "IN-$source-${System.currentTimeMillis()}"

                                if (chatDb.chatDao().countByPacketId(packetId) == 0) {
                                    chatDb.chatDao().insertMessage(
                                        ChatMessage(
                                            packetId = packetId,
                                            chatId = peerName,
                                            senderName = source,
                                            content = if (type == "SOS") {
                                                "SOS ALERT: $payload"
                                            } else {
                                                payload
                                            },
                                            contentType = if (type == "SOS") "SOS" else "TEXT",
                                            isSent = false,
                                            status = "RECEIVED"
                                        )
                                    )
                                }
                            }

                            renderHistory()
                        }
                    }
                }
            }
        }

    private val attachmentReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                val source =
                    intent?.getStringExtra(FileTransferManager.EXTRA_ATTACHMENT_SOURCE)
                        ?: return

                if (source != peerName) {
                    return
                }

                lifecycleScope.launch {
                    val label =
                        intent.getStringExtra(FileTransferManager.EXTRA_ATTACHMENT_LABEL)

                    if (label.isNullOrBlank()) {
                        renderHistory()
                    } else {
                        renderHistory("$label diterima")
                    }
                }
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_chat)

        peerIp =
            intent.getStringExtra("peerIp") ?: ""

        peerName =
            intent.getStringExtra("peerName") ?: "UNKNOWN"

        chatDb =
            ChatDatabase.getInstance(this)

        keyStore =
            KeyStoreManager(this)

        if (peerIp.isBlank() && peerName.isNotBlank()) {
            peerIp = keyStore.getPeerAddress(peerName).orEmpty()
        }

        txtChat =
            findViewById(R.id.txtChatHeader)

        txtChatStatus =
            findViewById(R.id.txtChatStatus)

        rvMessages =
            findViewById(R.id.rvMessages)

        chatAdapter =
            ChatAdapter(
                mutableListOf(),
                onMessageClick = { message ->
                    handleMessageClick(message)
                },
                onMessageLongClick = { message ->
                    showMessageActions(message)
                }
            )

        rvMessages.layoutManager =
            LinearLayoutManager(this).apply {
                stackFromEnd = true
            }

        rvMessages.adapter =
            chatAdapter

        edtMessage =
            findViewById(R.id.edtMessage)

        btnSend =
            findViewById(R.id.btnSend)

        btnCall =
            findViewById(R.id.btnCall)

        btnAttach =
            findViewById(R.id.btnAttach)

        btnCamera =
            findViewById(R.id.btnCamera)

        btnVoice =
            findViewById(R.id.btnVoice)

        btnRetryFailed =
            findViewById(R.id.btnRetryFailed)

        txtChat.text =
            buildChatHeader()

        lifecycleScope.launch {
            renderHistory()
        }

        txtChatStatus.text = getString(R.string.chat_voice_hold_hint)

        btnSend.setOnClickListener {
            sendMessage()
        }

        btnCall.setOnClickListener {
            startCallSession()
        }

        btnAttach.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        btnCamera.setOnClickListener {
            cameraPreviewLauncher.launch(null)
        }

        btnVoice.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    beginPushToTalk()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    finishPushToTalk(sendMessage = true)
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    finishPushToTalk(sendMessage = false)
                    true
                }

                else -> false
            }
        }

        btnRetryFailed.setOnClickListener {
            retryLastFailedMessage()
        }

        txtChat.setOnLongClickListener {
            showSaveContactDialog()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        activePeerName = peerName
        ChatReadStateManager.markChatViewed(this, peerName)
        AppNotificationManager.clearChatNotifications(this, peerName)

        LocalBroadcastManager
            .getInstance(this)
            .registerReceiver(
                packetReceiver,
                IntentFilter(
                    "com.ghalbitnet.meshx2.NEW_MESH_PACKET"
                )
            )

        LocalBroadcastManager
            .getInstance(this)
            .registerReceiver(
                attachmentReceiver,
                IntentFilter(
                    FileTransferManager.ACTION_ATTACHMENT_MESSAGE_RECEIVED
                )
            )
    }

    override fun onPause() {
        super.onPause()
        if (activePeerName == peerName) {
            activePeerName = null
        }

        stopVoiceRecording(cancelOnly = true)
        recordingStatusJob?.cancel()
        recordingStatusJob = null
        releasePlayer()

        LocalBroadcastManager
            .getInstance(this)
            .unregisterReceiver(
                packetReceiver
            )

        LocalBroadcastManager
            .getInstance(this)
            .unregisterReceiver(
                attachmentReceiver
            )
    }

    private fun beginPushToTalk() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingVoiceStart = true
            audioPermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
            return
        }

        startVoiceRecording()
    }

    private fun finishPushToTalk(sendMessage: Boolean) {
        pendingVoiceStart = false
        if (!isRecording) {
            return
        }
        stopVoiceRecording(cancelOnly = !sendMessage)
    }

    private fun startVoiceRecording() {
        if (isRecording) {
            return
        }

        try {
            val voiceDir = File(cacheDir, "voice_notes")
            if (!voiceDir.exists()) {
                voiceDir.mkdirs()
            }

            val outputFile =
                File(
                    voiceDir,
                    "voice_${System.currentTimeMillis()}.m4a"
                )

            val recorder =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(this)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(22050)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            currentRecordingFile = outputFile
            isRecording = true
            recordStartAt = System.currentTimeMillis()
            btnVoice.text = getString(R.string.chat_voice_stop)
            startRecordingStatusTicker()
        } catch (e: Exception) {
            MeshLogger.e("CHAT", "Voice record start failed", e)
            txtChatStatus.text = getString(R.string.chat_voice_failed)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_voice_failed)
            )
            stopVoiceRecording(cancelOnly = true)
        }
    }

    private fun stopVoiceRecording(cancelOnly: Boolean) {
        val recorder = mediaRecorder
        val recordedFile = currentRecordingFile

        mediaRecorder = null
        currentRecordingFile = null
        val wasRecording = isRecording
        isRecording = false
        val durationMs =
            System.currentTimeMillis() - recordStartAt
        recordStartAt = 0L
        recordingStatusJob?.cancel()
        recordingStatusJob = null
        btnVoice.text = getString(R.string.chat_voice_start)

        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        try {
            recorder?.reset()
        } catch (_: Exception) {
        }

        try {
            recorder?.release()
        } catch (_: Exception) {
        }

        if (cancelOnly || !wasRecording || recordedFile == null || !recordedFile.exists()) {
            recordedFile?.delete()
            if (!cancelOnly) {
                txtChatStatus.text = getString(R.string.chat_voice_failed)
            }
            return
        }

        if (durationMs < MIN_VOICE_DURATION_MS) {
            recordedFile.delete()
            txtChatStatus.text = getString(R.string.chat_voice_too_short)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_voice_too_short)
            )
            return
        }

        txtChatStatus.text = getString(R.string.chat_voice_ready)
        sendVoiceMessage(recordedFile, durationMs)
    }

    private fun sendVoiceMessage(
        audioFile: File,
        durationMs: Long
    ) {
        val packetId =
            "AUDIO-" + System.currentTimeMillis()
        val voiceLabel =
            buildVoiceLabel(durationMs)

        lifecycleScope.launch {
            btnSend.isEnabled = false
            btnAttach.isEnabled = false
            btnCamera.isEnabled = false
            btnVoice.isEnabled = false
            btnRetryFailed.isEnabled = false
            txtChatStatus.text = getString(R.string.chat_voice_sending)

            withContext(Dispatchers.IO) {
                chatDb.chatDao().insertMessage(
                    ChatMessage(
                        packetId = packetId,
                        chatId = peerName,
                        senderName = "ME",
                        content = voiceLabel,
                        contentType = "AUDIO",
                        filePath = audioFile.absolutePath,
                        isSent = true,
                        status = "SENDING"
                    )
                )
            }

            renderHistory()

            FileTransferManager.sendFile(
                context = this@ChatActivity,
                fileUri = android.net.Uri.fromFile(audioFile),
                destinationPeerId = peerName,
                keyStore = keyStore,
                myPeerId = MainActivity.myGlobalPeerId,
                listener =
                    object : FileTransferManager.TransferStatusListener {
                        override fun onProgress(
                            message: String,
                            busy: Boolean
                        ) {
                            runOnUiThread {
                                txtChatStatus.text =
                                    getString(R.string.chat_voice_sending)
                            }
                        }

                        override fun onComplete(message: String) {
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    chatDb.chatDao().updateStatus(
                                        packetId,
                                        "SENT"
                                    )
                                }

                                txtChatStatus.text =
                                    getString(R.string.chat_voice_sent)
                                renderHistory()
                                restoreChatButtons()
                            }
                        }

                        override fun onError(message: String) {
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    chatDb.chatDao().updateStatus(
                                        packetId,
                                        "FAILED"
                                    )
                                }

                                txtChatStatus.text =
                                    getString(R.string.chat_voice_failed)
                                UiFeedbackManager.showToast(
                                    this@ChatActivity,
                                    getString(R.string.chat_voice_failed)
                                )
                                renderHistory()
                                restoreChatButtons()
                            }
                        }
                    }
            )
        }
    }

    private fun playAudioMessage(message: ChatMessage) {
        val path =
            message.filePath

        if (path.isNullOrBlank() || !File(path).exists()) {
            txtChatStatus.text = getString(R.string.chat_voice_missing)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_voice_missing)
            )
            return
        }

        try {
            releasePlayer()
            chatAdapter.setPlayingMessage(message.packetId)

            mediaPlayer =
                MediaPlayer().apply {
                    setDataSource(path)
                    setOnCompletionListener {
                        if (!message.isSent) {
                            FileTransferManager.sendAudioStatusSignal(
                                context = this@ChatActivity,
                                targetPeerId = peerName,
                                packetType = "AUDIO_PLAYED",
                                referencePacketId = message.packetId
                            )
                        }
                        releasePlayer()
                        chatAdapter.setPlayingMessage(null)
                        txtChatStatus.text = getString(R.string.chat_idle)
                    }
                    prepare()
                    start()
                }

            txtChatStatus.text =
                "${getString(R.string.chat_voice_playing)} ${message.content}"
        } catch (e: Exception) {
            MeshLogger.e("CHAT", "Audio play failed", e)
            chatAdapter.setPlayingMessage(null)
            txtChatStatus.text = getString(R.string.chat_voice_failed)
        }
    }

    private fun handleMessageClick(message: ChatMessage) {
        when (message.contentType) {
            "AUDIO" -> playAudioMessage(message)
            "IMAGE" -> openImagePreview(message)
            "FILE" -> openSharedFile(message)
        }
    }

    private fun openImagePreview(message: ChatMessage) {
        val path = message.filePath
        if (path.isNullOrBlank() || !File(path).exists()) {
            txtChatStatus.text = getString(R.string.chat_file_missing)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_file_missing)
            )
            return
        }

        startActivity(
            ImageViewerActivity.createIntent(
                context = this,
                filePath = path,
                title = message.content
            )
        )
    }

    private fun openSharedFile(message: ChatMessage) {
        val path = message.filePath
        if (path.isNullOrBlank() || !File(path).exists()) {
            txtChatStatus.text = getString(R.string.chat_file_missing)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_file_missing)
            )
            return
        }

        try {
            val file = File(path)
            val uri =
                FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )

            val mimeType =
                MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase())
                    ?: "application/octet-stream"

            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

            startActivity(Intent.createChooser(intent, getString(R.string.chat_open_file)))
        } catch (e: Exception) {
            MeshLogger.e("CHAT", "Open file failed", e)
            txtChatStatus.text = getString(R.string.chat_open_file_failed)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_open_file_failed)
            )
        }
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }

        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }

        mediaPlayer = null
        chatAdapter.setPlayingMessage(null)
    }

    private fun startCallSession() {
        if (peerIp.isBlank()) {
            txtChatStatus.text = getString(R.string.peer_ip_empty)
            return
        }

        if (VoiceCallRegistry.isBusy()) {
            txtChatStatus.text = getString(R.string.call_busy_local)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.call_busy_local)
            )
            return
        }

        val callId =
            UUID.randomUUID().toString()

        startActivity(
            CallSessionActivity.createIntent(
                context = this,
                peerName = peerName,
                peerIp = peerIp,
                callId = callId,
                incoming = false
            )
        )
    }

    private fun sendCapturedPhoto(bitmap: Bitmap) {
        val photoDir =
            if (ChatMediaSettingsManager.shouldKeepCapturedPhotos(this)) {
                File(filesDir, "sent_media/camera_shots")
            } else {
                File(cacheDir, "camera_shots")
            }
        if (!photoDir.exists()) {
            photoDir.mkdirs()
        }

        val imageFile =
            File(photoDir, "photo_${System.currentTimeMillis()}.jpg")

        try {
            imageFile.outputStream().use { output ->
                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    ChatMediaSettingsManager.getPhotoQualityPercent(this),
                    output
                )
            }

            sendAttachment(
                fileUri = Uri.fromFile(imageFile),
                contentType = "IMAGE",
                displayName = imageFile.name
            )
        } catch (e: Exception) {
            MeshLogger.e("CHAT", "Camera photo save failed", e)
            txtChatStatus.text = getString(R.string.chat_camera_failed)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_camera_failed)
            )
        }
    }

    private fun sendAttachment(
        fileUri: Uri,
        contentType: String,
        displayName: String
    ) {
        if (peerIp.isBlank()) {
            txtChatStatus.text = getString(R.string.peer_ip_empty)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.peer_ip_empty)
            )
            return
        }

        val packetId =
            "FILE-" + System.currentTimeMillis()
        val localAttachment =
            cacheLocalAttachment(fileUri, displayName)

        if (localAttachment == null) {
            txtChatStatus.text = getString(R.string.chat_file_prepare_failed)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_file_prepare_failed)
            )
            return
        }

        val label =
            when (contentType) {
                "IMAGE" -> getString(R.string.chat_image_label_simple)
                else -> getString(R.string.chat_file_label, displayName)
            }

        lifecycleScope.launch {
            disableChatButtons()
            txtChatStatus.text =
                if (contentType == "IMAGE") {
                    getString(R.string.chat_image_sending)
                } else {
                    getString(R.string.chat_file_sending)
                }

            withContext(Dispatchers.IO) {
                        chatDb.chatDao().insertMessage(
                            ChatMessage(
                                packetId = packetId,
                                chatId = peerName,
                                senderName = "ME",
                                content = label,
                                contentType = contentType,
                                filePath = localAttachment.absolutePath,
                                isSent = true,
                                status = "SENDING"
                            )
                        )
                    }

            renderHistory()

            FileTransferManager.sendFile(
                context = this@ChatActivity,
                fileUri = Uri.fromFile(localAttachment),
                destinationPeerId = peerName,
                keyStore = keyStore,
                myPeerId = MainActivity.myGlobalPeerId,
                listener =
                    object : FileTransferManager.TransferStatusListener {
                        override fun onProgress(message: String, busy: Boolean) {
                            runOnUiThread {
                                txtChatStatus.text = message
                            }
                        }

                        override fun onComplete(message: String) {
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    chatDb.chatDao().updateStatus(packetId, "SENT")
                                }
                                txtChatStatus.text = message
                                renderHistory()
                                restoreChatButtons()
                            }
                        }

                        override fun onError(message: String) {
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    chatDb.chatDao().updateStatus(packetId, "FAILED")
                                }
                                txtChatStatus.text = message
                                UiFeedbackManager.showToast(
                                    this@ChatActivity,
                                    message
                                )
                                renderHistory()
                                restoreChatButtons()
                            }
                        }
                    }
            )
        }
    }

    private fun cacheLocalAttachment(
        fileUri: Uri,
        displayName: String
    ): File? {
        return try {
            val attachDir = File(cacheDir, "chat_attachments")
            if (!attachDir.exists()) {
                attachDir.mkdirs()
            }

            val safeName =
                displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val localFile =
                createUniqueAttachmentFile(attachDir, safeName)

            contentResolver.openInputStream(fileUri)?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            localFile
        } catch (e: Exception) {
            MeshLogger.e("CHAT", "Cache attachment failed", e)
            null
        }
    }

    private fun createUniqueAttachmentFile(
        directory: File,
        safeName: String
    ): File {
        val dotIndex = safeName.lastIndexOf('.')
        val baseName =
            if (dotIndex > 0) safeName.substring(0, dotIndex) else safeName
        val extension =
            if (dotIndex > 0) safeName.substring(dotIndex) else ""

        var candidate = File(directory, safeName)
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "${baseName}_$suffix$extension")
            suffix++
        }

        return candidate
    }

    private fun restoreChatButtons() {
        btnSend.isEnabled = true
        btnAttach.isEnabled = true
        btnCamera.isEnabled = true
        btnVoice.isEnabled = true
        btnRetryFailed.isEnabled = true
        btnSend.text = getString(R.string.send)
        btnVoice.text = getString(R.string.chat_voice_start)
    }

    private fun disableChatButtons() {
        btnSend.isEnabled = false
        btnAttach.isEnabled = false
        btnCamera.isEnabled = false
        btnVoice.isEnabled = false
        btnRetryFailed.isEnabled = false
    }

    private fun startRecordingStatusTicker() {
        recordingStatusJob?.cancel()
        recordingStatusJob =
            lifecycleScope.launch {
                while (isRecording) {
                    val elapsedMs =
                        System.currentTimeMillis() - recordStartAt

                    txtChatStatus.text =
                        "${getString(R.string.chat_recording)} ${formatDuration(elapsedMs)}"

                    delay(250)
                }
            }
    }

    private fun buildVoiceLabel(durationMs: Long): String {
        return "${getString(R.string.chat_voice_label)} (${formatDuration(durationMs)})"
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds =
            (durationMs / 1000L).coerceAtLeast(0L)

        val minutes =
            totalSeconds / 60L

        val seconds =
            totalSeconds % 60L

        return "%02d:%02d".format(minutes, seconds)
    }

    private fun sendMessage() {
        val message =
            edtMessage.text.toString().trim()

        if (message.isEmpty()) {
            UiFeedbackManager.showToast(
                this,
                getString(R.string.message_empty)
            )
            return
        }

        if (message.length > MAX_MESSAGE_LENGTH) {
            txtChatStatus.text = getString(R.string.message_too_long)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.message_too_long)
            )
            return
        }

        if (peerIp.isEmpty()) {
            txtChatStatus.text = getString(R.string.peer_ip_empty)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.peer_ip_empty)
            )
            return
        }

        try {
            val packetId =
                "CHAT-" + System.currentTimeMillis()

            lifecycleScope.launch {
                btnSend.isEnabled = false
                btnAttach.isEnabled = false
                btnCamera.isEnabled = false
                btnVoice.isEnabled = false
                btnRetryFailed.isEnabled = false
                btnSend.text = "SENDING..."
                txtChatStatus.text = getString(R.string.chat_sending)

                try {
                    withContext(Dispatchers.IO) {
                        chatDb.chatDao().insertMessage(
                            ChatMessage(
                                packetId = packetId,
                                chatId = peerName,
                                senderName = "ME",
                                content = message,
                                isSent = true,
                                status = "SENDING"
                            )
                        )
                    }

                    renderHistory()

                    val ok =
                        withContext(Dispatchers.IO) {
                            val securePayload =
                                buildChatPayload(message)

                            val packet =
                                MeshPacket(
                                    packetId = packetId,
                                    source = MainActivity.myGlobalPeerId,
                                    destination = peerName,
                                    type = "CHAT",
                                    payload = securePayload.payload,
                                    encrypted = securePayload.encrypted
                                )

                            ReliablePacketSender.sendWithRetry(
                                peerIp,
                                packet
                            )
                        }

                    withContext(Dispatchers.IO) {
                        chatDb.chatDao().updateStatus(
                            packetId,
                            if (ok) "SENT" else "FAILED"
                        )
                    }

                    if (!ok) {
                        txtChatStatus.text = getString(R.string.send_failed)
                        UiFeedbackManager.showToast(
                            this@ChatActivity,
                            getString(R.string.send_failed)
                        )
                    } else {
                        txtChatStatus.text = getString(R.string.chat_sent)
                    }

                    renderHistory()
                } catch (e: Exception) {
                    MeshLogger.e("CHAT", "Send failed", e)
                    txtChatStatus.text = getString(R.string.send_failed)
                    UiFeedbackManager.showToast(
                        this@ChatActivity,
                        getString(R.string.send_failed)
                    )
                } finally {
                    restoreChatButtons()
                    btnSend.text = getString(R.string.send)
                }
            }

            edtMessage.setText("")

            MeshLogger.i(
                "CHAT",
                "Message sent to $peerName"
            )

        } catch (e: Exception) {
            restoreChatButtons()
            btnSend.text = getString(R.string.send)
            txtChatStatus.text = getString(R.string.send_failed)
            MeshLogger.e(
                "CHAT",
                "Send failed",
                e
            )

            UiFeedbackManager.showToast(
                this,
                getString(R.string.send_failed)
            )
        }
    }

    private fun retryLastFailedMessage() {
        if (peerIp.isEmpty()) {
            txtChatStatus.text = getString(R.string.peer_ip_empty)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.peer_ip_empty)
            )
            return
        }

        lifecycleScope.launch {
            btnSend.isEnabled = false
            btnAttach.isEnabled = false
            btnCamera.isEnabled = false
            btnVoice.isEnabled = false
            btnRetryFailed.isEnabled = false
            btnRetryFailed.text = "RETRYING..."
            txtChatStatus.text = getString(R.string.chat_retrying)

            try {
                val failedMessage =
                    withContext(Dispatchers.IO) {
                        chatDb.chatDao().getLastFailedMessage(peerName)
                    }

                if (failedMessage == null) {
                    UiFeedbackManager.showToast(
                        this@ChatActivity,
                        getString(R.string.no_failed_message)
                    )
                    txtChatStatus.text = getString(R.string.no_failed_message)
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    chatDb.chatDao().updateStatus(
                        failedMessage.packetId,
                        "SENDING"
                    )
                }

                renderHistory()

                val ok =
                    withContext(Dispatchers.IO) {
                        val securePayload =
                            buildChatPayload(failedMessage.content)

                        val packet =
                            MeshPacket(
                                packetId = failedMessage.packetId,
                                source = MainActivity.myGlobalPeerId,
                                destination = peerName,
                                type = "CHAT",
                                payload = securePayload.payload,
                                encrypted = securePayload.encrypted
                            )

                        ReliablePacketSender.sendWithRetry(
                            peerIp,
                            packet
                        )
                    }

                withContext(Dispatchers.IO) {
                    chatDb.chatDao().updateStatus(
                        failedMessage.packetId,
                        if (ok) "SENT" else "FAILED"
                    )
                }

                if (!ok) {
                    txtChatStatus.text = getString(R.string.send_failed)
                    UiFeedbackManager.showToast(
                        this@ChatActivity,
                        getString(R.string.send_failed)
                    )
                } else {
                    txtChatStatus.text = getString(R.string.chat_sent)
                }

                renderHistory()
            } catch (e: Exception) {
                MeshLogger.e("CHAT", "Retry failed", e)
                txtChatStatus.text = getString(R.string.send_failed)
                UiFeedbackManager.showToast(
                    this@ChatActivity,
                    getString(R.string.send_failed)
                )
            } finally {
                restoreChatButtons()
                btnRetryFailed.text = getString(R.string.retry_failed)
            }
        }
    }

    private fun resolveAttachmentContentType(uri: Uri): String {
        val mimeType =
            contentResolver.getType(uri).orEmpty()

        return if (mimeType.startsWith("image/")) {
            "IMAGE"
        } else {
            "FILE"
        }
    }

    private fun readDisplayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index) ?: "file"
            }
        }

        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    private fun showMessageActions(message: ChatMessage) {
        val items =
            buildList {
                add(getString(R.string.chat_action_share))
                if (message.contentType == "AUDIO" ||
                    message.contentType == "IMAGE" ||
                    message.contentType == "FILE"
                ) {
                    add(getString(R.string.chat_action_save))
                }
            }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.chat_action_title))
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    getString(R.string.chat_action_share) ->
                        shareMessage(message)

                    getString(R.string.chat_action_save) ->
                        saveMessageToDevice(message)
                }
            }
            .show()
    }

    private fun shareMessage(message: ChatMessage) {
        when (message.contentType) {
            "AUDIO", "IMAGE", "FILE" -> shareFileMessage(message)
            else -> shareTextMessage(message)
        }
    }

    private fun shareTextMessage(message: ChatMessage) {
        val text =
            "${message.senderName}: ${message.content}"

        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                getString(R.string.chat_share_via)
            )
        )
    }

    private fun shareFileMessage(message: ChatMessage) {
        val file = resolveMessageFile(message) ?: run {
            txtChatStatus.text = getString(R.string.chat_file_missing)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_file_missing)
            )
            return
        }

        try {
            val uri =
                FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )

            val mimeType =
                resolveMessageMimeType(message, file)

            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, message.content)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    getString(R.string.chat_share_via)
                )
            )
        } catch (e: Exception) {
            MeshLogger.e("CHAT", "Share file failed", e)
            txtChatStatus.text = getString(R.string.chat_share_failed)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_share_failed)
            )
        }
    }

    private fun saveMessageToDevice(message: ChatMessage) {
        val file = resolveMessageFile(message) ?: run {
            txtChatStatus.text = getString(R.string.chat_file_missing)
            UiFeedbackManager.showToast(
                this,
                getString(R.string.chat_file_missing)
            )
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val result =
                runCatching {
                    saveFileToMediaStore(message, file)
                }

            runOnUiThread {
                if (result.isSuccess) {
                    txtChatStatus.text = getString(R.string.chat_saved_success)
                    UiFeedbackManager.showToast(
                        this@ChatActivity,
                        getString(R.string.chat_saved_success)
                    )
                } else {
                    txtChatStatus.text = getString(R.string.chat_saved_failed)
                    UiFeedbackManager.showToast(
                        this@ChatActivity,
                        getString(R.string.chat_saved_failed)
                    )
                }
            }
        }
    }

    private fun saveFileToMediaStore(
        message: ChatMessage,
        file: File
    ) {
        val mimeType =
            resolveMessageMimeType(message, file)
        val resolver = contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, resolveRelativePath(message))
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

        val collection =
            when (message.contentType) {
                "IMAGE" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "AUDIO" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }

        val targetUri =
            resolver.insert(collection, values)
                ?: throw IllegalStateException("Insert media store failed")

        resolver.openOutputStream(targetUri)?.use { output ->
            FileInputStream(file).use { input ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Open output stream failed")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(targetUri, values, null, null)
        }
    }

    private fun resolveRelativePath(message: ChatMessage): String {
        return when (message.contentType) {
            "IMAGE" -> Environment.DIRECTORY_PICTURES + "/GhalbitMesh"
            "AUDIO" -> Environment.DIRECTORY_MUSIC + "/GhalbitMesh"
            else -> Environment.DIRECTORY_DOWNLOADS + "/GhalbitMesh"
        }
    }

    private fun resolveMessageFile(message: ChatMessage): File? {
        val path = message.filePath ?: return null
        val file = File(path)
        return file.takeIf { it.exists() }
    }

    private fun resolveMessageMimeType(
        message: ChatMessage,
        file: File
    ): String {
        return when (message.contentType) {
            "IMAGE" -> "image/jpeg"
            "AUDIO" -> "audio/mp4"
            else -> {
                MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase())
                    ?: "application/octet-stream"
            }
        }
    }

    private fun buildChatPayload(
        message: String
    ): ChatPayload {
        val plainPayload =
            PacketTtlManager.attachTtl(message)

        val peerPublicKey =
            keyStore.getPeerKey(peerName)

        if (peerPublicKey.isNullOrBlank()) {
            return ChatPayload(
                payload = plainPayload,
                encrypted = false
            )
        }

        return try {
            val sharedSecret =
                CryptoEngine.deriveSharedSecret(
                    keyStore.privateKey,
                    CryptoEngine.base64ToPublicKey(peerPublicKey)
                )

            val encryptedBytes =
                CryptoEngine.encrypt(
                    plainPayload.toByteArray(),
                    sharedSecret
                )

            ChatPayload(
                payload = Base64.encodeToString(
                    encryptedBytes,
                    Base64.NO_WRAP
                ),
                encrypted = true
            )
        } catch (e: Exception) {
            MeshLogger.e(
                "CHAT",
                "Encryption failed; sending plaintext fallback",
                e
            )

            ChatPayload(
                payload = plainPayload,
                encrypted = false
            )
        }
    }

    private suspend fun renderHistory(
        systemLine: String? = null
    ) {
        val messages =
            withContext(Dispatchers.IO) {
                chatDb.chatDao().getMessages(peerName)
            }
        ChatReadStateManager.markChatViewed(
            this@ChatActivity,
            peerName,
            messages.lastOrNull()?.timestamp ?: System.currentTimeMillis()
        )

        txtChat.text =
            buildChatHeader(systemLine)

        chatAdapter.submitMessages(messages)

        if (messages.isNotEmpty()) {
            rvMessages.scrollToPosition(messages.lastIndex)
        }
    }

    private data class ChatPayload(
        val payload: String,
        val encrypted: Boolean
    )

    private fun buildChatHeader(
        systemLine: String? = null
    ): String {
        val displayName =
            ContactAliasManager.getDisplayName(this, peerName)

        return buildString {
            append("Chat with ")
            append(displayName)
            if (displayName != peerName) {
                append("\nNode: ")
                append(peerName)
            }
            append("\nIP: ")
            append(peerIp.ifBlank { "-" })
            if (!systemLine.isNullOrBlank()) {
                append("\n")
                append(systemLine)
            }
        }
    }

    private fun showSaveContactDialog() {
        val input =
            EditText(this).apply {
                setText(ContactAliasManager.getAlias(this@ChatActivity, peerName).orEmpty())
                hint = getString(R.string.contact_alias_hint)
                setSelection(text.length)
            }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.contact_save_dialog_title))
            .setView(input)
            .setPositiveButton(R.string.contact_save_button) { _, _ ->
                ContactAliasManager.saveAlias(
                    this,
                    peerName,
                    input.text?.toString().orEmpty()
                )
                txtChat.text = buildChatHeader()
                UiFeedbackManager.showToast(
                    this,
                    getString(
                        R.string.contact_saved_message,
                        ContactAliasManager.getDisplayName(this, peerName)
                    )
                )
            }
            .setNeutralButton(R.string.contact_remove_button) { _, _ ->
                ContactAliasManager.removeAlias(this, peerName)
                txtChat.text = buildChatHeader()
                UiFeedbackManager.showToast(
                    this,
                    getString(R.string.contact_removed_message, peerName)
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
