package com.ghalbitnet.meshx2.simulation

data class LoopGuardResult(
    val ok: Boolean,
    val violations: List<String>
)

class LoopGuard {
    private val counters = mutableMapOf<String, Int>()
    private val violations = mutableListOf<String>()

    fun track(name: String, maxPerRun: Int) {
        val count = (counters[name] ?: 0) + 1
        counters[name] = count
        if (count > maxPerRun) {
            violations += "$name exceeded $maxPerRun (actual=$count)"
        }
    }

    fun assertNoLoop(): LoopGuardResult = LoopGuardResult(
        ok = violations.isEmpty(),
        violations = violations.toList()
    )
}

