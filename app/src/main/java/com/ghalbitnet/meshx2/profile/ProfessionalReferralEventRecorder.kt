package com.ghalbitnet.meshx2.profile

import android.content.Context
import android.util.Log

object ProfessionalReferralEventRecorder {
    private const val TAG = "GHALBIT-REFERRAL-EVENT"

    private enum class EventType(val key: String) {
        SEEN("referral_seen"),
        SAVED_CONTACT("referral_saved_contact"),
        VERIFIED("referral_verified"),
        JOINED("referral_joined"),
        REWARD_PENDING("referral_reward_pending"),
        REWARDED("referral_rewarded")
    }

    fun recordReferralSeen(context: Context, sourceGhalbitId: String?, targetGhalbitId: String?) {
        record(context, EventType.SEEN, sourceGhalbitId, targetGhalbitId)
    }

    fun recordReferralSavedContact(context: Context, sourceGhalbitId: String?, targetGhalbitId: String?) {
        record(context, EventType.SAVED_CONTACT, sourceGhalbitId, targetGhalbitId)
    }

    fun recordReferralVerified(context: Context, sourceGhalbitId: String?, targetGhalbitId: String?) {
        record(context, EventType.VERIFIED, sourceGhalbitId, targetGhalbitId)
    }

    fun recordReferralJoined(context: Context, sourceGhalbitId: String?, targetGhalbitId: String?) {
        record(context, EventType.JOINED, sourceGhalbitId, targetGhalbitId)
    }

    fun recordReferralRewarded(context: Context, sourceGhalbitId: String?, targetGhalbitId: String?) {
        record(context, EventType.REWARDED, sourceGhalbitId, targetGhalbitId)
    }

    fun recordReferralRewardPending(context: Context, sourceGhalbitId: String?, targetGhalbitId: String?, txId: String?) {
        record(context, EventType.REWARD_PENDING, sourceGhalbitId, targetGhalbitId, txId)
    }

    fun recordReferralRewarded(context: Context, sourceGhalbitId: String?, targetGhalbitId: String?, txId: String?) {
        record(context, EventType.REWARDED, sourceGhalbitId, targetGhalbitId, txId)
    }

    private fun record(
        context: Context,
        type: EventType,
        sourceGhalbitId: String?,
        targetGhalbitId: String?,
        txId: String? = null
    ) {
        val source = normalizeId(sourceGhalbitId)
        val target = normalizeId(targetGhalbitId)
        if (source == null || target == null || source == target) {
            Log.d(TAG, "referral event skipped invalid type=${type.key} source=${source ?: "-"} target=${target ?: "-"}")
            return
        }
        val profile = ProfileRepository.getResolvedContact(
            context = context,
            globalId = source,
            chatId = source,
            fallbackDisplayName = source
        )
        val oldTags = profile.localTags.map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
        oldTags.add("referral:$source")
        oldTags.add("sponsor:$source")
        val txSuffix = txId?.trim().orEmpty().takeIf { it.isNotBlank() }?.let { ":tx:$it" }.orEmpty()
        val eventTag = "${type.key}:$target$txSuffix"
        if (!oldTags.add(eventTag)) {
            Log.d(TAG, "referral event skipped duplicate type=${type.key} source=$source target=$target")
            return
        }
        ProfileRepository.saveLocalAlias(
            context = context,
            globalId = source,
            chatId = source,
            publicDisplayName = profile.displayName,
            publicNickname = profile.nickname,
            localAlias = profile.localAlias,
            localNote = profile.localNote,
            communityLabel = profile.communityLabel,
            savedAsName = profile.savedAsName,
            localTags = oldTags.toList(),
            favorite = profile.isFavorite,
            pinned = profile.isPinned
        )
        Log.d(TAG, "referral event recorded type=${type.key} source=$source target=$target")
    }

    private fun normalizeId(value: String?): String? {
        val id = value?.trim().orEmpty()
        return id.takeIf { it.isNotBlank() }
    }
}
