package com.ghalbitnet.meshx2.ui.dashboard

import android.content.Context

/** PHASE 250A — Dashboard Header Integration Factory. */
object DashboardHeaderFactory {
    fun create(
        context: Context,
        displayName: String = "GHALBITNET",
        globalId: String = "GX-UNKNOWN",
        nodeCount: Int = 0,
        online: Boolean = false
    ): ProfessionalDashboardHeaderView {
        return ProfessionalDashboardHeaderView(context).apply {
            render(
                displayName = displayName,
                globalId = globalId,
                nodeCount = nodeCount,
                online = online
            )
        }
    }
}
