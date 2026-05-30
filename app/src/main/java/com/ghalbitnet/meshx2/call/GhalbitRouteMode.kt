package com.ghalbitnet.meshx2.call

enum class GhalbitRouteMode {
    AUTO_HYBRID,
    FORCE_RELAY_ONLY,
    FORCE_MESH_ONLY;

    companion object {
        fun fromRaw(raw: String?): GhalbitRouteMode {
            val normalized = raw.orEmpty().trim().uppercase()
            return entries.firstOrNull { it.name == normalized } ?: AUTO_HYBRID
        }
    }
}
