package com.ghalbitnet.meshx2.chat

import android.content.Context

object GlobalContactDirectory {

    data class RemoteContact(
        val globalId: String,
        val alias: String,
        val group: String,
        val note: String,
        val prioritized: Boolean
    )

    private const val PREFS_NAME = "global_contact_directory"
    private const val ALIAS_PREFIX = "alias_"
    private const val GROUP_PREFIX = "group_"
    private const val NOTE_PREFIX = "note_"
    private const val PRIORITY_PREFIX = "priority_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveContact(
        context: Context,
        globalId: String,
        alias: String,
        group: String,
        note: String
    ) {
        val cleanId = normalizeGlobalId(globalId)
        if (cleanId.isBlank()) {
            return
        }

        prefs(context)
            .edit()
            .putString("$ALIAS_PREFIX$cleanId", alias.trim())
            .putString("$GROUP_PREFIX$cleanId", group.trim())
            .putString("$NOTE_PREFIX$cleanId", note.trim())
            .apply()
    }

    fun removeContact(
        context: Context,
        globalId: String
    ) {
        val cleanId = normalizeGlobalId(globalId)
        prefs(context)
            .edit()
            .remove("$ALIAS_PREFIX$cleanId")
            .remove("$GROUP_PREFIX$cleanId")
            .remove("$NOTE_PREFIX$cleanId")
            .remove("$PRIORITY_PREFIX$cleanId")
            .apply()
    }

    fun getAll(
        context: Context
    ): List<RemoteContact> {
        return prefs(context)
            .all
            .keys
            .filter { it.startsWith(ALIAS_PREFIX) }
            .map { key ->
                val globalId = key.removePrefix(ALIAS_PREFIX)
                RemoteContact(
                    globalId = globalId,
                    alias = prefs(context).getString(key, "").orEmpty(),
                    group = prefs(context).getString("$GROUP_PREFIX$globalId", "").orEmpty(),
                    note = prefs(context).getString("$NOTE_PREFIX$globalId", "").orEmpty(),
                    prioritized = prefs(context).getBoolean("$PRIORITY_PREFIX$globalId", false)
                )
            }
            .filter { it.globalId.isNotBlank() && it.alias.isNotBlank() }
            .sortedWith(
                compareByDescending<RemoteContact> { it.prioritized }
                    .thenBy { it.alias.lowercase() }
            )
    }

    fun setPrioritized(
        context: Context,
        globalId: String,
        prioritized: Boolean
    ) {
        val cleanId = normalizeGlobalId(globalId)
        prefs(context)
            .edit()
            .putBoolean("$PRIORITY_PREFIX$cleanId", prioritized)
            .apply()
    }

    fun isPrioritized(
        context: Context,
        globalId: String
    ): Boolean {
        val cleanId = normalizeGlobalId(globalId)
        return prefs(context).getBoolean("$PRIORITY_PREFIX$cleanId", false)
    }

    fun find(
        context: Context,
        globalId: String
    ): RemoteContact? {
        val cleanId =
            normalizeGlobalId(globalId)
        return getAll(context)
            .firstOrNull { it.globalId == cleanId }
    }

    fun normalizeGlobalId(raw: String): String {
        return raw.trim().uppercase()
    }
}
