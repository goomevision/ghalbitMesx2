package com.ghalbitnet.meshx2.verified.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.ghalbitnet.meshx2.verified.ui.ProfessionalCardUiModel

object VerifiedCardPngBitmapRenderer {
    fun render(model: ProfessionalCardUiModel, width: Int = 1080, height: Int = 1350): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 48f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 36f
            textAlign = Paint.Align.CENTER
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("GHALBIT VERIFIED CARD", width / 2f, 130f, titlePaint)
        canvas.drawText(model.displayName, width / 2f, 260f, titlePaint)
        canvas.drawText(model.role, width / 2f, 330f, bodyPaint)
        canvas.drawText(model.community, width / 2f, 395f, bodyPaint)
        canvas.drawText(if (model.verified) "VERIFIED ✓" else "UNVERIFIED", width / 2f, 500f, bodyPaint)
        canvas.drawText("Trust Score: ${model.trustScore}", width / 2f, 570f, bodyPaint)
        canvas.drawText(model.globalId, width / 2f, height - 120f, smallPaint)
        return bitmap
    }
}
