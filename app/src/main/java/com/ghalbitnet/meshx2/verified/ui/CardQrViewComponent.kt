package com.ghalbitnet.meshx2.verified.ui

import android.graphics.Bitmap
import com.ghalbitnet.meshx2.verified.screen.RealQrDisplayIntegration

object CardQrViewComponent {
    fun create(content:String): Bitmap {
        return RealQrDisplayIntegration.createQr(content)
    }
}
