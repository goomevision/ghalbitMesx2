package com.ghalbitnet.meshx2.call

import android.util.Log
import java.util.TreeMap

class VoiceChunkAssembler {
    private val chunks = TreeMap<Int, VoiceChunk>()

    fun add(chunk: VoiceChunk) {
        if (chunks.containsKey(chunk.sequenceNumber)) {
            Log.d("GHALBIT-VOICE-CHUNK", "duplicate ignored")
            return
        }
        chunks[chunk.sequenceNumber] = chunk
        Log.d("GHALBIT-VOICE-ASSEMBLER", "received seq=${chunk.sequenceNumber}")
    }

    fun nextBurst(): List<VoiceChunk> {
        if (chunks.isEmpty()) return emptyList()
        val start = chunks.firstKey()
        val result = mutableListOf<VoiceChunk>()
        var expected = start
        for ((seq, chunk) in chunks) {
            if (seq != expected) {
                Log.d("GHALBIT-VOICE-ASSEMBLER", "gap detected")
                break
            }
            result += chunk
            expected++
            if (chunk.isLastInBurst) break
        }
        result.forEach { chunks.remove(it.sequenceNumber) }
        if (result.isNotEmpty()) {
            Log.d("GHALBIT-VOICE-ASSEMBLER", "playback start")
        }
        return result
    }
}
