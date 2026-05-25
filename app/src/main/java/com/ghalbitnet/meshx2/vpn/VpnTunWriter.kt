package com.ghalbitnet.meshx2.vpn

import java.io.FileOutputStream

object VpnTunWriter {

    @Volatile
    private var outputStream: FileOutputStream? = null

    @Synchronized
    fun attach(
        stream: FileOutputStream
    ) {
        outputStream = stream
    }

    @Synchronized
    fun detach() {
        runCatching { outputStream?.close() }
        outputStream = null
    }

    fun isActive(): Boolean = outputStream != null

    @Synchronized
    fun write(
        payload: ByteArray
    ): Boolean {
        val target = outputStream ?: return false
        return runCatching {
            target.write(payload)
            target.flush()
            true
        }.getOrElse { false }
    }
}
