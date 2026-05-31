package com.ghalbitnet.meshx2.verified

/**
 * PHASE 251A
 * Standard document type identifiers for GHALBITNET verified documents.
 */
enum class VerifiedDocumentType(val wireName: String) {
    NAME_CARD("ghalbit_verified_name_card"),
    TRANSACTION_RECEIPT("ghalbit_verified_transaction_receipt");

    companion object {
        fun fromWireName(value: String?): VerifiedDocumentType? =
            values().firstOrNull { it.wireName == value }
    }
}
