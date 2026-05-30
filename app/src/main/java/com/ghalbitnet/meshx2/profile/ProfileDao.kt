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
}
