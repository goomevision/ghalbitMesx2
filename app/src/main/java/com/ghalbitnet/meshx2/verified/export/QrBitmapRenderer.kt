package com.ghalbitnet.meshx2.verified.export

import android.graphics.Bitmap
import com.ghalbitnet.meshx2.verified.BitmapQrGenerator
import com.ghalbitnet.meshx2.verified.QrGenerationRequest

object QrBitmapRenderer {
    fun render(content:String, sizePx:Int = 768): Bitmap {
        return BitmapQrGenerator.generate(QrGenerationRequest(content, sizePx))
    }
}
