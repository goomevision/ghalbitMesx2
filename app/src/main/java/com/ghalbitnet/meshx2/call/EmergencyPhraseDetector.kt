package com.ghalbitnet.meshx2.call

object EmergencyPhraseDetector {
    private val phrases =
        listOf("tolong", "darurat", "banjir", "api", "sakit", "terjebak", "bahaya", "evakuasi")

    fun detect(text: String): String? {
        val normalized = text.lowercase()
        return phrases.firstOrNull { normalized.contains(it) }
    }
}
