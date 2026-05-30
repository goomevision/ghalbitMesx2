package com.ghalbitnet.meshx2.chat

enum class ChatDeliveryState(
    val dbValue: String,
    val userLabel: String
) {
    DRAFT("DRAFT", "Draft"),
    DRAFT_TEXT("DRAFT_TEXT", "Draft teks"),
    DRAFT_MEDIA("DRAFT_MEDIA", "Draft media"),
    DRAFT_FILE("DRAFT_FILE", "Draft file"),
    REVIEW_READY("REVIEW_READY", "Siap ditinjau"),
    EDITING_DRAFT("EDITING_DRAFT", "Mengedit draft"),
    SEND_CONFIRMED("SEND_CONFIRMED", "Siap dikirim"),
    SEND_CANCELLED("SEND_CANCELLED", "Dibatalkan"),
    INTERNET_RELAY_NOT_CONFIGURED("INTERNET_RELAY_NOT_CONFIGURED", "Relay internet belum diatur"),
    RELAY_CONFIG_REQUIRED("RELAY_CONFIG_REQUIRED", "Relay belum dikonfigurasi"),
    WAITING_FOR_ROUTE("WAITING_FOR_ROUTE", "Menunggu jalur tersedia"),
    QUEUED_LOCAL("QUEUED_LOCAL", "Menunggu koneksi"),
    QUEUED_REMOTE("QUEUED_REMOTE", "Menunggu penerima online"),
    ACCEPTED_BY_RELAY("ACCEPTED_BY_RELAY", "Akan dikirim otomatis"),
    QUEUED("QUEUED", "Mengirim..."),
    SENDING("SENDING", "Mengirim..."),
    SENT_LOCAL("SENT_LOCAL", "Terkirim"),
    SENT_INTERNET("SENT_INTERNET", "Terkirim"),
    DELIVERED("DELIVERED", "Diterima"),
    DELIVERED_REMOTE("DELIVERED_REMOTE", "Diterima"),
    READ("READ", "Dibaca"),
    READ_REMOTE("READ_REMOTE", "Dibaca"),
    PENDING("PENDING", "Menunggu koneksi"),
    WAITING_FOR_PEER("WAITING_FOR_PEER", "Menunggu penerima online"),
    MEDIA_UPLOADING("MEDIA_UPLOADING", "Mengirim..."),
    MEDIA_RESUMING("MEDIA_RESUMING", "Melanjutkan kirim..."),
    MEDIA_QUEUED_REMOTE("MEDIA_QUEUED_REMOTE", "Menunggu penerima online"),
    MEDIA_DELIVERED_REMOTE("MEDIA_DELIVERED_REMOTE", "Diterima"),
    MEDIA_READ_REMOTE("MEDIA_READ_REMOTE", "Dibaca"),
    MEDIA_EXPIRED("MEDIA_EXPIRED", "Gagal"),
    EXPIRED_REMOTE("EXPIRED_REMOTE", "Gagal"),
    DELETED_LOCAL("DELETED_LOCAL", "Pesan dihapus"),
    DELETE_REQUESTED_REMOTE("DELETE_REQUESTED_REMOTE", "Menghapus pesan"),
    DELETED_REMOTE("DELETED_REMOTE", "Pesan dihapus"),
    EDIT_REQUESTED_REMOTE("EDIT_REQUESTED_REMOTE", "Mengubah pesan"),
    EDITED_REMOTE("EDITED_REMOTE", "Diedit"),
    FAILED_RETRYING("FAILED_RETRYING", "Mencoba ulang"),
    FAILED_FINAL("FAILED_FINAL", "Gagal");

    companion object {
        fun fromDb(raw: String?): ChatDeliveryState {
            val normalized = raw.orEmpty().trim().uppercase()
            return entries.firstOrNull { it.dbValue == normalized }
                ?: when (normalized) {
                    "SENT" -> SENT_LOCAL
                    "RECEIVED" -> DELIVERED
                    "PLAYED" -> READ
                    "FAILED" -> FAILED_FINAL
                    else -> DRAFT
                }
        }
    }
}
