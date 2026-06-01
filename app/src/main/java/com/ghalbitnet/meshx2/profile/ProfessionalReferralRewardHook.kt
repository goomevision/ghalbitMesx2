package com.ghalbitnet.meshx2.profile

import android.content.Context
import android.util.Log

object ProfessionalReferralRewardHook {
    private const val TAG = "GHALBIT-REFERRAL-REWARD"

    fun onReferralRewardSettled(
        context: Context,
        sourceGhalbitId: String?,
        targetGhalbitId: String?,
        txId: String?
    ) {
        val normalizedTx = txId?.trim().orEmpty()
        if (normalizedTx.isBlank()) {
            Log.d(TAG, "settled skipped invalid txId")
            return
        }
        ProfessionalReferralEventRecorder.recordReferralRewarded(
            context = context,
            sourceGhalbitId = sourceGhalbitId,
            targetGhalbitId = targetGhalbitId,
            txId = normalizedTx
        )
    }

    fun onReferralRewardPending(
        context: Context,
        sourceGhalbitId: String?,
        targetGhalbitId: String?,
        txId: String?
    ) {
        val normalizedTx = txId?.trim().orEmpty()
        if (normalizedTx.isBlank()) {
            Log.d(TAG, "pending skipped invalid txId")
            return
        }
        ProfessionalReferralEventRecorder.recordReferralRewardPending(
            context = context,
            sourceGhalbitId = sourceGhalbitId,
            targetGhalbitId = targetGhalbitId,
            txId = normalizedTx
        )
    }
}

