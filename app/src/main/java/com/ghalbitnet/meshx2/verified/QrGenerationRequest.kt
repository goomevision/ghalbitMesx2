package com.ghalbitnet.meshx2.verified

data class QrGenerationRequest(
    val payload:String,
    val sizePx:Int=768
)