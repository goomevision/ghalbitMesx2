package com.ghalbitnet.meshx2.online

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ghalbitnet.meshx2.call.CallSignalQueue
import com.ghalbitnet.meshx2.chat.ChatDeliveryManager
import java.util.concurrent.TimeUnit

class GhalbitBackgroundSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val power = PowerAwareSyncManager.snapshot(applicationContext)
        Log.d("GHALBIT-BG", "sync start")
        if (power.lowPowerMode && !power.charging) {
            Log.d("GHALBIT-BG", "sync skipped battery")
            return Result.success()
        }
        return runCatching {
            val report = ChatDeliveryManager.syncNow(applicationContext, reason = "worker")
            val callSignals = CallSignalQueue.flushDue(applicationContext)
            Log.d("GHALBIT-BG", "inbox pulled count=${report.inboxMessages}")
            Log.d("GHALBIT-BG", "retry pending id=$callSignals")
            Log.d("GHALBIT-BG", "sync complete")
            Result.success()
        }.getOrElse {
            Log.e("GHALBIT-BG", "sync failed", it)
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "ghalbit_background_sync"

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<GhalbitBackgroundSyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
