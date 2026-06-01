package com.ghalbitnet.meshx2.simulation

class FakeClock(startMs: Long = 0L) {
    var nowMs: Long = startMs
        private set

    fun advance(ms: Long) {
        nowMs += ms.coerceAtLeast(0L)
    }
}

