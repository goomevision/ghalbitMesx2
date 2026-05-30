package com.ghalbitnet.meshx2.chat

enum class RouteHealthStatus(
    val label: String
) {
    STABLE("Local stabil"),
    NEARBY_DETECTED("Perangkat dekat terdeteksi"),
    PROBING_ROUTE("Menguji jalur suara"),
    VOICE_PROBE_READY("Probe suara siap"),
    LOCAL_VOICE_READY("Suara lokal siap"),
    LOCAL_VOICE_UNSTABLE("Jalur suara tidak stabil"),
    ROUTE_DEMOTED_AFTER_CONFIRMATION("Jalur diturunkan"),
    WEAK("Weak Signal"),
    RECONNECTING("Reconnecting"),
    INTERNET_FALLBACK("Online internet"),
    OFFLINE_PENDING("Offline / pending")
}
