package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.vpn.VpnLogManager
import java.security.SecureRandom

object HotspotPasswordRotationAdvisor {

    private const val PREFS_NAME = "ghalbit_password_rotation"
    private const val KEY_LAST_RECOMMEND_AT = "last_recommend_at"
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray()

    fun evaluate(context: Context, forced: Boolean = false): PasswordRotationRecommendation {
        val summary = HotspotBlocklistAssistant.summary(context)
        val status = ProviderNetworkStatusEvaluator.evaluate(context)
        val shouldRecommend = forced || summary.unauthorizedCount >= 3 || status.status == ProviderNetworkStatus.RISK || status.status == ProviderNetworkStatus.DANGER
        val reason =
            when {
                forced -> "Provider meminta rotasi password."
                status.status == ProviderNetworkStatus.DANGER -> "Ada risiko tinggi pada hotspot komunitas."
                summary.unauthorizedCount >= 3 -> "Banyak client asing terdeteksi."
                else -> "Hotspot terlihat stabil."
            }
        if (shouldRecommend) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_RECOMMEND_AT, System.currentTimeMillis())
                .apply()
            VpnLogManager.info("PASSWORD_ROTATION_RECOMMENDED", reason)
        }
        return PasswordRotationRecommendation(
            shouldRecommend = shouldRecommend,
            reason = reason,
            suggestedPassword = if (shouldRecommend) generatePassword() else null
        )
    }

    fun generatePassword(): String {
        val random = SecureRandom()
        val parts =
            List(3) {
                buildString {
                    repeat(4) {
                        append(alphabet[random.nextInt(alphabet.size)])
                    }
                }
            }
        val password = "GX2-" + parts.joinToString("-")
        VpnLogManager.info("PASSWORD_GENERATED", "password=$password")
        return password
    }
}
