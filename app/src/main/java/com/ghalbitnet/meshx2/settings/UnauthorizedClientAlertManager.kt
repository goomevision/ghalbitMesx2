package com.ghalbitnet.meshx2.settings

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.ghalbitnet.meshx2.vpn.VpnLogManager

object UnauthorizedClientAlertManager {

    fun showBlockedDialog(
        context: Context,
        onOpenSettings: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("Perangkat ditandai BLOCKED")
            .setMessage("Perangkat ditandai BLOCKED.\nUntuk memutus akses hotspot sepenuhnya, buka daftar blokir hotspot Android.")
            .setPositiveButton("Buka Pengaturan Hotspot") { _, _ ->
                VpnLogManager.warn(
                    "BLOCKLIST_MANUAL_ACTION_REQUIRED",
                    "Android standar mungkin memerlukan blokir manual dari pengaturan hotspot."
                )
                onOpenSettings()
            }
            .setNegativeButton("Nanti", null)
            .show()
    }
}
