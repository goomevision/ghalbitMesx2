package com.ghalbitnet.meshx2.file

import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.util.Log
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.chat.ChatDatabase
import com.ghalbitnet.meshx2.chat.ChatMessage
import com.ghalbitnet.meshx2.chat.ChatActivity
import com.ghalbitnet.meshx2.core.utils.AppNotificationManager
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.network.MeshSocketClient
import com.ghalbitnet.meshx2.network.MeshTrafficGuard
import com.ghalbitnet.meshx2.security.KeyStoreManager
import com.ghalbitnet.meshx2.settings.ChatMediaSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object FileTransferManager {
    const val ACTION_TRANSFER_STATUS = "com.ghalbitnet.meshx2.FILE_TRANSFER_STATUS"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_BUSY = "busy"
    const val ACTION_AUDIO_MESSAGE_RECEIVED = "com.ghalbitnet.meshx2.AUDIO_MESSAGE_RECEIVED"
    const val ACTION_ATTACHMENT_MESSAGE_RECEIVED = "com.ghalbitnet.meshx2.ATTACHMENT_MESSAGE_RECEIVED"
    const val EXTRA_AUDIO_SOURCE = "audio_source"
    const val EXTRA_AUDIO_PACKET_ID = "audio_packet_id"
    const val EXTRA_AUDIO_FILE_PATH = "audio_file_path"
    const val EXTRA_AUDIO_LABEL = "audio_label"
    const val EXTRA_ATTACHMENT_SOURCE = "attachment_source"
    const val EXTRA_ATTACHMENT_PACKET_ID = "attachment_packet_id"
    const val EXTRA_ATTACHMENT_FILE_PATH = "attachment_file_path"
    const val EXTRA_ATTACHMENT_LABEL = "attachment_label"
    const val EXTRA_ATTACHMENT_CONTENT_TYPE = "attachment_content_type"

    private const val CHUNK_SIZE = 64 * 1024 // 64 KB
    private const val CHUNK_SEND_DELAY_MS = 75L
    private const val PROGRESS_EVERY_CHUNKS = 3
    private val sendingTransfer = AtomicBoolean(false)
    private val receiveChunks = ConcurrentHashMap<String, MutableSet<Int>>()
    private val receiveFileNames = ConcurrentHashMap<String, String>()
    private val receiveTotalChunks = ConcurrentHashMap<String, Int>()
    private val receiveTempFiles = ConcurrentHashMap<String, File>()

    interface TransferStatusListener {
        fun onProgress(message: String, busy: Boolean = true)
        fun onComplete(message: String)
        fun onError(message: String)
    }

    fun sendFile(
        context: Context,
        fileUri: Uri,
        destinationPeerId: String,
        keyStore: KeyStoreManager,
        myPeerId: String,
        listener: TransferStatusListener? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            if (!sendingTransfer.compareAndSet(false, true)) {
                listener?.onError("Transfer lain masih berjalan. Tunggu sebentar.")
                return@launch
            }

            try {
                val destIp = keyStore.getPeerAddress(destinationPeerId)
                if (destIp.isNullOrBlank()) {
                    listener?.onError("Alamat tujuan belum tersedia.")
                    Log.e("GHALBIT", "No IP for file transfer to $destinationPeerId")
                    return@launch
                }

                val fileName = getDisplayName(context, fileUri)
                val fileSize = getFileSize(context, fileUri)
                val mimeType =
                    resolveMimeType(context, fileUri, fileName)
                val maxAllowedSize =
                    maxAllowedSizeFor(context, mimeType)
                val fileGuard =
                    MeshTrafficGuard.validateFile(fileName, mimeType)

                if (!fileGuard.allowed) {
                    listener?.onError(fileGuard.reason)
                    return@launch
                }

                if (fileSize <= 0L) {
                    listener?.onError("Ukuran file belum dapat dibaca.")
                    return@launch
                }

                if (fileSize > maxAllowedSize) {
                    listener?.onError(
                        "File terlalu besar untuk jaringan mesh. Batas ${formatSize(maxAllowedSize)}."
                    )
                    return@launch
                }

                val totalChunks =
                    ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
                val transferId = UUID.randomUUID().toString()
                val buffer = ByteArray(CHUNK_SIZE)

                context.contentResolver.openInputStream(fileUri).use { inputStream ->
                    if (inputStream == null) {
                        listener?.onError("File tidak dapat dibuka.")
                        return@launch
                    }

                    var chunkIndex = 0
                    var read = inputStream.read(buffer)

                    while (read > 0) {
                        val chunk =
                            if (read == buffer.size) {
                                buffer.copyOf()
                            } else {
                                buffer.copyOf(read)
                            }

                        val chunkData = Base64.encodeToString(chunk, Base64.NO_WRAP)
                        val jsonPayload = org.json.JSONObject().apply {
                            put("transferId", transferId)
                            put("fileName", fileName)
                            put("mimeType", mimeType)
                            put("fileSize", fileSize)
                            put("chunkIndex", chunkIndex)
                            put("totalChunks", totalChunks)
                            put("data", chunkData)
                        }.toString()

                        val packet = MeshPacket(
                            packetId = UUID.randomUUID().toString(),
                            source = myPeerId,
                            destination = destinationPeerId,
                            type = "FILE_CHUNK",
                            payload = jsonPayload,
                            hopCount = 0,
                            maxHop = 5,
                            timestamp = System.currentTimeMillis(),
                            encrypted = false
                        )

                        MeshSocketClient.send(destIp, packet)
                        if (chunkIndex == 0 || chunkIndex % PROGRESS_EVERY_CHUNKS == 0) {
                            listener?.onProgress("Mengirim file ${chunkIndex + 1}/$totalChunks")
                        }
                        Log.d("GHALBIT", "Sent chunk $chunkIndex/$totalChunks to $destinationPeerId")

                        chunkIndex++
                        delay(CHUNK_SEND_DELAY_MS)
                        read = inputStream.read(buffer)
                    }
                }

                listener?.onComplete("Transfer file selesai dikirim.")
            } catch (e: Exception) {
                listener?.onError("Transfer file gagal.")
                Log.e("GHALBIT", "File send failed", e)
            } finally {
                sendingTransfer.set(false)
            }
        }
    }

    fun handleFileChunk(context: Context, packet: MeshPacket) {
        try {
            val json = org.json.JSONObject(packet.payload)
            val transferId = json.getString("transferId")
            val fileName = json.getString("fileName")
            val mimeType = json.optString("mimeType", "application/octet-stream")
            val fileSize = json.optLong("fileSize", -1L)
            val chunkIndex = json.getInt("chunkIndex")
            val totalChunks = json.getInt("totalChunks")
            val data = Base64.decode(json.getString("data"), Base64.NO_WRAP)

            if (totalChunks <= 0 || chunkIndex !in 0 until totalChunks) {
                Log.e("GHALBIT", "Invalid file chunk index")
                return
            }

            val fileGuard =
                MeshTrafficGuard.validateFile(fileName, mimeType)

            if (!fileGuard.allowed) {
                Log.e("GHALBIT", "Incoming file rejected: ${fileGuard.reason}")
                broadcastStatus(
                    context,
                    fileGuard.reason,
                    false
                )
                clearReceiveState(transferId)
                return
            }

            val maxAllowedSize = maxAllowedSizeFor(context, mimeType)
            val estimatedSize = totalChunks.toLong() * CHUNK_SIZE

            if (
                estimatedSize > maxAllowedSize + CHUNK_SIZE ||
                (fileSize > 0L && fileSize > maxAllowedSize)
            ) {
                Log.e("GHALBIT", "Incoming file rejected by mesh traffic policy")
                broadcastStatus(
                    context,
                    "File masuk ditolak karena terlalu besar.",
                    false
                )
                clearReceiveState(transferId)
                return
            }

            receiveFileNames[transferId] = fileName
            receiveTotalChunks[transferId] = totalChunks
            val chunks =
                receiveChunks.getOrPut(transferId) {
                    ConcurrentHashMap.newKeySet()
                }

            val tempFile =
                receiveTempFiles.getOrPut(transferId) {
                    createReceiveFile(context, "tmp_${transferId}_${safeFileName(fileName)}")
                }

            RandomAccessFile(tempFile, "rw").use { file ->
                file.seek(chunkIndex.toLong() * CHUNK_SIZE)
                file.write(data)
            }

            chunks.add(chunkIndex)
            if (chunkIndex == 0 || chunkIndex % PROGRESS_EVERY_CHUNKS == 0) {
                broadcastStatus(
                    context,
                    "Menerima file ${chunks.size}/$totalChunks: $fileName",
                    true
                )
            }

            if (chunks.size == totalChunks) {
                val finalFile =
                    createUniqueReceiveFile(context, safeFileName(fileName))

                tempFile.renameTo(finalFile)
                handleCompletedIncomingFile(
                    context = context,
                    packet = packet,
                    fileName = fileName,
                    mimeType = mimeType,
                    filePath = finalFile.absolutePath,
                    transferId = transferId
                )
                clearReceiveState(transferId)
                broadcastStatus(
                    context,
                    "File diterima: ${finalFile.name}",
                    false
                )
                Log.d("GHALBIT", "File received: ${finalFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("GHALBIT", "File chunk error", e)
        }
    }

    private fun getDisplayName(
        context: Context,
        uri: Uri
    ): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex) ?: "file.bin"
            }
        }

        return uri.lastPathSegment?.substringAfterLast('/') ?: "file.bin"
    }

    private fun getFileSize(
        context: Context,
        uri: Uri
    ): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst()) {
                val size = cursor.getLong(sizeIndex)
                if (size > 0L) {
                    return size
                }
            }
        }

        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
            it.length
        } ?: -1L
    }

    private fun maxAllowedSizeFor(context: Context, mimeType: String): Long =
        ChatMediaSettingsManager.maxAllowedSizeFor(context, mimeType)

    private fun resolveMimeType(
        context: Context,
        fileUri: Uri,
        fileName: String
    ): String {
        val directType =
            context.contentResolver.getType(fileUri)
        if (!directType.isNullOrBlank() && directType != "application/octet-stream") {
            return directType
        }

        val extension =
            fileName.substringAfterLast('.', "").lowercase()
        if (extension.isNotBlank()) {
            val mappedType =
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (!mappedType.isNullOrBlank()) {
                return mappedType
            }
        }

        return "application/octet-stream"
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / 1024L / 1024L
        return "$mb MB"
    }

    private fun createReceiveFile(
        context: Context,
        fileName: String
    ): File {
        val receiveDir = File(context.filesDir, "mesh_received")
        if (!receiveDir.exists()) {
            receiveDir.mkdirs()
        }

        return File(receiveDir, fileName)
    }

    private fun createUniqueReceiveFile(
        context: Context,
        fileName: String
    ): File {
        val baseFile = createReceiveFile(context, fileName)
        if (!baseFile.exists()) {
            return baseFile
        }

        val name = baseFile.nameWithoutExtension
        val extension =
            baseFile.extension
                .takeIf { it.isNotBlank() }
                ?.let { ".$it" }
                ?: ""

        var index = 1
        var candidate: File
        do {
            candidate = createReceiveFile(context, "${name}_$index$extension")
            index++
        } while (candidate.exists())

        return candidate
    }

    private fun safeFileName(fileName: String): String {
        return fileName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "file.bin" }
    }

    private fun clearReceiveState(transferId: String) {
        receiveChunks.remove(transferId)
        receiveFileNames.remove(transferId)
        receiveTotalChunks.remove(transferId)
        receiveTempFiles.remove(transferId)?.delete()
    }

    private fun broadcastStatus(
        context: Context,
        message: String,
        busy: Boolean
    ) {
        val intent =
            Intent(ACTION_TRANSFER_STATUS)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_BUSY, busy)

        LocalBroadcastManager
            .getInstance(context)
            .sendBroadcast(intent)
    }

    private fun handleCompletedIncomingFile(
        context: Context,
        packet: MeshPacket,
        fileName: String,
        mimeType: String,
        filePath: String,
        transferId: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val chatDb =
                ChatDatabase.getInstance(context)

            if (chatDb.chatDao().countByPacketId(transferId) > 0) {
                return@launch
            }

            val contentType =
                when {
                    mimeType.startsWith("audio/") -> "AUDIO"
                    mimeType.startsWith("image/") -> "IMAGE"
                    else -> "FILE"
                }

            val label =
                when (contentType) {
                    "AUDIO" -> buildIncomingAudioLabel(context, filePath)
                    "IMAGE" -> buildIncomingImageLabel()
                    else -> buildIncomingFileLabel(fileName)
                }

            chatDb.chatDao().insertMessage(
                ChatMessage(
                    packetId = transferId,
                    chatId = packet.source,
                    senderName = packet.source,
                    content = label,
                    contentType = contentType,
                    filePath = filePath,
                    isSent = false,
                    status = "RECEIVED"
                )
            )

            if (ChatMediaSettingsManager.shouldAutoSaveIncomingMedia(context)) {
                saveIncomingMediaToDevice(
                    context = context,
                    filePath = filePath,
                    fileName = fileName,
                    mimeType = mimeType,
                    contentType = contentType
                )
            }

            if (contentType == "AUDIO") {
                sendAudioStatusSignal(
                    context = context,
                    targetPeerId = packet.source,
                    packetType = "AUDIO_RECEIVED",
                    referencePacketId = transferId
                )

                broadcastIncomingAudio(
                    context = context,
                    source = packet.source,
                    packetId = transferId,
                    filePath = filePath,
                    label = label
                )

                if (!ChatActivity.isViewingChatWith(packet.source)) {
                    AppNotificationManager.notifyChatMessage(
                        context = context,
                        peerName = packet.source,
                        message = label,
                        isSilent = true
                    )
                }
            } else {
                broadcastIncomingAttachment(
                    context = context,
                    source = packet.source,
                    packetId = transferId,
                    filePath = filePath,
                    label = label,
                    contentType = contentType
                )

                if (!ChatActivity.isViewingChatWith(packet.source)) {
                    AppNotificationManager.notifyChatMessage(
                        context = context,
                        peerName = packet.source,
                        message = label,
                        isSilent = true
                    )
                }
            }
        }
    }

    private fun broadcastIncomingAudio(
        context: Context,
        source: String,
        packetId: String,
        filePath: String,
        label: String
    ) {
        val intent =
            Intent(ACTION_AUDIO_MESSAGE_RECEIVED)
                .putExtra(EXTRA_AUDIO_SOURCE, source)
                .putExtra(EXTRA_AUDIO_PACKET_ID, packetId)
                .putExtra(EXTRA_AUDIO_FILE_PATH, filePath)
                .putExtra(EXTRA_AUDIO_LABEL, label)

        LocalBroadcastManager
            .getInstance(context)
            .sendBroadcast(intent)
    }

    private fun broadcastIncomingAttachment(
        context: Context,
        source: String,
        packetId: String,
        filePath: String,
        label: String,
        contentType: String
    ) {
        val intent =
            Intent(ACTION_ATTACHMENT_MESSAGE_RECEIVED)
                .putExtra(EXTRA_ATTACHMENT_SOURCE, source)
                .putExtra(EXTRA_ATTACHMENT_PACKET_ID, packetId)
                .putExtra(EXTRA_ATTACHMENT_FILE_PATH, filePath)
                .putExtra(EXTRA_ATTACHMENT_LABEL, label)
                .putExtra(EXTRA_ATTACHMENT_CONTENT_TYPE, contentType)

        LocalBroadcastManager
            .getInstance(context)
            .sendBroadcast(intent)
    }

    fun sendAudioStatusSignal(
        context: Context,
        targetPeerId: String,
        packetType: String,
        referencePacketId: String
    ) {
        try {
            val keyStore =
                KeyStoreManager(context)

            val targetIp =
                keyStore.getPeerAddress(targetPeerId)
                    ?: return

            val packet =
                MeshPacket(
                    packetId = "$packetType-${System.currentTimeMillis()}",
                    source = MainActivity.myGlobalPeerId.ifBlank { "LOCAL" },
                    destination = targetPeerId,
                    type = packetType,
                    payload = referencePacketId,
                    encrypted = false
                )

            MeshSocketClient.send(
                targetIp,
                packet
            )
        } catch (e: Exception) {
            Log.e("GHALBIT", "Audio status signal failed", e)
        }
    }

    private fun buildIncomingAudioLabel(
        context: Context,
        filePath: String
    ): String {
        val durationMs =
            readAudioDuration(filePath)

        return if (durationMs > 0L) {
            "${context.getString(com.ghalbitnet.meshx2.R.string.chat_voice_label)} (${formatAudioDuration(durationMs)})"
        } else {
            context.getString(com.ghalbitnet.meshx2.R.string.chat_voice_label)
        }
    }

    private fun buildIncomingImageLabel(): String {
        return "Foto"
    }

    private fun buildIncomingFileLabel(fileName: String): String {
        return "[File] $fileName"
    }

    private fun saveIncomingMediaToDevice(
        context: Context,
        filePath: String,
        fileName: String,
        mimeType: String,
        contentType: String
    ) {
        runCatching {
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) {
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveIncomingMediaWithMediaStore(
                    context = context,
                    sourceFile = sourceFile,
                    fileName = fileName,
                    mimeType = mimeType,
                    contentType = contentType
                )
            } else {
                saveIncomingMediaLegacy(
                    sourceFile = sourceFile,
                    fileName = fileName,
                    contentType = contentType
                )
            }
        }.onFailure {
            Log.e("GHALBIT", "Auto-save incoming media failed", it)
        }
    }

    private fun saveIncomingMediaWithMediaStore(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String,
        contentType: String
    ) {
        val collection =
            when (contentType) {
                "IMAGE" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "AUDIO" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }
        val relativePath =
            when (contentType) {
                "IMAGE" -> Environment.DIRECTORY_PICTURES + "/GhalbitMesh"
                "AUDIO" -> Environment.DIRECTORY_MUSIC + "/GhalbitMesh"
                else -> Environment.DIRECTORY_DOWNLOADS + "/GhalbitMesh"
            }

        val uri =
            context.contentResolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
            ) ?: return

        context.contentResolver.openOutputStream(uri)?.use { output ->
            FileInputStream(sourceFile).use { input ->
                input.copyTo(output)
            }
        }
    }

    private fun saveIncomingMediaLegacy(
        sourceFile: File,
        fileName: String,
        contentType: String
    ) {
        val parent =
            when (contentType) {
                "IMAGE" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                "AUDIO" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                else -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
        val targetDir = File(parent, "GhalbitMesh")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val targetFile = File(targetDir, fileName)
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun readAudioDuration(filePath: String): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val raw =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )
            retriever.release()
            raw?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun formatAudioDuration(durationMs: Long): String {
        val totalSeconds =
            (durationMs / 1000L).coerceAtLeast(0L)
        val minutes =
            totalSeconds / 60L
        val seconds =
            totalSeconds % 60L

        return "%02d:%02d".format(minutes, seconds)
    }
}
