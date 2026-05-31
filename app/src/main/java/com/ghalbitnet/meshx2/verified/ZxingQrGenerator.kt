package com.ghalbitnet.meshx2.verified

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object ZxingQrGenerator {
    fun createMatrix(content:String,size:Int)=QRCodeWriter().encode(content,BarcodeFormat.QR_CODE,size,size)
}
