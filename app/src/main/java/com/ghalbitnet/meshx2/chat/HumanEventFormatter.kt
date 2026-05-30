package com.ghalbitnet.meshx2.chat

import android.content.Context
import com.ghalbitnet.meshx2.settings.CommunicationSettingsManager
import com.ghalbitnet.meshx2.settings.DeveloperModeManager

object HumanEventFormatter {
    fun format(type: String, fallbackText: String): String? {
        return when (type.uppercase()) {
            "CALL_INVITE", "CALL_START" -> "Memulai panggilan..."
            "CALL_ACCEPT" -> "Panggilan terhubung"
            "CALL_REJECT" -> "Panggilan ditolak"
            "CALL_END" -> "Panggilan berakhir"
            "VOICE_HELLO" -> "Menghubungkan suara..."
            "VOICE_PROBE" -> "Menguji kualitas suara..."
            "VOICE_STREAM_START" -> "Suara mulai tersambung"
            "VOICE_STREAM_END" -> "Suara berhenti"
            "ROUTE_UPDATE" -> "Rute komunikasi diperbarui"
            "RELAY_PREPARED" -> "Relay cadangan siap"
            "IDENTITY_LOCAL_ONLY" -> "ID tersimpan lokal"
            "IDENTITY_PENDING_SYNC" -> "ID sedang disinkronkan"
            "IDENTITY_SERVER_SYNCED" -> "ID berhasil tersambung ke server"
            "ROUTE_SEARCHING" -> "Mencari jalur kontak"
            "ROUTE_SECONDARY_READY" -> "Jalur cadangan ditemukan"
            "HEARTBEAT_SIGNAL",
            "VOICE_ACK",
            "VOICE_HELLO_ACK",
            "VOICE_PROBE_ACK",
            "VOICE_TRANSPORT_ACK",
            "VOICE_STREAM_ACTIVE_ACK",
            "CALL_RINGING_ACK",
            "VOICE_HEARTBEAT",
            "IDENTITY_COPY_FORWARD",
            "IDENTITY_SYNC_ACK",
            "ROUTE_PROBE",
            "LOOKUP_PACKET" -> null
            else -> fallbackText
        }
    }

    fun displayText(context: Context, message: ChatMessage): String {
        val base = format(message.internalSignalType.orEmpty(), message.content) ?: message.content
        val showTechnicalDetail =
            DeveloperModeManager.isEnabled(context) &&
                CommunicationSettingsManager.isTechnicalDetailEnabled(context) &&
                !message.internalSignalType.isNullOrBlank()
        return if (showTechnicalDetail) "$base - ${message.internalSignalType}" else base
    }
}
