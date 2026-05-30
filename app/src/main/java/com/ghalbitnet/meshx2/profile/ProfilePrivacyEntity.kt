package com.ghalbitnet.meshx2.profile

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile_privacy")
data class ProfilePrivacyEntity(
    @PrimaryKey val key: String = "default",
    val publicProfileEnabled: Boolean = false,
    val showPhonePublicly: Boolean = false,
    val showRegionPublicly: Boolean = true,
    val showStatusPublicly: Boolean = true,
    val relayDiscoveryEnabled: Boolean = true,
    val avatarSyncEnabled: Boolean = false
)
