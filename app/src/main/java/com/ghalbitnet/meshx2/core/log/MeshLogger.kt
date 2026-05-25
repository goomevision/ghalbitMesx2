package com.ghalbitnet.meshx2.core.log

import android.util.Log

/**
 * =====================================================
 * GHALBIT MESH X2
 * MESH LOGGER
 * =====================================================
 */

object MeshLogger {

    private const val PREFIX = "GHALBIT"

    fun d(
        module: String,
        message: String
    ) {
        LocalLogBuffer.add("D/$module: $message")
        Log.d("$PREFIX-$module", message)
    }

    fun i(
        module: String,
        message: String
    ) {
        LocalLogBuffer.add("I/$module: $message")
        Log.i("$PREFIX-$module", message)
    }

    fun w(
        module: String,
        message: String
    ) {
        LocalLogBuffer.add("W/$module: $message")
        Log.w("$PREFIX-$module", message)
    }

    fun e(
        module: String,
        message: String,
        throwable: Throwable? = null
    ) {
        LocalLogBuffer.add("E/$module: $message")

        if (throwable != null) {
            Log.e("$PREFIX-$module", message, throwable)
        } else {
            Log.e("$PREFIX-$module", message)
        }
    }
}
