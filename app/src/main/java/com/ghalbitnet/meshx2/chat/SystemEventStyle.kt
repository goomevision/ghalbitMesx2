package com.ghalbitnet.meshx2.chat

import androidx.annotation.LayoutRes
import com.ghalbitnet.meshx2.R

data class SystemEventStyle(
    val category: String,
    val icon: String,
    val accentColor: Int,
    val priority: EventPriority,
    val lifetime: EventLifetime,
    val autoHide: Boolean,
    @LayoutRes val layoutRes: Int
) {
    companion object {
        fun fromMessage(message: ChatMessage): SystemEventStyle {
            val signal = message.internalSignalType.orEmpty().uppercase()
            return when {
                message.contentType == "EMERGENCY_EVENT" || signal.contains("EMERGENCY") || signal.contains("SOS") -> SystemEventStyle(
                    category = "EMERGENCY_EVENT",
                    icon = "\uD83D\uDEA8",
                    accentColor = 0xFFE14D4D.toInt(),
                    priority = EventPriority.CRITICAL,
                    lifetime = EventLifetime.CRITICAL,
                    autoHide = false,
                    layoutRes = R.layout.item_system_emergency_event
                )
                signal.contains("RELAY") -> SystemEventStyle(
                    category = "RELAY_EVENT",
                    icon = "\uD83D\uDEF0",
                    accentColor = 0xFF4CC9F0.toInt(),
                    priority = EventPriority.NORMAL,
                    lifetime = EventLifetime.TRANSIENT,
                    autoHide = true,
                    layoutRes = R.layout.item_system_relay_event
                )
                signal.contains("ROUTE") || signal.contains("HEARTBEAT") -> SystemEventStyle(
                    category = "ROUTE_EVENT",
                    icon = "\uD83D\uDD00",
                    accentColor = 0xFF4A8DF5.toInt(),
                    priority = EventPriority.NORMAL,
                    lifetime = EventLifetime.TRANSIENT,
                    autoHide = true,
                    layoutRes = R.layout.item_system_relay_event
                )
                signal.contains("WARNING") || signal.contains("FAILED") || signal.contains("UNREACHABLE") -> SystemEventStyle(
                    category = "WARNING_EVENT",
                    icon = "\u26A0\uFE0F",
                    accentColor = 0xFFF0B14C.toInt(),
                    priority = EventPriority.IMPORTANT,
                    lifetime = EventLifetime.STICKY,
                    autoHide = false,
                    layoutRes = R.layout.item_system_warning_event
                )
                signal.contains("VOICE") -> SystemEventStyle(
                    category = "VOICE_EVENT",
                    icon = "\uD83C\uDFA4",
                    accentColor = 0xFF35D07F.toInt(),
                    priority = EventPriority.NORMAL,
                    lifetime = EventLifetime.TRANSIENT,
                    autoHide = true,
                    layoutRes = R.layout.item_system_call_event
                )
                else -> SystemEventStyle(
                    category = "CALL_EVENT",
                    icon = "\uD83D\uDCF1",
                    accentColor = 0xFF4A8DF5.toInt(),
                    priority = EventPriority.NORMAL,
                    lifetime = EventLifetime.STICKY,
                    autoHide = false,
                    layoutRes = R.layout.item_system_call_event
                )
            }
        }
    }
}
