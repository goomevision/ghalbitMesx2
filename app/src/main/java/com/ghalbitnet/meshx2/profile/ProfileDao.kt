package com.ghalbitnet.meshx2.profile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProfileDao {
    @Query("SELECT * FROM my_profile LIMIT 1")
    fun getMyProfile(): MyProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertMyProfile(profile: MyProfileEntity)

    @Query("SELECT * FROM contact_profiles WHERE globalId = :globalId LIMIT 1")
    fun getContactProfile(globalId: String): ContactProfileEntity?

    @Query("SELECT * FROM contact_profiles WHERE globalId IN (:globalIds)")
    fun getContactProfiles(globalIds: List<String>): List<ContactProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertContactProfile(profile: ContactProfileEntity)

    @Query("SELECT * FROM contact_aliases WHERE contactKey = :contactKey LIMIT 1")
    fun getContactAlias(contactKey: String): ContactAliasEntity?

    @Query("SELECT * FROM contact_aliases WHERE globalId = :globalId LIMIT 1")
    fun getContactAliasByGlobalId(globalId: String): ContactAliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertContactAlias(alias: ContactAliasEntity)

    @Query("SELECT * FROM profile_privacy WHERE `key` = 'default' LIMIT 1")
    fun getPrivacy(): ProfilePrivacyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertPrivacy(privacy: ProfilePrivacyEntity)

    @Query("SELECT * FROM contact_aliases")
    fun listContactAliases(): List<ContactAliasEntity>

    @Query("SELECT COUNT(*) FROM contact_aliases WHERE (savedAsName IS NOT NULL AND TRIM(savedAsName) != '') OR isFavorite = 1 OR isPinned = 1")
    fun countSavedContactSignals(): Int

    @Query("SELECT * FROM contact_profiles")
    fun listContactProfiles(): List<ContactProfileEntity>

    @Query("SELECT COUNT(*) FROM contact_profiles")
    fun countContactProfiles(): Int

    @Query("SELECT COUNT(*) FROM contact_profiles WHERE verifiedAt IS NOT NULL")
    fun countVerifiedContactProfiles(): Int
}
