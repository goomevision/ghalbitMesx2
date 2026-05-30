package com.ghalbitnet.meshx2.call

import android.util.Log
import java.util.PriorityQueue

enum class VoiceFramePriority {
    HIGH,
    MEDIUM,
    LOW
}

data class PrioritizedVoiceFrame(
    val sequenceNumber: Int,
    val frame: VoiceFrame,
    val priority: VoiceFramePriority
)

class SpeechPriorityQueue {
    private val queue =
        PriorityQueue<PrioritizedVoiceFrame>(compareBy<PrioritizedVoiceFrame> {
            when (it.priority) {
                VoiceFramePriority.HIGH -> 0
                VoiceFramePriority.MEDIUM -> 1
                VoiceFramePriority.LOW -> 2
            }
        }.thenBy { it.sequenceNumber })

    fun offer(item: PrioritizedVoiceFrame) {
        if (item.priority == VoiceFramePriority.LOW) {
            Log.d("GHALBIT-SPEECH-PRIORITY", "dropped noise seq=${item.sequenceNumber}")
        } else {
            Log.d("GHALBIT-SPEECH-PRIORITY", "queued speech seq=${item.sequenceNumber}")
        }
        queue.offer(item)
    }

    fun poll(): PrioritizedVoiceFrame? {
        val next = queue.poll()
        if (next?.priority == VoiceFramePriority.HIGH) {
            Log.d("GHALBIT-SPEECH-PRIORITY", "high seq=${next.sequenceNumber}")
        }
        return next
    }
}
