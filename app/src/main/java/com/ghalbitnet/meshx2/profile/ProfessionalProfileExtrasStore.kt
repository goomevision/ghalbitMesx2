package com.ghalbitnet.meshx2.profile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ProfessionalProfileExtrasStore {
    private const val PREFS = "ghalbit_professional_profile_extras"

    fun load(context: Context, globalId: String): ProfessionalProfileExtras {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(globalId, null) ?: return ProfessionalProfileExtras()
        return runCatching {
            val json = JSONObject(raw)
            ProfessionalProfileExtras(
                careerHeadline = json.optString("careerHeadline").ifBlank { null },
                visionStatement = json.optString("visionStatement").ifBlank { null },
                missionStatement = json.optString("missionStatement").ifBlank { null },
                activeProjects = json.optJSONArray("activeProjects").toList(),
                skillsOffered = json.optJSONArray("skillsOffered").toList(),
                skillsWanted = json.optJSONArray("skillsWanted").toList(),
                helpOffered = json.optJSONArray("helpOffered").toList(),
                helpNeeded = json.optJSONArray("helpNeeded").toList(),
                portfolioLinks = json.optJSONArray("portfolioLinks").toList(),
                communityRoles = json.optJSONArray("communityRoles").toList(),
                availabilityStatus = json.optString("availabilityStatus").ifBlank { null }
            )
        }.getOrDefault(ProfessionalProfileExtras())
    }

    fun save(context: Context, globalId: String, extras: ProfessionalProfileExtras) {
        val payload = JSONObject()
            .put("careerHeadline", extras.careerHeadline ?: "")
            .put("visionStatement", extras.visionStatement ?: "")
            .put("missionStatement", extras.missionStatement ?: "")
            .put("activeProjects", JSONArray(extras.activeProjects))
            .put("skillsOffered", JSONArray(extras.skillsOffered))
            .put("skillsWanted", JSONArray(extras.skillsWanted))
            .put("helpOffered", JSONArray(extras.helpOffered))
            .put("helpNeeded", JSONArray(extras.helpNeeded))
            .put("portfolioLinks", JSONArray(extras.portfolioLinks))
            .put("communityRoles", JSONArray(extras.communityRoles))
            .put("availabilityStatus", extras.availabilityStatus ?: "")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(globalId, payload.toString())
            .apply()
    }

    private fun JSONArray?.toList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val value = optString(i).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }
}

