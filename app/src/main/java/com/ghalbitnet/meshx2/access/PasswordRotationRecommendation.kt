package com.ghalbitnet.meshx2.access

data class PasswordRotationRecommendation(
    val shouldRecommend: Boolean,
    val reason: String,
    val suggestedPassword: String?
)
