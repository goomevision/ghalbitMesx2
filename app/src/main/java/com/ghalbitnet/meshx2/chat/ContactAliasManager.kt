package com.ghalbitnet.meshx2.chat

import android.content.Context

object ContactAliasManager {

    private const val GROUP_PREFIX = "group_"

    data class SavedContact(
        val peerName: String,
        val alias: String,
        val group: String?
    )

    private const val PREFS_NAME = "contact_aliases"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAlias(
        context: Context,
        peerName: String
    ): String? {
        return prefs(context)
            .getString(peerName, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun getDisplayName(
        context: Context,
        peerName: String
    ): String {
        return getAlias(context, peerName) ?: peerName
    }

    fun hasAlias(
        context: Context,
        peerName: String
    ): Boolean {
        return !getAlias(context, peerName).isNullOrBlank()
    }

    fun saveAlias(
        context: Context,
        peerName: String,
        alias: String
    ) {
        val cleanAlias =
            alias.trim()

        if (cleanAlias.isEmpty() || cleanAlias == peerName) {
            removeAlias(context, peerName)
            return
        }

        prefs(context)
            .edit()
            .putString(peerName, cleanAlias)
            .apply()
    }

    fun getGroup(
        context: Context,
        peerName: String
    ): String? {
        return prefs(context)
            .getString("$GROUP_PREFIX$peerName", null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun saveGroup(
        context: Context,
        peerName: String,
        group: String
    ) {
        val cleanGroup =
            group.trim()

        if (cleanGroup.isEmpty()) {
            removeGroup(context, peerName)
            return
        }

        prefs(context)
            .edit()
            .putString("$GROUP_PREFIX$peerName", cleanGroup)
            .apply()
    }

    fun saveContactProfile(
        context: Context,
        peerName: String,
        alias: String,
        group: String
    ) {
        saveAlias(context, peerName, alias)
        saveGroup(context, peerName, group)
    }

    fun removeAlias(
        context: Context,
        peerName: String
    ) {
        prefs(context)
            .edit()
            .remove(peerName)
            .apply()
    }

    fun removeGroup(
        context: Context,
        peerName: String
    ) {
        prefs(context)
            .edit()
            .remove("$GROUP_PREFIX$peerName")
            .apply()
    }

    fun getSavedContacts(
        context: Context
    ): List<SavedContact> {
        return prefs(context)
            .all
            .mapNotNull { entry ->
                if (entry.key.startsWith(GROUP_PREFIX)) {
                    return@mapNotNull null
                }

                val alias =
                    (entry.value as? String)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null

                SavedContact(
                    peerName = entry.key,
                    alias = alias,
                    group = getGroup(context, entry.key)
                )
            }
            .sortedBy { it.alias.lowercase() }
    }
}
