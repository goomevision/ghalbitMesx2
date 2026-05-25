package com.ghalbitnet.meshx2.core.utils

import android.app.Activity
import android.content.Intent
import android.widget.Toast

object SafeNavigator {

    fun open(
        activity: Activity,
        target: Class<*>,
        failMessage: String = "Halaman belum tersedia"
    ) {
        try {
            activity.startActivity(
                Intent(activity, target)
            )
        } catch (e: Exception) {
            Toast.makeText(
                activity,
                failMessage,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
