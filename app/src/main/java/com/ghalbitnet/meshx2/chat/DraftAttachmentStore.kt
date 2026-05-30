package com.ghalbitnet.meshx2.chat

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

object DraftAttachmentStore {
    fun copyToDraft(context: Context, source: Uri, displayName: String): File? {
        return runCatching {
            val directory = File(context.cacheDir, "chat_drafts").apply { mkdirs() }
            val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = File(directory, "${System.currentTimeMillis()}_$safeName")
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            Log.d("GHALBIT-FILE-DRAFT", "created path=${target.name}")
            target
        }.getOrNull()
    }

    fun remove(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val file = File(path)
            if (file.exists()) {
                file.delete()
                Log.d("GHALBIT-FILE-DRAFT", "removed path=${file.name}")
            }
        }
    }
}
