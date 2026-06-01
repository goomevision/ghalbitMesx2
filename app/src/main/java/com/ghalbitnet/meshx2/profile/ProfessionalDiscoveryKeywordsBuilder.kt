package com.ghalbitnet.meshx2.profile

object ProfessionalDiscoveryKeywordsBuilder {
    fun build(profile: CommunityProfile): List<String> {
        return buildList {
            addTokens(profile.roleTitle)
            addTokens(profile.communityName)
            addTokens(profile.careerHeadline)
            addTokens(profile.bio)
            profile.activeProjects.forEach { addTokens(it) }
            profile.skillsOffered.forEach { addTokens(it) }
            profile.skillsWanted.forEach { addTokens(it) }
            profile.communityRoles.forEach { addTokens(it) }
        }
            .map { it.trim().lowercase() }
            .filter { it.length >= 3 }
            .distinct()
            .take(64)
    }

    private fun MutableList<String>.addTokens(value: String?) {
        if (value.isNullOrBlank()) return
        value.split(",", ";", "\n", "\t", "|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { chunk ->
                add(chunk)
                chunk.split(" ")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { add(it) }
            }
    }
}
