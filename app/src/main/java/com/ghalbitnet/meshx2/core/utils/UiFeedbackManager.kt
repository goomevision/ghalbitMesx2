package com.ghalbitnet.meshx2.core.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object UiFeedbackManager {

    fun showToast(
        context: Context,
        message: String,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(context, message, duration).show()
            return
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, duration).show()
        }
    }
}
