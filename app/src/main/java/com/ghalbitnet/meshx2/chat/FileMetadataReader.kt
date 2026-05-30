package com.ghalbitnet.meshx2.chat

import android.content.Context
import android.webkit.MimeTypeMap
import java.io.File

object FileMetadataReader {
    data class Metadata(
        val displayName: String,
        val mimeType: String,
        val fileSize: Long,
        val warning: String?
    )

    fun fromFile(context: Context, file: File, fallbackMimeType: String, contentType: String): Metadata {
        val mimeType =
            fallbackMimeType.ifBlank {
                MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase())
                    ?: if (contentType == "IMAGE") "image/jpeg" else "application/octet-stream"
            }
        val warning =
            if (file.length() > 8L * 1024L * 1024L) {
                "File cukup besar, pengiriman mungkin lebih lambat."
            } else {
                null
            }
        return Metadata(
            displayName = file.name,
            mimeType = mimeType,
            fileSize = file.length(),
            warning = warning
        )
    }
}
