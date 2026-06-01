package com.ghalbitnet.meshx2.profile

data class ProfessionalProfileExtras(
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
    fun withFallback(): ProfessionalProfileExtras {
        return copy(
            careerHeadline = careerHeadline?.ifBlank { "Anggota Komunitas" } ?: "Anggota Komunitas",
            visionStatement = visionStatement?.ifBlank { "Belum diisi" } ?: "Belum diisi",
            missionStatement = missionStatement?.ifBlank { "Belum diisi" } ?: "Belum diisi",
            availabilityStatus = availabilityStatus?.ifBlank { "Belum tersedia" } ?: "Belum tersedia"
        )
    }
}

