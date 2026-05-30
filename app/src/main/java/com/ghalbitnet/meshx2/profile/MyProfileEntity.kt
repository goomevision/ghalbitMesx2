package com.ghalbitnet.meshx2.profile

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "my_profile")
data class MyProfileEntity(
    @PrimaryKey val globalId: String,
    val publicKeyBase64: String,
    val publicKeyHash: String,
    val displayName: String,
    val nickname: String,
    val communityName: String,
    val roleTitle: String,
    val bio: String,
    val region: String,
    val organization: String?,
    val skillTagsCsv: String,
    val avatarUri: String?,
    val bannerColor: String,
    val cardThemeId: String,
    val statusMessage: String,
    val statusType: String,
    val statusUpdatedAt: Long,
    val statusExpiresAt: Long?,
    val updatedAt: Long,
    val profileVersion: Int,
    val signature: String,
    val isPublicProfile: Boolean,
    val showPhonePublicly: Boolean,
    val showRegionPublicly: Boolean,
    val showStatusPublicly: Boolean,
    val relayDiscoveryEnabled: Boolean,
    val avatarSyncEnabled: Boolean,
    val relaySyncEnabled: Boolean
)
