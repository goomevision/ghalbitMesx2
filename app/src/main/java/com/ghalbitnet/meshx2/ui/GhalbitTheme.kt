package com.ghalbitnet.meshx2.ui

import android.app.Activity
import android.util.Log

object GhalbitTheme {
    fun applyWindow(activity: Activity, surfaceName: String) {
        activity.window.statusBarColor = GhalbitColors.SCREEN_START
        activity.window.navigationBarColor = GhalbitColors.SCREEN_END
        Log.d("GHALBIT-UI-THEME", "applied surface=$surfaceName")
    }

    fun logCardRendered(name: String) {
        Log.d("GHALBIT-UI-CARD", "rendered $name")
    }
}
