package com.ghalbitnet.meshx2.core.log

/**
 * =====================================================
 * GHALBIT MESH X2
 * LOCAL LOG BUFFER
 * =====================================================
 *
 * Penyimpanan log ringan di memori.
 *
 * FUTURE:
 * - export log ke file
 * - kirim log terenkripsi ke node admin
 * - tampilkan di halaman DebugActivity
 */

object LocalLogBuffer {

    private const val MAX_LINES = 200

    private val logs =
        ArrayDeque<String>()

    @Synchronized
    fun add(
        line: String
    ) {
        if (logs.size >= MAX_LINES) {
            logs.removeFirst()
        }

        logs.addLast(
            "${System.currentTimeMillis()} | $line"
        )
    }

    @Synchronized
    fun getAll(): List<String> {
        return logs.toList()
    }

    @Synchronized
    fun getText(): String {
        return logs.joinToString("\n")
    }

    @Synchronized
    fun clear() {
        logs.clear()
    }
}
