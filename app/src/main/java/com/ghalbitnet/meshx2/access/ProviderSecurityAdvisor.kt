package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.R

object ProviderSecurityAdvisor {

    data class Advice(
        val title: String,
        val detail: String,
        val suggestions: List<String>
    )

    fun advise(context: Context, snapshot: HotspotClientDetector.Snapshot): Advice {
        val suggestions =
            listOf(
                context.getString(R.string.hotspot_security_suggestion_password),
                context.getString(R.string.hotspot_security_suggestion_share_only_ghalbit),
                context.getString(R.string.hotspot_security_suggestion_gateway_proxy),
                context.getString(R.string.hotspot_security_suggestion_router)
            )

        if (!snapshot.hotspotActive) {
            return Advice(
                title = context.getString(R.string.hotspot_security_title_idle),
                detail = context.getString(R.string.hotspot_security_detail_idle),
                suggestions = suggestions
            )
        }

        return Advice(
            title = context.getString(R.string.hotspot_security_title_warning),
            detail = context.getString(R.string.hotspot_security_detail_warning),
            suggestions = suggestions
        )
    }
}
