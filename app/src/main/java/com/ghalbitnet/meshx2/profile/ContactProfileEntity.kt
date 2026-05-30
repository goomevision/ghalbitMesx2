package com.ghalbitnet.meshx2.profile

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_profiles")
data class ContactProfileEntity(
    @PrimaryKey val globalId: String,
    val publicKeyBase64: String = "",
    val publicKeyHash: String = "",
    val publicDisplayName: String = "",
    val publicNickname: String = "",
    val communityName: String = "",
    val roleTitle: String = "",
    val bio: String = "",
    val region: String = "",
    val organization: String? = null,
    val skillTagsCsv: String = "",
    val avatarUri: String? = null,
    val bannerColor: String = "#48D6E8",
    val cardThemeId: String = ContactCardTheme.OCEAN.themeId,
    val statusMessage: String = "",
    val statusType: String = CommunityStatusType.AVAILABLE.wireValue,
    val statusUpdatedAt: Long = 0L,
    val statusExpiresAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val profileVersion: Int = 1,
    val signature: String = "",
    val visibility: String = "PRIVATE",
    val routeHint: String? = null,
    val verifiedAt: Long? = null
)
