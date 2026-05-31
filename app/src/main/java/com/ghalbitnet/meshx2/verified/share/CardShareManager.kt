package com.ghalbitnet.meshx2.verified.share

object CardShareManager {
    fun supportedTargets(): List<String> {
        return listOf(
            "WhatsApp",
            "Telegram",
            "Email",
            "Bluetooth"
        )
    }
}
