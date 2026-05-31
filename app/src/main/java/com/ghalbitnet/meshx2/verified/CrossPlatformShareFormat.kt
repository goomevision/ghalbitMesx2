package com.ghalbitnet.meshx2.verified

/**
 * PHASE 251L
 * Defines portable formats for sharing verified cards outside GHALBITNET.
 */
enum class CrossPlatformShareFormat(val mimeType: String) {
    TEXT("text/plain"),
    IMAGE_PNG("image/png"),
    IMAGE_JPEG("image/jpeg"),
    PDF("application/pdf"),
    JSON("application/json");

    fun isAttachment(): Boolean = this != TEXT && this != JSON
}
