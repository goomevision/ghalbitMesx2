package com.ghalbitnet.meshx2.verified.validation

object CardPayloadValidator {
    fun isUsable(payload: String?): Boolean {
        if (payload.isNullOrBlank()) return false
        val value = payload.trim()
        return value.length >= 24 && (
            value.contains("global", ignoreCase = true) ||
            value.contains("GHALBIT", ignoreCase = true) ||
            value.contains("|")
        )
    }

    fun statusLabel(payload: String?): String {
        return if (isUsable(payload)) "VALID STRUCTURE" else "INVALID STRUCTURE"
    }
}
