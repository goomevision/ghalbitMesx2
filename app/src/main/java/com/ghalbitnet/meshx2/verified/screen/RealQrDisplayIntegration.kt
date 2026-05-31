package com.ghalbitnet.meshx2.verified.screen

import android.graphics.Bitmap
import com.ghalbitnet.meshx2.verified.export.QrBitmapRenderer

object RealQrDisplayIntegration {
    fun createQr(content:String): Bitmap {
        return QrBitmapRenderer.render(content)
    }
}
