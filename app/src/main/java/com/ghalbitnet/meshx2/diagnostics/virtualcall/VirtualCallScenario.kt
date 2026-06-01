package com.ghalbitnet.meshx2.diagnostics.virtualcall

data class VirtualCallScenario(
    val callerPeerId: String,
    val callerGlobalId: String,
    val callerDisplayName: String,
    val routeHint: String = "virtual://incoming",
    val speechPrompt: String = "Halo Ghalbit, test panggilan satu dua tiga",
    val waitAcceptMs: Long = 30_000L,
    val audioProbeMs: Long = 10_000L
)

data class VirtualCallResult(
    val callId: String,
    val incomingShown: Boolean,
    val accepted: Boolean,
    val connected: Boolean,
    val audioRms: Double,
    val audioPeak: Int,
    val speechDetected: Boolean,
    val ringtoneStopped: Boolean,
    val ended: Boolean,
    val status: String,
    val notes: List<String>
)

