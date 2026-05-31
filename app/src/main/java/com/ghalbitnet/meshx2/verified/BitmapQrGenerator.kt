package com.ghalbitnet.meshx2.verified

import android.graphics.Bitmap
import android.graphics.Color

object BitmapQrGenerator {
    fun generate(request: QrGenerationRequest): Bitmap {
        val matrix = ZxingQrGenerator.createMatrix(request.payload, request.sizePx)
        val bitmap = Bitmap.createBitmap(request.sizePx, request.sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until request.sizePx) {
            for (y in 0 until request.sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
