package com.ghalbitnet.meshx2.core.server

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

object FirebaseBootstrap {

    @Volatile
    private var initialized = false

    @Synchronized
    fun ensureInitialized(context: Context): Boolean {
        if (!initialized) {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context) ?: return false
            }
            runCatching {
                FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            }
            initialized = true
        }
        return FirebaseApp.getApps(context).isNotEmpty()
    }
}
