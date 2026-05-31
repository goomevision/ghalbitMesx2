package com.ghalbitnet.meshx2.ui.contact

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import com.ghalbitnet.meshx2.R

/**
 * PHASE 250C — Contact Carousel Home Integration.
 *
 * Registers a lightweight lifecycle hook that attaches HomeContactCarouselView
 * into MainActivity after the Wallet Hero Card. This avoids replacing the large
 * activity_main.xml file while still making the carousel visible in the APK.
 */
object HomeContactCarouselInstaller {
    private const val TAG_VIEW = "GHALBIT_HOME_CONTACT_CAROUSEL"
    private var registered = false

    fun register(application: Application) {
        if (registered) return
        registered = true
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityResumed(activity: Activity) {
                    if (activity.javaClass.name != "com.ghalbitnet.meshx2.MainActivity") return
                    activity.window.decorView.post { attach(activity) }
                }
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    private fun attach(activity: Activity) {
        val scroll = activity.findViewById<ScrollView?>(R.id.mainScroll) ?: return
        val container = scroll.getChildAt(0) as? LinearLayout ?: return
        if (hasCarousel(container)) return

        val carousel = HomeContactCarouselView(activity).apply { tag = TAG_VIEW }
        val insertIndex = findWalletHeroIndex(container).let { index ->
            if (index >= 0) index + 1 else container.childCount
        }
        container.addView(carousel, insertIndex.coerceIn(0, container.childCount))
    }

    private fun hasCarousel(container: LinearLayout): Boolean {
        for (i in 0 until container.childCount) {
            if (container.getChildAt(i).tag == TAG_VIEW) return true
        }
        return false
    }

    private fun findWalletHeroIndex(container: LinearLayout): Int {
        for (i in 0 until container.childCount) {
            if (container.getChildAt(i).id == R.id.walletHeroCard) return i
        }
        return -1
    }
}
