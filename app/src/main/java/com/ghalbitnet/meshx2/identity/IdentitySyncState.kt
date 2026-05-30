package com.ghalbitnet.meshx2.identity

enum class IdentitySyncState {
    LOCAL_ONLY,
    PENDING_SERVER_SYNC,
    SERVER_SYNCED,
    MESH_COPY_ACTIVE,
    MESH_COPY_EXPIRED,
    CONFLICT_NEEDS_RESOLVE
}
