package com.ghalbitnet.meshx2.diagnostics.evidence

import android.content.Context
import org.json.JSONObject
import java.io.File

object RuntimeEvidenceStore {
    private const val DIR_NAME = "diagnostics"
    private const val FILE_NAME = "runtime_events.jsonl"
    private val lock = Any()

    fun file(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE_NAME)
    }

    fun append(context: Context, event: RuntimeEvidenceEvent) {
        val line = JSONObject().apply {
            put("ts", event.ts)
            put("event", event.event)
            put("source", event.source)
            put("messageId", event.messageId ?: "")
            put("callId", event.callId ?: "")
            put("peerId", event.peerId ?: "")
            put("status", event.status ?: "")
            put("details", event.details ?: "")
        }.toString()
        synchronized(lock) {
            file(context).appendText(line + "\n")
        }
    }

    fun readAll(context: Context): List<RuntimeEvidenceEvent> {
        val target = file(context)
        if (!target.exists()) return emptyList()
        return synchronized(lock) {
            target.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    runCatching {
                        val json = JSONObject(line)
                        RuntimeEvidenceEvent(
                            ts = json.optLong("ts"),
                            event = json.optString("event"),
                            source = json.optString("source"),
                            messageId = json.optString("messageId").ifBlank { null },
                            callId = json.optString("callId").ifBlank { null },
                            peerId = json.optString("peerId").ifBlank { null },
                            status = json.optString("status").ifBlank { null },
                            details = json.optString("details").ifBlank { null }
                        )
                    }.getOrNull()
                }
        }
    }

    fun clear(context: Context): Boolean = synchronized(lock) {
        val target = file(context)
        if (!target.exists()) return@synchronized true
        target.writeText("")
        true
    }
}

