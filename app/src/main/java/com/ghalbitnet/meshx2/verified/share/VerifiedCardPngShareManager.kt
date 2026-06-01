package com.ghalbitnet.meshx2.verified.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ghalbitnet.meshx2.profile.CommunityProfile
import com.ghalbitnet.meshx2.profile.ProfileQrCodec
import com.ghalbitnet.meshx2.profile.ProfileSyncManager
import com.ghalbitnet.meshx2.profile.ProfessionalCardDataMapper
import com.ghalbitnet.meshx2.verified.export.VerifiedCardPngBitmapRenderer
import com.ghalbitnet.meshx2.verified.ui.ProfessionalCardUiModel
import java.io.File
import java.io.FileOutputStream

object VerifiedCardPngShareManager {
    fun modelFromProfile(profile: CommunityProfile): ProfessionalCardUiModel {
        val mapped = ProfessionalCardDataMapper.fromProfile(profile)
        val qrPayload = ProfileQrCodec.encode(ProfileSyncManager.buildQrPayload(profile, profile.routeHint))
        return mapped.model.copy(qrPayload = qrPayload)
    }

    fun savePngToCache(context: Context, model: ProfessionalCardUiModel): File {
        val dir = File(context.cacheDir, "verified_cards").apply { mkdirs() }
        val safeId = model.globalId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "ghalbit_verified_$safeId.png")
        val bitmap = VerifiedCardPngBitmapRenderer.render(context, model)
        FileOutputStream(file).use { output ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
        return file
    }

    fun createSharePngIntent(context: Context, model: ProfessionalCardUiModel): Intent {
        val file = savePngToCache(context, model)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
