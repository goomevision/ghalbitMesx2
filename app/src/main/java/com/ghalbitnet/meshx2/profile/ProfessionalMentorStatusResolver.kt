package com.ghalbitnet.meshx2.profile

import android.util.Log
import com.ghalbitnet.meshx2.verified.trust.MentorBadgeRenderer

object ProfessionalMentorStatusResolver {
    data class Result(
        val isMentor: Boolean,
        val mentorCount: Int,
        val mentorLabel: String,
        val source: String,
        val fallbackUsed: Boolean
    )

    fun resolve(profile: CommunityProfile): Result {
        val roleMentor = profile.roleTitle.contains("mentor", ignoreCase = true) ||
            profile.roleTitle.contains("pembimbing", ignoreCase = true)
        val skillMentor = profile.skillTags.any {
            it.contains("mentor", ignoreCase = true) || it.contains("pembimbing", ignoreCase = true)
        }
        val localTagMentor = profile.localTags.any {
            it.contains("mentor", ignoreCase = true) || it.contains("pembimbing", ignoreCase = true)
        }
        val isMentor = roleMentor || skillMentor || localTagMentor
        val mentorCount = if (isMentor) 1 else 0
        val mentorLabel = MentorBadgeRenderer.level(mentorCount)
        val source = when {
            roleMentor -> "roleTitle"
            skillMentor -> "skillTags"
            localTagMentor -> "localTags"
            else -> "fallback"
        }
        val fallback = !isMentor
        Log.d("GHALBIT-CARD-TRUST", "mentor source resolved source=$source mentor=$isMentor")
        if (fallback) {
            Log.d("GHALBIT-CARD-TRUST", "mentor fallback used")
        }
        return Result(
            isMentor = isMentor,
            mentorCount = mentorCount,
            mentorLabel = mentorLabel,
            source = source,
            fallbackUsed = fallback
        )
    }
}

