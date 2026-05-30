package com.ghalbitnet.meshx2.identity

import android.content.Context
import java.util.UUID

object DeviceInstanceRegistry {
    private const val PREFS = "ghalbit_identity_runtime"
    private const val KEY_DEVICE_INSTANCE_ID = "device_instance_id"

    fun getOrCreate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_INSTANCE_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_INSTANCE_ID, created).apply()
        return created
    }
}
