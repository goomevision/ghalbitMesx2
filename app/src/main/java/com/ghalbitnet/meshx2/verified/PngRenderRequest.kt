package com.ghalbitnet.meshx2.verified

data class PngRenderRequest(
    val title:String,
    val subtitle:String,
    val qrContent:String,
    val includeVerificationBadge:Boolean=true
)