package com.ghalbitnet.meshx2.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

object MediaPreviewLoader {
    fun loadImageThumbnail(path: String, reqWidth: Int = 360, reqHeight: Int = 240): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        )
        if (bitmap != null) {
            Log.d("GHALBIT-FILE-PREVIEW", "thumbnail generated")
        }
        return bitmap
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth / 2 >= reqWidth && currentHeight / 2 >= reqHeight) {
            currentWidth /= 2
            currentHeight /= 2
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
