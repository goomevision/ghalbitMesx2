package com.ghalbitnet.meshx2

import android.app.Application
import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager
import com.ghalbitnet.meshx2.identity.IdentitySyncManager
import com.ghalbitnet.meshx2.online.GhalbitStartupScheduler

class GhalbitMeshApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MeshRuntimeManager.initialize(this)
        IdentitySyncManager.initialize(this)
        GhalbitStartupScheduler.schedule(this)
    }
}
