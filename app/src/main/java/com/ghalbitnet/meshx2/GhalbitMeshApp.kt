package com.ghalbitnet.meshx2

import android.app.Application
import com.ghalbitnet.meshx2.activityfeed.ActivityFeedManager
import com.ghalbitnet.meshx2.activityfeed.ActivityFeedType
import com.ghalbitnet.meshx2.core.runtime.MeshRuntimeManager
import com.ghalbitnet.meshx2.identity.IdentitySyncManager
import com.ghalbitnet.meshx2.online.GhalbitStartupScheduler

class GhalbitMeshApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ActivityFeedManager.bind(this)
        ActivityFeedManager.publish(
            type = ActivityFeedType.RUNTIME_EVENT,
            title = "Aplikasi aktif",
            message = "Activity Feed siap merekam aktivitas GHALBIT Mesh X2.",
            source = "GhalbitMeshApp"
        )
        MeshRuntimeManager.initialize(this)
        IdentitySyncManager.initialize(this)
        GhalbitStartupScheduler.schedule(this)
    }
}
