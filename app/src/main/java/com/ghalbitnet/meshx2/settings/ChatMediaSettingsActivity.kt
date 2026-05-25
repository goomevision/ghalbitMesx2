package com.ghalbitnet.meshx2.settings

import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.ghalbitnet.meshx2.R

class ChatMediaSettingsActivity : AppCompatActivity() {

    private lateinit var seekPhotoQuality: SeekBar
    private lateinit var txtPhotoQualityValue: TextView
    private lateinit var txtMediaLimitSummary: TextView
    private lateinit var switchMediaSaver: SwitchCompat
    private lateinit var switchKeepCapturedPhotos: SwitchCompat
    private lateinit var switchImagePreview: SwitchCompat
    private lateinit var switchAutoSaveIncomingMedia: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_media_settings)

        seekPhotoQuality = findViewById(R.id.seekPhotoQuality)
        txtPhotoQualityValue = findViewById(R.id.txtPhotoQualityValue)
        txtMediaLimitSummary = findViewById(R.id.txtMediaLimitSummary)
        switchMediaSaver = findViewById(R.id.switchMediaSaver)
        switchKeepCapturedPhotos = findViewById(R.id.switchKeepCapturedPhotos)
        switchImagePreview = findViewById(R.id.switchImagePreview)
        switchAutoSaveIncomingMedia = findViewById(R.id.switchAutoSaveIncomingMedia)

        bindState()
        bindActions()
    }

    private fun bindState() {
        seekPhotoQuality.max = 2
        seekPhotoQuality.progress = ChatMediaSettingsManager.getPhotoQualityLevel(this)
        switchMediaSaver.isChecked = ChatMediaSettingsManager.isMediaDataSaverEnabled(this)
        switchKeepCapturedPhotos.isChecked = ChatMediaSettingsManager.shouldKeepCapturedPhotos(this)
        switchImagePreview.isChecked = ChatMediaSettingsManager.shouldShowImagePreview(this)
        switchAutoSaveIncomingMedia.isChecked = ChatMediaSettingsManager.shouldAutoSaveIncomingMedia(this)

        updatePhotoQualityLabel(seekPhotoQuality.progress)
        updateLimitSummary()
    }

    private fun bindActions() {
        seekPhotoQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                val safeProgress = progress.coerceIn(0, 2)
                if (safeProgress != progress) {
                    seekBar?.progress = safeProgress
                    return
                }

                ChatMediaSettingsManager.setPhotoQualityLevel(this@ChatMediaSettingsActivity, safeProgress)
                updatePhotoQualityLabel(safeProgress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        switchMediaSaver.setOnCheckedChangeListener { _, isChecked ->
            ChatMediaSettingsManager.setMediaDataSaverEnabled(this, isChecked)
            updateLimitSummary()
        }

        switchKeepCapturedPhotos.setOnCheckedChangeListener { _, isChecked ->
            ChatMediaSettingsManager.setKeepCapturedPhotos(this, isChecked)
        }

        switchImagePreview.setOnCheckedChangeListener { _, isChecked ->
            ChatMediaSettingsManager.setShowImagePreview(this, isChecked)
        }

        switchAutoSaveIncomingMedia.setOnCheckedChangeListener { _, isChecked ->
            ChatMediaSettingsManager.setAutoSaveIncomingMedia(this, isChecked)
        }
    }

    private fun updatePhotoQualityLabel(level: Int) {
        val labelRes =
            when (level) {
                0 -> R.string.chat_media_settings_quality_low
                2 -> R.string.chat_media_settings_quality_high
                else -> R.string.chat_media_settings_quality_medium
            }
        val qualityPercent = ChatMediaSettingsManager.getPhotoQualityPercent(this)
        txtPhotoQualityValue.text = getString(labelRes, qualityPercent)
    }

    private fun updateLimitSummary() {
        txtMediaLimitSummary.text =
            getString(
                R.string.chat_media_settings_limit_summary,
                formatMb(ChatMediaSettingsManager.maxAllowedSizeFor(this, "image/jpeg")),
                formatMb(ChatMediaSettingsManager.maxAllowedSizeFor(this, "audio/aac")),
                formatMb(ChatMediaSettingsManager.maxAllowedSizeFor(this, "video/mp4")),
                formatMb(ChatMediaSettingsManager.maxAllowedSizeFor(this, "application/pdf"))
            )
    }

    private fun formatMb(bytes: Long): Long = bytes / 1024L / 1024L
}
