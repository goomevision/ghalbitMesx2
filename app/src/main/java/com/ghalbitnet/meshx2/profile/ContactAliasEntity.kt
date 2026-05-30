package com.ghalbitnet.meshx2.profile

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_aliases")
data class ContactAliasEntity(
    @PrimaryKey val contactKey: String,
    val globalId: String? = null,
    val chatId: String? = null,
    val publicDisplayName: String? = null,
    val publicNickname: String? = null,
    val localAlias: String? = null,
    val localNote: String? = null,
    val communityLabel: String? = null,
    val savedAsName: String? = null,
    val localTagsCsv: String = "",
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val lastProfileSyncAt: Long = 0L
)
