package com.ghalbitnet.meshx2.profile

object ProfileShareFormatter {

    private const val BEGIN_MARKER = "--- GHALBIT VERIFY PAYLOAD BEGIN ---"
    private const val END_MARKER = "--- GHALBIT VERIFY PAYLOAD END ---"

    fun format(profile: CommunityProfile, encodedPayload: String): String {
        val role = profile.roleTitle?.takeIf { it.isNotBlank() } ?: "Anggota Komunitas"
        val nickname = profile.nickname?.takeIf { it.isNotBlank() } ?: profile.globalId.takeLast(6)
        val route = profile.routeHint?.takeIf { it.isNotBlank() } ?: "Belum tersedia"
        val publicHash = profile.publicKeyHash?.takeIf { it.isNotBlank() }?.take(16) ?: "Belum tersedia"
        val centralLink = ProfileSmartLink.central(profile)
        val appLink = ProfileSmartLink.appDeepLink(profile, encodedPayload)
        val localLink = ProfileSmartLink.local(profile)

        return buildString {
            appendLine("📇 KARTU NAMA GHALBIT")
            appendLine()
            appendLine("Nama: ${profile.displayName}")
            appendLine("Panggilan: $nickname")
            appendLine("Peran: $role")
            appendLine("ID: ${profile.globalId}")
            appendLine("Fingerprint: $publicHash")
            appendLine("Jalur terakhir: $route")
            appendLine()
            appendLine("Buka kartu nama:")
            appendLine(centralLink)
            appendLine()
            appendLine("Buka di aplikasi GHALBIT:")
            appendLine(appLink)
            appendLine()
            appendLine("Jika sedang satu jaringan lokal:")
            appendLine(localLink)
            appendLine()
            appendLine("Jika link tidak bisa dibuka, aplikasi GHALBIT tetap dapat membaca payload verifikasi di bawah ini dan menampilkan kartu nama secara lokal.")
            appendLine()
            appendLine(BEGIN_MARKER)
            appendLine(encodedPayload)
            appendLine(END_MARKER)
        }.trim()
    }

    fun extractPayload(text: String): String {
        val begin = text.indexOf(BEGIN_MARKER)
        val end = text.indexOf(END_MARKER)
        if (begin >= 0 && end > begin) {
            return text.substring(begin + BEGIN_MARKER.length, end).trim()
        }
        return text.trim()
    }
}
