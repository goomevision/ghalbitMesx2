package com.ghalbitnet.meshx2.settings

import android.content.Context

object ChatMediaSettingsManager {
    private const val PREFS_NAME = "chat_media_settings"
    private const val KEY_PHOTO_QUALITY_LEVEL = "photo_quality_level"
    private const val KEY_MEDIA_DATA_SAVER = "media_data_saver"
    private const val KEY_KEEP_CAPTURED_PHOTOS = "keep_captured_photos"
    private const val KEY_SHOW_IMAGE_PREVIEW = "show_image_preview"
    private const val KEY_AUTO_SAVE_INCOMING_MEDIA = "auto_save_incoming_media"

    private const val PHOTO_QUALITY_LOW = 72
    private const val PHOTO_QUALITY_MEDIUM = 84
    private const val PHOTO_QUALITY_HIGH = 92

    private const val DEFAULT_IMAGE_SIZE = 5L * 1024L * 1024L
    private const val DEFAULT_AUDIO_SIZE = 8L * 1024L * 1024L
    private const val DEFAULT_VIDEO_SIZE = 16L * 1024L * 1024L
    private const val DEFAULT_DOCUMENT_SIZE = 20L * 1024L * 1024L

    private const val SAVER_IMAGE_SIZE = 3L * 1024L * 1024L
    private const val SAVER_AUDIO_SIZE = 6L * 1024L * 1024L
    private const val SAVER_VIDEO_SIZE = 10L * 1024L * 1024L
    private const val SAVER_DOCUMENT_SIZE = 12L * 1024L * 1024L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPhotoQualityLevel(context: Context): Int =
        prefs(context).getInt(KEY_PHOTO_QUALITY_LEVEL, 1).coerceIn(0, 2)

    fun setPhotoQualityLevel(
        context: Context,
        level: Int
    ) {
        prefs(context).edit().putInt(KEY_PHOTO_QUALITY_LEVEL, level.coerceIn(0, 2)).apply()
    }

    fun getPhotoQualityPercent(context: Context): Int =
        when (getPhotoQualityLevel(context)) {
            0 -> PHOTO_QUALITY_LOW
            2 -> PHOTO_QUALITY_HIGH
            else -> PHOTO_QUALITY_MEDIUM
        }

    fun isMediaDataSaverEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MEDIA_DATA_SAVER, false)

    fun setMediaDataSaverEnabled(
        context: Context,
        enabled: Boolean
    ) {
        prefs(context).edit().putBoolean(KEY_MEDIA_DATA_SAVER, enabled).apply()
    }

    fun shouldKeepCapturedPhotos(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEEP_CAPTURED_PHOTOS, true)

    fun setKeepCapturedPhotos(
        context: Context,
        enabled: Boolean
    ) {
        prefs(context).edit().putBoolean(KEY_KEEP_CAPTURED_PHOTOS, enabled).apply()
    }

    fun shouldShowImagePreview(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_IMAGE_PREVIEW, true)

    fun setShowImagePreview(
        context: Context,
        enabled: Boolean
    ) {
        prefs(context).edit().putBoolean(KEY_SHOW_IMAGE_PREVIEW, enabled).apply()
    }

    fun shouldAutoSaveIncomingMedia(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_SAVE_INCOMING_MEDIA, false)

    fun setAutoSaveIncomingMedia(
        context: Context,
        enabled: Boolean
    ) {
        prefs(context).edit().putBoolean(KEY_AUTO_SAVE_INCOMING_MEDIA, enabled).apply()
    }

    fun maxAllowedSizeFor(
        context: Context,
        mimeType: String
    ): Long {
        val saverEnabled = isMediaDataSaverEnabled(context)
        return when {
            mimeType.startsWith("image/") -> if (saverEnabled) SAVER_IMAGE_SIZE else DEFAULT_IMAGE_SIZE
            mimeType.startsWith("audio/") -> if (saverEnabled) SAVER_AUDIO_SIZE else DEFAULT_AUDIO_SIZE
            mimeType.startsWith("video/") -> if (saverEnabled) SAVER_VIDEO_SIZE else DEFAULT_VIDEO_SIZE
            else -> if (saverEnabled) SAVER_DOCUMENT_SIZE else DEFAULT_DOCUMENT_SIZE
        }
    }
}
