package com.ghalbitnet.meshx2.call

enum class VoipConnectionState {
    IDLE,
    INITIALIZING,
    CONNECTING,
    RINGING,
    CONNECTED,
    RECONNECTING,
    ENDED,
    FAILED
}
