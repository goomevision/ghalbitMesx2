package com.ghalbitnet.meshx2.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.ghalbitnet.meshx2.security.NodeSigningIdentityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.min

object RemoteMediaRelayManager {
    private const val MEDIA_EXPIRY_MS = 24 * 60 * 60 * 1000L

    suspend fun uploadPendingMedia(
        context: Context,
        pending: PendingMessage
    ): RelayMediaUploadResult = withContext(Dispatchers.IO) {
        val filePath = pending.mediaUri ?: return@withContext RelayMediaUploadResult(false, "MEDIA_FAILED_FINAL", "", pending.messageId, pending.packetId, error = "missing_file")
        val file = File(filePath)
        if (!file.exists()) {
            return@withContext RelayMediaUploadResult(false, "MEDIA_FAILED_FINAL", "", pending.messageId, pending.packetId, error = "missing_file")
        }
        val targetGlobalId = pending.targetGlobalId.orEmpty()
        if (targetGlobalId.isBlank()) {
            return@withContext RelayMediaUploadResult(false, "WAITING_FOR_PEER", "", pending.messageId, pending.packetId, error = "missing_target_global_id")
        }
        val relayUrl = RelayRegistryManager.current(context)?.url ?: OnlineFallbackTransport.relayBaseUrl()
        if (relayUrl.isBlank()) {
            return@withContext RelayMediaUploadResult(false, "INTERNET_RELAY_NOT_CONFIGURED", "", pending.messageId, pending.packetId, error = "missing_relay")
        }

        val checksum = pending.mediaChecksum ?: file.sha256()
        val chunkSize = preferredChunkSize(context)
        val chunkCount = ((file.length() + chunkSize - 1) / chunkSize).toInt()
        val init = initUpload(context, relayUrl, pending, file, checksum, chunkCount, chunkSize)
        if (!init.successful) {
            return@withContext RelayMediaUploadResult(false, init.status, init.mediaId, pending.messageId, pending.packetId, init.secureMediaToken, init.expiresAt, init.error)
        }
        PendingMessageStore.upsert(
            context,
            pending.copy(
                mediaChecksum = checksum,
                chunkCount = chunkCount,
                uploadSessionId = init.uploadSessionId,
                remoteMediaId = init.mediaId,
                secureMediaToken = init.secureMediaToken,
                uploadState = "MEDIA_UPLOADING"
            )
        )

        val uploadedChunks = init.uploadedChunks.toMutableSet()
        RandomAccessFile(file, "r").use { raf ->
            var chunkIndex = 0
            while (chunkIndex < chunkCount) {
                if (uploadedChunks.contains(chunkIndex)) {
                    chunkIndex++
                    continue
                }
                val offset = chunkIndex.toLong() * chunkSize
                val remaining = file.length() - offset
                val bytesToRead = min(chunkSize.toLong(), remaining).toInt()
                val buffer = ByteArray(bytesToRead)
                raf.seek(offset)
                raf.readFully(buffer)
                val uploaded = uploadChunk(relayUrl, init.uploadSessionId, init.secureMediaToken, init.mediaId, chunkIndex, chunkCount, buffer)
                if (!uploaded) {
                    Log.w("GHALBIT-MEDIA", "chunk retry mediaId=${init.mediaId} chunk=$chunkIndex")
                    return@withContext RelayMediaUploadResult(false, "MEDIA_RESUMING", init.mediaId, pending.messageId, pending.packetId, init.secureMediaToken, init.expiresAt, "chunk_failed")
                }
                uploadedChunks += chunkIndex
                Log.d("GHALBIT-MEDIA", "chunk upload mediaId=${init.mediaId} chunk=$chunkIndex/$chunkCount")
                PendingMessageStore.upsert(
                    context,
                    pending.copy(
                        mediaChecksum = checksum,
                        chunkCount = chunkCount,
                        uploadedChunks = uploadedChunks,
                        uploadSessionId = init.uploadSessionId,
                        remoteMediaId = init.mediaId,
                        secureMediaToken = init.secureMediaToken,
                        uploadState = "MEDIA_RESUMING"
                    )
                )
                chunkIndex++
            }
        }

        val completed = completeUpload(relayUrl, init.uploadSessionId, init.secureMediaToken)
        if (completed.successful) {
            Log.d("GHALBIT-MEDIA", "upload complete mediaId=${completed.mediaId}")
        }
        completed
    }

    private suspend fun initUpload(
        context: Context,
        relayUrl: String,
        pending: PendingMessage,
        file: File,
        checksum: String,
        chunkCount: Int,
        chunkSize: Int
    ): RelayMediaInitResult {
        val now = System.currentTimeMillis()
        val identity = NodeSigningIdentityManager.getOrCreate(context)
        val proof =
            RelaySecurityProof.Payload(
                senderGlobalId = identity.globalId,
                targetGlobalId = pending.targetGlobalId.orEmpty(),
                messageId = pending.messageId,
                packetId = pending.packetId,
                createdAt = now,
                expiresAt = pending.expiresAt.takeIf { it > 0L } ?: (now + MEDIA_EXPIRY_MS),
                nonce = RelaySecurityProof.nonce(),
                contentType = "MEDIA_INIT",
                payload = "${file.name}:${file.length()}:$checksum",
                senderPublicKey = identity.publicKeyBase64
            )
        val body =
            JSONObject()
                .put("senderGlobalId", identity.globalId)
                .put("senderNodeId", identity.nodeId)
                .put("publicKeyHash", identity.publicKeyHash)
                .put("senderPublicKey", identity.publicKeyBase64)
                .put("targetGlobalId", pending.targetGlobalId)
                .put("targetNodeId", pending.targetNodeId)
                .put("messageId", pending.messageId)
                .put("packetId", pending.packetId)
                .put("createdAt", proof.createdAt)
                .put("expiresAt", proof.expiresAt)
                .put("nonce", proof.nonce)
                .put("contentType", proof.contentType)
                .put("payload", proof.payload)
                .put("signature", NodeSigningIdentityManager.sign(context, RelaySecurityProof.canonical(proof), proof.messageId))
                .put("algorithm", proof.algorithm)
                .put("fileName", file.name)
                .put("mimeType", pending.mimeType ?: "application/octet-stream")
                .put("fileSize", file.length())
                .put("mediaChecksum", checksum)
                .put("chunkCount", chunkCount)
                .put("chunkSize", chunkSize)
                .put("uploadSessionId", pending.uploadSessionId)
                .put("mediaId", pending.remoteMediaId)
                .put("secureMediaToken", pending.secureMediaToken)
        val response = postJson("$relayUrl/relay/media/upload/init", body.toString())
        return RelayMediaInitResult(
            successful = response.optBoolean("ok"),
            status = response.optString("status", if (response.optBoolean("ok")) "MEDIA_UPLOADING" else "FAILED"),
            uploadSessionId = response.optString("uploadSessionId"),
            mediaId = response.optString("mediaId"),
            chunkSize = response.optInt("chunkSize", chunkSize),
            uploadedChunks = response.optJSONArray("uploadedChunks").toIntSet(),
            secureMediaToken = response.optString("secureMediaToken"),
            expiresAt = response.optLong("expiresAt", proof.expiresAt),
            error = response.optString("error").ifBlank { null }
        )
    }

    private suspend fun uploadChunk(
        relayUrl: String,
        uploadSessionId: String,
        secureMediaToken: String,
        mediaId: String,
        chunkIndex: Int,
        chunkCount: Int,
        data: ByteArray
    ): Boolean {
        repeat(3) { attempt ->
            val body =
                JSONObject()
                    .put("uploadSessionId", uploadSessionId)
                    .put("secureMediaToken", secureMediaToken)
                    .put("mediaId", mediaId)
                    .put("chunkIndex", chunkIndex)
                    .put("totalChunks", chunkCount)
                    .put("data", Base64.encodeToString(data, Base64.NO_WRAP))
            val response = postJson("$relayUrl/relay/media/upload/chunk", body.toString())
            if (response.optBoolean("ok")) {
                return true
            }
            Log.w("GHALBIT-MEDIA", "chunk retry mediaId=$mediaId chunk=$chunkIndex attempt=${attempt + 1}")
        }
        return false
    }

    private suspend fun completeUpload(
        relayUrl: String,
        uploadSessionId: String,
        secureMediaToken: String
    ): RelayMediaUploadResult {
        val response =
            postJson(
                "$relayUrl/relay/media/upload/complete",
                JSONObject()
                    .put("uploadSessionId", uploadSessionId)
                    .put("secureMediaToken", secureMediaToken)
                    .toString()
            )
        return RelayMediaUploadResult(
            successful = response.optBoolean("ok"),
            status = response.optString("status", if (response.optBoolean("ok")) "MEDIA_QUEUED_REMOTE" else "FAILED"),
            mediaId = response.optString("mediaId"),
            messageId = response.optString("messageId"),
            packetId = response.optString("packetId"),
            secureMediaToken = response.optString("secureMediaToken").ifBlank { null },
            expiresAt = response.optLong("expiresAt", 0L),
            error = response.optString("error").ifBlank { null }
        )
    }

    private suspend fun postJson(urlValue: String, payload: String): JSONObject = withContext(Dispatchers.IO) {
        runCatching {
            val connection =
                (URL(urlValue).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 7000
                    readTimeout = 7000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            connection.outputStream.bufferedWriter().use { it.write(payload) }
            val text =
                (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
            connection.disconnect()
            if (text.isBlank()) JSONObject().put("ok", false) else JSONObject(text)
        }.getOrElse {
            JSONObject().put("ok", false).put("error", it.message ?: "upload_failed")
        }
    }

    private fun preferredChunkSize(context: Context): Int {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return 96 * 1024
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return 96 * 1024
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 256 * 1024
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> 128 * 1024
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 64 * 1024
            else -> 96 * 1024
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun JSONArray?.toIntSet(): Set<Int> {
        if (this == null) return emptySet()
        return buildSet {
            for (i in 0 until length()) {
                add(optInt(i))
            }
        }
    }
}
