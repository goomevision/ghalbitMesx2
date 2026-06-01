package com.ghalbitnet.meshx2.profile

import android.net.Uri
import android.util.Log
import android.widget.ImageView
import java.io.FileNotFoundException
import java.io.InputStream

object SafeAvatarLoader {
    private const val TAG = "GHALBIT-AVATAR"

    fun loadInto(view: ImageView, uriText: String?): Boolean {
        val raw = uriText?.trim().orEmpty()
        if (raw.isBlank()) return false
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return false
        return runCatching {
            view.context.contentResolver.openInputStream(uri).useBitmap { bitmap ->
                if (bitmap != null) {
                    view.setImageBitmap(bitmap)
                    true
                } else {
                    false
                }
            }
        }.onFailure { error ->
            if (error is SecurityException || error is FileNotFoundException) {
                Log.w(TAG, "avatar uri not accessible scheme=${uri.scheme}")
            } else {
                Log.w(TAG, "avatar load failed scheme=${uri.scheme} reason=${error.javaClass.simpleName}")
            }
        }.getOrDefault(false)
    }

    private inline fun InputStream?.useBitmap(block: (android.graphics.Bitmap?) -> Boolean): Boolean {
        return this.use { stream ->
            val bitmap = stream?.let { android.graphics.BitmapFactory.decodeStream(it) }
            block(bitmap)
        }
    }
}
