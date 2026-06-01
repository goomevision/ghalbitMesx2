package com.ghalbitnet.meshx2.profile

import android.content.Context
import android.util.Log
import com.ghalbitnet.meshx2.security.NodeSigningIdentityManager

object ProfileRepository {
    fun saveProfessionalExtras(context: Context, globalId: String, extras: ProfessionalProfileExtras) {
        ProfessionalProfileExtrasStore.save(context, globalId, extras)
    }

    fun contactKey(globalId: String?, chatId: String?): String {
        return globalId?.takeIf { it.isNotBlank() }
            ?: chatId?.takeIf { it.isNotBlank() }
            ?: "unknown"
    }

    fun getOrCreateMyProfile(context: Context): CommunityProfile {
        val dao = ProfileDatabase.getInstance(context).profileDao()
        val existing = dao.getMyProfile()
        if (existing != null) {
            return existing.toDomain(
                context = context,
                privacy = dao.getPrivacy() ?: ProfilePrivacyEntity()
            )
        }
        val identity = NodeSigningIdentityManager.getOrCreate(context)
        val now = System.currentTimeMillis()
        val base = MyProfileEntity(
            globalId = identity.globalId,
            publicKeyBase64 = identity.publicKeyBase64,
            publicKeyHash = identity.publicKeyHash,
            displayName = "Pengguna GhalbitNet",
            nickname = identity.globalId.takeLast(6),
            communityName = "GhalbitNet Community",
            roleTitle = "Anggota Komunitas",
            bio = "Siap terhubung lewat mesh dan relay komunitas.",
            region = "",
            organization = null,
            skillTagsCsv = "",
            avatarUri = null,
            bannerColor = ContactCardTheme.OCEAN.accentColor,
            cardThemeId = ContactCardTheme.OCEAN.themeId,
            statusMessage = "Tersedia untuk terhubung",
            statusType = CommunityStatusType.AVAILABLE.wireValue,
            statusUpdatedAt = now,
            statusExpiresAt = null,
            updatedAt = now,
            profileVersion = 1,
            signature = "",
            isPublicProfile = false,
            showPhonePublicly = false,
            showRegionPublicly = true,
            showStatusPublicly = true,
            relayDiscoveryEnabled = true,
            avatarSyncEnabled = false,
            relaySyncEnabled = false
        )
        val signed = signProfile(context, base)
        dao.upsertMyProfile(signed)
        dao.upsertPrivacy(
            ProfilePrivacyEntity(
                publicProfileEnabled = signed.isPublicProfile,
                showPhonePublicly = signed.showPhonePublicly,
                showRegionPublicly = signed.showRegionPublicly,
                showStatusPublicly = signed.showStatusPublicly,
                relayDiscoveryEnabled = signed.relayDiscoveryEnabled,
                avatarSyncEnabled = signed.avatarSyncEnabled
            )
        )
        Log.d("GHALBIT-PROFILE", "signed")
        return signed.toDomain(context, dao.getPrivacy() ?: ProfilePrivacyEntity())
    }

    fun updateMyProfile(context: Context, mutate: (MyProfileEntity) -> MyProfileEntity): CommunityProfile {
        val dao = ProfileDatabase.getInstance(context).profileDao()
        val current = dao.getMyProfile() ?: getOrCreateMyProfile(context).toEntity()
        val next = mutate(current).copy(
            globalId = current.globalId,
            publicKeyBase64 = current.publicKeyBase64,
            publicKeyHash = current.publicKeyHash,
            updatedAt = System.currentTimeMillis(),
            profileVersion = current.profileVersion + 1
        )
        val signed = signProfile(context, next)
        dao.upsertMyProfile(signed)
        dao.upsertPrivacy(
            ProfilePrivacyEntity(
                publicProfileEnabled = signed.isPublicProfile,
                showPhonePublicly = signed.showPhonePublicly,
                showRegionPublicly = signed.showRegionPublicly,
                showStatusPublicly = signed.showStatusPublicly,
                relayDiscoveryEnabled = signed.relayDiscoveryEnabled,
                avatarSyncEnabled = signed.avatarSyncEnabled
            )
        )
        Log.d("GHALBIT-PROFILE", "edited")
        return signed.toDomain(context, dao.getPrivacy() ?: ProfilePrivacyEntity())
    }

    fun saveLocalAlias(
        context: Context,
        globalId: String?,
        chatId: String?,
        publicDisplayName: String?,
        publicNickname: String?,
        localAlias: String?,
        localNote: String?,
        communityLabel: String?,
        savedAsName: String?,
        localTags: List<String>,
        favorite: Boolean,
        pinned: Boolean,
        lastProfileSyncAt: Long = System.currentTimeMillis()
    ) {
        val key = contactKey(globalId, chatId)
        ProfileDatabase.getInstance(context).profileDao().upsertContactAlias(
            ContactAliasEntity(
                contactKey = key,
                globalId = globalId,
                chatId = chatId,
                publicDisplayName = publicDisplayName,
                publicNickname = publicNickname,
                localAlias = localAlias?.trim().orEmpty().ifBlank { null },
                localNote = localNote?.trim().orEmpty().ifBlank { null },
                communityLabel = communityLabel?.trim().orEmpty().ifBlank { null },
                savedAsName = savedAsName?.trim().orEmpty().ifBlank { null },
                localTagsCsv = localTags.joinToString(","),
                isFavorite = favorite,
                isPinned = pinned,
                lastProfileSyncAt = lastProfileSyncAt
            )
        )
        Log.d("GHALBIT-CONTACT-ALIAS", "saved id=$key")
        Log.d("GHALBIT-CONTACT-ALIAS", "local only id=$key")
    }

    fun getResolvedContact(
        context: Context,
        globalId: String?,
        chatId: String?,
        fallbackDisplayName: String,
        publicKeyHash: String? = null,
        routeHint: String? = null
    ): CommunityProfile {
        val dao = ProfileDatabase.getInstance(context).profileDao()
        val alias = globalId?.let { dao.getContactAliasByGlobalId(it) } ?: dao.getContactAlias(contactKey(globalId, chatId))
        val remoteProfile = globalId?.let { dao.getContactProfile(it) }
        val baseDisplayName =
            alias?.publicDisplayName?.takeIf { it.isNotBlank() }
                ?: remoteProfile?.publicDisplayName?.takeIf { it.isNotBlank() }
                ?: fallbackDisplayName
        val profile =
            if (remoteProfile != null) {
                remoteProfile.toDomain(context, alias)
            } else {
                CommunityProfile(
                    globalId = globalId ?: contactKey(globalId, chatId),
                    publicKeyBase64 = "",
                    publicKeyHash = publicKeyHash ?: "",
                    displayName = baseDisplayName,
                    nickname = alias?.publicNickname ?: "",
                    communityName = alias?.communityLabel ?: "",
                    roleTitle = "",
                    bio = "",
                    region = "",
                    organization = null,
                    skillTags = alias?.localTagsCsv?.split(',')?.mapNotNull { it.trim().takeIf(String::isNotBlank) } ?: emptyList(),
                    avatarUri = null,
                    bannerColor = ContactCardTheme.OCEAN.accentColor,
                    cardTheme = ContactCardTheme.OCEAN,
                    statusMessage = "",
                    statusType = CommunityStatusType.OFFLINE,
                    statusUpdatedAt = 0L,
                    statusExpiresAt = null,
                    updatedAt = 0L,
                    profileVersion = 0,
                    signature = "",
                    isPublicProfile = false,
                    isRelayDiscoveryEnabled = false,
                    isStatusVisible = true,
                    isRegionVisible = true,
                    isAvatarSyncEnabled = false,
                    localAlias = alias?.localAlias,
                    localNote = alias?.localNote,
                    communityLabel = alias?.communityLabel,
                    savedAsName = alias?.savedAsName,
                    localTags = alias?.localTagsCsv?.split(',')?.mapNotNull { it.trim().takeIf(String::isNotBlank) } ?: emptyList(),
                    isFavorite = alias?.isFavorite == true,
                    isPinned = alias?.isPinned == true,
                    lastProfileSyncAt = alias?.lastProfileSyncAt ?: 0L,
                    routeHint = routeHint,
                    verifiedAt = null,
                    careerHeadline = null,
                    visionStatement = null,
                    missionStatement = null,
                    activeProjects = emptyList(),
                    skillsOffered = emptyList(),
                    skillsWanted = emptyList(),
                    helpOffered = emptyList(),
                    helpNeeded = emptyList(),
                    portfolioLinks = emptyList(),
                    communityRoles = emptyList(),
                    availabilityStatus = null
                )
            }
        Log.d("GHALBIT-CONTACT-ALIAS", "displayed id=${contactKey(globalId, chatId)}")
        return if (profile.displayName.isBlank()) profile.copy(displayName = fallbackDisplayName) else profile
    }

    fun upsertRemoteProfile(context: Context, profile: ContactProfileEntity) {
        ProfileDatabase.getInstance(context).profileDao().upsertContactProfile(profile)
    }

    fun getPrivacy(context: Context): ProfilePrivacyEntity {
        return ProfileDatabase.getInstance(context).profileDao().getPrivacy() ?: ProfilePrivacyEntity()
    }

    private fun signProfile(context: Context, entity: MyProfileEntity): MyProfileEntity {
        val payload = ProfileSyncManager.profilePayloadJson(entity)
        val signature = NodeSigningIdentityManager.sign(context, payload, entity.globalId)
        return entity.copy(signature = signature)
    }

    private fun MyProfileEntity.toDomain(context: Context, privacy: ProfilePrivacyEntity): CommunityProfile {
        val extras = ProfessionalProfileExtrasStore.load(context, globalId).withFallback()
        return CommunityProfile(
            globalId = globalId,
            publicKeyBase64 = publicKeyBase64,
            publicKeyHash = publicKeyHash,
            displayName = displayName,
            nickname = nickname,
            communityName = communityName,
            roleTitle = roleTitle,
            bio = bio,
            region = region,
            organization = organization,
            skillTags = skillTagsCsv.split(',').mapNotNull { it.trim().takeIf(String::isNotBlank) },
            avatarUri = avatarUri,
            bannerColor = bannerColor,
            cardTheme = ContactCardTheme.fromId(cardThemeId),
            statusMessage = statusMessage,
            statusType = CommunityStatusType.fromWireValue(statusType),
            statusUpdatedAt = statusUpdatedAt,
            statusExpiresAt = statusExpiresAt,
            updatedAt = updatedAt,
            profileVersion = profileVersion,
            signature = signature,
            isPublicProfile = privacy.publicProfileEnabled,
            isRelayDiscoveryEnabled = privacy.relayDiscoveryEnabled,
            isStatusVisible = privacy.showStatusPublicly,
            isRegionVisible = privacy.showRegionPublicly,
            isAvatarSyncEnabled = privacy.avatarSyncEnabled,
            careerHeadline = extras.careerHeadline,
            visionStatement = extras.visionStatement,
            missionStatement = extras.missionStatement,
            activeProjects = extras.activeProjects,
            skillsOffered = extras.skillsOffered,
            skillsWanted = extras.skillsWanted,
            helpOffered = extras.helpOffered,
            helpNeeded = extras.helpNeeded,
            portfolioLinks = extras.portfolioLinks,
            communityRoles = extras.communityRoles,
            availabilityStatus = extras.availabilityStatus
        )
    }

    private fun ContactProfileEntity.toDomain(context: Context, alias: ContactAliasEntity?): CommunityProfile {
        val extras = ProfessionalProfileExtrasStore.load(context, globalId).withFallback()
        return CommunityProfile(
            globalId = globalId,
            publicKeyBase64 = publicKeyBase64,
            publicKeyHash = publicKeyHash,
            displayName = publicDisplayName,
            nickname = publicNickname,
            communityName = communityName,
            roleTitle = roleTitle,
            bio = bio,
            region = region,
            organization = organization,
            skillTags = skillTagsCsv.split(',').mapNotNull { it.trim().takeIf(String::isNotBlank) },
            avatarUri = avatarUri,
            bannerColor = bannerColor,
            cardTheme = ContactCardTheme.fromId(cardThemeId),
            statusMessage = statusMessage,
            statusType = CommunityStatusType.fromWireValue(statusType),
            statusUpdatedAt = statusUpdatedAt,
            statusExpiresAt = statusExpiresAt,
            updatedAt = updatedAt,
            profileVersion = profileVersion,
            signature = signature,
            isPublicProfile = visibility.equals("PUBLIC", ignoreCase = true),
            isRelayDiscoveryEnabled = true,
            isStatusVisible = true,
            isRegionVisible = true,
            isAvatarSyncEnabled = true,
            localAlias = alias?.localAlias,
            localNote = alias?.localNote,
            communityLabel = alias?.communityLabel,
            savedAsName = alias?.savedAsName,
            localTags = alias?.localTagsCsv?.split(',')?.mapNotNull { it.trim().takeIf(String::isNotBlank) } ?: emptyList(),
            isFavorite = alias?.isFavorite == true,
            isPinned = alias?.isPinned == true,
            lastProfileSyncAt = alias?.lastProfileSyncAt ?: 0L,
            routeHint = routeHint,
            verifiedAt = verifiedAt,
            careerHeadline = extras.careerHeadline,
            visionStatement = extras.visionStatement,
            missionStatement = extras.missionStatement,
            activeProjects = extras.activeProjects,
            skillsOffered = extras.skillsOffered,
            skillsWanted = extras.skillsWanted,
            helpOffered = extras.helpOffered,
            helpNeeded = extras.helpNeeded,
            portfolioLinks = extras.portfolioLinks,
            communityRoles = extras.communityRoles,
            availabilityStatus = extras.availabilityStatus
        )
    }

    private fun CommunityProfile.toEntity(): MyProfileEntity {
        return MyProfileEntity(
            globalId = globalId,
            publicKeyBase64 = publicKeyBase64,
            publicKeyHash = publicKeyHash,
            displayName = displayName,
            nickname = nickname,
            communityName = communityName,
            roleTitle = roleTitle,
            bio = bio,
            region = region,
            organization = organization,
            skillTagsCsv = skillTags.joinToString(","),
            avatarUri = avatarUri,
            bannerColor = bannerColor,
            cardThemeId = cardTheme.themeId,
            statusMessage = statusMessage,
            statusType = statusType.wireValue,
            statusUpdatedAt = statusUpdatedAt,
            statusExpiresAt = statusExpiresAt,
            updatedAt = updatedAt,
            profileVersion = profileVersion,
            signature = signature,
            isPublicProfile = isPublicProfile,
            showPhonePublicly = false,
            showRegionPublicly = isRegionVisible,
            showStatusPublicly = isStatusVisible,
            relayDiscoveryEnabled = isRelayDiscoveryEnabled,
            avatarSyncEnabled = isAvatarSyncEnabled,
            relaySyncEnabled = isRelayDiscoveryEnabled
        )
    }
}
