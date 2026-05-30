package com.ghalbitnet.meshx2.ui

enum class RuntimeUiState {
    IDLE,
    PREPARING,
    DISCOVERING,
    CONNECTING,
    VERIFYING,
    SYNCING,
    RECONNECTING,
    READY,
    WEAK_SIGNAL,
    INTERNET_FALLBACK,
    OFFLINE_PENDING,
    FAILED
}
