package com.ghalbitnet.meshx2.verified.export

object PngExportImplementation {
    fun buildExportName(globalId:String): String {
        return "ghalbit_verified_${globalId}.png"
    }
}
