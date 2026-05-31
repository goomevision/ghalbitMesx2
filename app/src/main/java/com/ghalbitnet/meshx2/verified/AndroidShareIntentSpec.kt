package com.ghalbitnet.meshx2.verified

data class AndroidShareIntentSpec(
    val mimeType: String,
    val chooserTitle: String,
    val includeTextFallback: Boolean = true
)
