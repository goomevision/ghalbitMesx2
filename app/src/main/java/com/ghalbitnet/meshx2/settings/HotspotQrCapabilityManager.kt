package com.ghalbitnet.meshx2.settings

import android.os.Build

object HotspotQrCapabilityManager {

    private val likelyQrShareManufacturers =
        setOf(
            "infinix",
            "tecno",
            "xiaomi",
            "redmi",
            "poco",
            "realme",
            "oppo",
            "vivo",
            "oneplus",
            "samsung"
        )

    fun isQrProofRequired(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim().lowercase()
        return manufacturer in likelyQrShareManufacturers
    }

    fun deviceLabel(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").trim()
    }
}
