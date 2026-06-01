package com.ghalbitnet.meshx2.profile

data class CommunityProfile(
    val globalId: String,
    val publicKeyBase64: String,
    val publicKeyHash: String,
    val displayName: String,
    val nickname: String,
    val communityName: String,
    val roleTitle: String,
    val bio: String,
    val region: String,
    val organization: String?,
    val skillTags: List<String>,
    val avatarUri: String?,
    val bannerColor: String,
    val cardTheme: ContactCardTheme,
    val statusMessage: String,
    val statusType: CommunityStatusType,
    val statusUpdatedAt: Long,
    val statusExpiresAt: Long?,
    val updatedAt: Long,
    val profileVersion: Int,
    val signature: String,
    val isPublicProfile: Boolean,
    val isRelayDiscoveryEnabled: Boolean,
    val isStatusVisible: Boolean,
    val isRegionVisible: Boolean,
    val isAvatarSyncEnabled: Boolean,
    val localAlias: String? = null,
    val localNote: String? = null,
    val communityLabel: String? = null,
    val savedAsName: String? = null,
    val localTags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val lastProfileSyncAt: Long = 0L,
    val routeHint: String? = null,
    val verifiedAt: Long? = null,
    val careerHeadline: String? = null,
    val visionStatement: String? = null,
    val missionStatement: String? = null,
    val activeProjects: List<String> = emptyList(),
    val skillsOffered: List<String> = emptyList(),
    val skillsWanted: List<String> = emptyList(),
    val helpOffered: List<String> = emptyList(),
    val helpNeeded: List<String> = emptyList(),
    val portfolioLinks: List<String> = emptyList(),
    val communityRoles: List<String> = emptyList(),
    val availabilityStatus: String? = null
) {
    val primaryName: String
        get() = localAlias?.takeIf { it.isNotBlank() } ?: displayName

    val publicSubtitle: String
        get() = nickname.takeIf { it.isNotBlank() } ?: roleTitle
}
