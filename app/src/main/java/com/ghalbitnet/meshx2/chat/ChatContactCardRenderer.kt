package com.ghalbitnet.meshx2.chat

import org.json.JSONObject

object ChatContactCardRenderer {

    fun isContactCard(raw: String): Boolean {
        val json = extractJson(raw) ?: return false
        return json.has("globalId") &&
            (json.has("displayName") || json.has("publicKeyHash") || json.has("signature"))
    }

    fun render(raw: String): String? {
        val json = extractJson(raw) ?: return null
        if (!isContactCard(raw)) return null

        val globalId = json.optString("globalId").ifBlank { "GX-UNKNOWN" }
        val displayName = json.optString("displayName").ifBlank { "Kontak GHALBIT" }
        val nickname = json.optString("nickname").ifBlank { globalId.takeLast(6) }
        val roleTitle = json.optString("roleTitle").ifBlank { "Anggota Komunitas" }
        val relayHint = json.optString("relayHint").ifBlank { "Belum tersedia" }
        val publicKeyHash = json.optString("publicKeyHash").ifBlank { "Belum tersedia" }
        val shortHash = if (publicKeyHash.length > 16) publicKeyHash.take(16) else publicKeyHash
        val centralLink = "https://ghalbit.net/card/$globalId"
        val appLink = "ghalbit://card?id=$globalId"

        return buildString {
            appendLine("📇 KARTU NAMA GHALBIT")
            appendLine("━━━━━━━━━━━━━━━━")
            appendLine("👤 $displayName")
            appendLine("🏷️ $nickname")
            appendLine("🤝 $roleTitle")
            appendLine("🆔 $globalId")
            appendLine("🔐 $shortHash")
            appendLine("📡 $relayHint")
            appendLine()
            appendLine("Ketuk / salin link untuk membuka kartu:")
            appendLine(centralLink)
            appendLine()
            appendLine("Buka di aplikasi:")
            appendLine(appLink)
            appendLine("━━━━━━━━━━━━━━━━")
        }.trim()
    }

    private fun extractJson(raw: String): JSONObject? {
        val payload = extractMarkedPayload(raw)
        val candidate = payload ?: raw.trim()
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            JSONObject(candidate.substring(start, end + 1))
        }.getOrNull()
    }

    private fun extractMarkedPayload(raw: String): String? {
        val begin = "--- GHALBIT VERIFY PAYLOAD BEGIN ---"
        val end = "--- GHALBIT VERIFY PAYLOAD END ---"
        val beginIndex = raw.indexOf(begin)
        val endIndex = raw.indexOf(end)
        if (beginIndex >= 0 && endIndex > beginIndex) {
            return raw.substring(beginIndex + begin.length, endIndex).trim()
        }
        return null
    }
}
