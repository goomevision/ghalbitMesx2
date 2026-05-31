package com.ghalbitnet.meshx2.verified

/**
 * PHASE 251B
 * Human-readable renderer for a verified name card.
 *
 * This is intentionally text-first so it can be shown safely in chat, logs,
 * notification previews, exported images, and later converted into a custom View.
 */
object VerifiedNameCardRenderer {
    fun renderText(payload: VerifiedNameCardPayload): String {
        val status = if (payload.isOfflineVerifiable() && !payload.isExpired()) {
            "VALID & SIGNED"
        } else if (payload.isExpired()) {
            "EXPIRED"
        } else {
            "PENDING SIGNATURE"
        }

        return buildString {
            appendLine("┌─────────────────────────────┐")
            appendLine("│ GHALBITNET VERIFIED CARD    │")
            appendLine("├─────────────────────────────┤")
            appendLine("│ Name      : ${payload.displayName.safeCardText()}".fitCardLine())
            appendLine("│ Role      : ${(payload.role ?: "-").safeCardText()}".fitCardLine())
            appendLine("│ Community : ${(payload.community ?: "-").safeCardText()}".fitCardLine())
            appendLine("│ Global ID : ${payload.globalId.safeCardText()}".fitCardLine())
            appendLine("│ Status    : $status".fitCardLine())
            appendLine("├─────────────────────────────┤")
            appendLine("│ Verify with QR / Signature  │")
            appendLine("└─────────────────────────────┘")
        }
    }

    fun renderShareSummary(payload: VerifiedNameCardPayload): String {
        val status = if (payload.isOfflineVerifiable()) "signed" else "unsigned"
        return "GHALBITNET verified card for ${payload.displayName.safeCardText()} (${payload.globalId.safeCardText()}) - $status"
    }

    private fun String.safeCardText(): String =
        replace("\n", " ")
            .replace("\r", " ")
            .trim()
            .ifBlank { "-" }

    private fun String.fitCardLine(width: Int = 31): String {
        val normalized = take(width - 1)
        return normalized.padEnd(width, ' ') + "│"
    }
}
