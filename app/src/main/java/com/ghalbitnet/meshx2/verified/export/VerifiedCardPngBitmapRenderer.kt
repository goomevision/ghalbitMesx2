package com.ghalbitnet.meshx2.verified.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import com.ghalbitnet.meshx2.profile.ProfileQrCodec
import com.ghalbitnet.meshx2.verified.ui.ProfessionalCardUiModel
import kotlin.math.min

object VerifiedCardPngBitmapRenderer {
    fun render(context: Context, model: ProfessionalCardUiModel, width: Int = 1680, height: Int = 2200): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#050C1B"))

        val outer = RectF(40f, 40f, width - 40f, height - 40f)
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(outer.left, outer.top, outer.right, outer.bottom,
                intArrayOf(Color.parseColor("#081A3D"), Color.parseColor("#0A1D44"), Color.parseColor("#071636")), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(outer, 34f, 34f, outerPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.parseColor("#D6AA4D")
            alpha = 220
        }
        canvas.drawRoundRect(outer, 34f, 34f, borderPaint)

        val topRect = RectF(58f, 58f, width - 58f, 1380f)
        val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(topRect.left, topRect.top, topRect.right, topRect.bottom,
                intArrayOf(Color.parseColor("#123A77"), Color.parseColor("#0D2D5C"), Color.parseColor("#0A2248")), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(topRect, 28f, 28f, topPaint)

        val stripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(topRect.left, topRect.top, topRect.right, topRect.top + 130f,
                intArrayOf(Color.parseColor("#2A72C9"), Color.parseColor("#1D58AE")), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(topRect.left, topRect.top, topRect.right, topRect.top + 112f), 24f, 24f, stripPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 62f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E1B75A")
            textSize = 42f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("GHALBITNET", width / 2f, 130f, titlePaint)
        canvas.drawText("TRUSTED IDENTITY", width / 2f, 182f, subTitlePaint)

        val avatarCx = 290f
        val avatarCy = 560f
        val avatarRadius = 190f
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            color = Color.parseColor("#E4BD66")
        }
        canvas.drawCircle(avatarCx, avatarCy, avatarRadius + 14f, ringPaint)
        val avatarPath = Path().apply { addCircle(avatarCx, avatarCy, avatarRadius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(avatarPath)
        val photo = loadPhoto(context, model.profilePhotoUri)
        if (photo != null) {
            val dst = RectF(avatarCx - avatarRadius, avatarCy - avatarRadius, avatarCx + avatarRadius, avatarCy + avatarRadius)
            canvas.drawBitmap(photo, null, dst, null)
        } else {
            val fallback = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(avatarCx - avatarRadius, avatarCy - avatarRadius, avatarCx + avatarRadius, avatarCy + avatarRadius,
                    intArrayOf(Color.parseColor("#2A5FA5"), Color.parseColor("#1C345D")), null, Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(avatarCx, avatarCy, avatarRadius, fallback)
            val initial = model.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "G"
            val initialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 160f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(initial, avatarCx, avatarCy + 55f, initialPaint)
        }
        canvas.restore()

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (model.verified) Color.parseColor("#D9B24C") else Color.parseColor("#355C8E") }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 38f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 82f; typeface = Typeface.DEFAULT_BOLD }
        val rolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F4C45E"); textSize = 56f; typeface = Typeface.DEFAULT_BOLD }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EAF3FF"); textSize = 52f }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C3D5EF"); textSize = 42f }

        val leftX = 520f
        val badgeRect = RectF(leftX, 300f, leftX + 320f, 388f)
        canvas.drawRoundRect(badgeRect, 30f, 30f, badgePaint)
        canvas.drawText(verificationBadge(model), badgeRect.centerX(), 359f, badgeTextPaint)

        canvas.drawText(model.displayName, leftX, 530f, namePaint)
        canvas.drawText(model.role, leftX, 620f, rolePaint)
        drawSingleLineTrimmed(canvas, model.community, leftX, 700f, 520f, bodyPaint)
        drawSingleLineTrimmed(canvas, "GHALBITNET ID: ${model.globalId}", leftX, 780f, 520f, metaPaint)
        drawSingleLineTrimmed(canvas, model.region, leftX, 844f, 520f, metaPaint)
        drawWrappedText(canvas, "\u201cMembangun jaringan komunitas dan komunikasi terdesentralisasi.\u201d", leftX, 922f, 520f, metaPaint, 2)

        val mentorTier = model.tier.name
        val mentorRect = RectF(width - 420f, 208f, width - 120f, 438f)
        val mentorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(mentorRect.left, mentorRect.top, mentorRect.right, mentorRect.bottom,
                intArrayOf(Color.parseColor("#2C2213"), Color.parseColor("#15110B")), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(mentorRect, 24f, 24f, mentorPaint)
        canvas.drawRoundRect(mentorRect, 24f, 24f, borderPaint)
        canvas.drawText("MENTOR", mentorRect.centerX(), mentorRect.centerY() - 12f, badgeTextPaint)
        canvas.drawText(mentorTier, mentorRect.centerX(), mentorRect.centerY() + 42f, metaPaint.apply { textAlign = Paint.Align.CENTER })

        val scoreRect = RectF(width - 585f, 520f, width - 120f, 820f)
        val scoreBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(scoreRect.left, scoreRect.top, scoreRect.right, scoreRect.bottom,
                intArrayOf(Color.parseColor("#091A35"), Color.parseColor("#0E2446")), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(scoreRect, 24f, 24f, scoreBg)
        canvas.drawRoundRect(scoreRect, 24f, 24f, borderPaint)
        val scoreTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E4BD66"); textSize = 48f; textAlign = Paint.Align.CENTER }
        val scoreValue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD67A"); textSize = 92f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        canvas.drawText("TRUST SCORE", scoreRect.centerX(), scoreRect.top + 74f, scoreTitle)
        canvas.drawText("${model.trustScore}/100", scoreRect.centerX(), scoreRect.centerY() + 18f, scoreValue)

        val qrContent = model.qrPayload?.takeIf { it.isNotBlank() } ?: model.globalId
        val qrBitmap = ProfileQrCodec.renderBitmap(qrContent, 460)
        val qrLeft = width - 620f
        val qrTop = 860f
        val qrGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E7C97B"); alpha = 48 }
        canvas.drawRoundRect(RectF(qrLeft - 28f, qrTop - 28f, qrLeft + 488f, qrTop + 488f), 30f, 30f, qrGlowPaint)
        val qrBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(RectF(qrLeft - 14f, qrTop - 14f, qrLeft + 474f, qrTop + 474f), 22f, 22f, qrBgPaint)
        canvas.drawBitmap(qrBitmap, qrLeft, qrTop, null)
        val qrLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8C87D"); textSize = 36f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        canvas.drawText("SCAN TO VERIFY", qrLeft + 230f, qrTop + 548f, qrLabel)

        val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val chipY = 1320f
        drawChip(canvas, RectF(110f, chipY - 52f, 390f, chipY + 16f), Color.parseColor("#225FAE"), "VERIFIED", chipTextPaint)
        drawChip(canvas, RectF(430f, chipY - 52f, 730f, chipY + 16f), Color.parseColor("#2F7B47"), "TRUSTED", chipTextPaint)
        drawChip(canvas, RectF(770f, chipY - 52f, 1070f, chipY + 16f), Color.parseColor("#5A3A8E"), "MENTOR", chipTextPaint)

        val bottomRect = RectF(58f, 1410f, width - 58f, height - 130f)
        val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(bottomRect.left, bottomRect.top, bottomRect.right, bottomRect.bottom,
                intArrayOf(Color.parseColor("#071530"), Color.parseColor("#041126")), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(bottomRect, 28f, 28f, bottomPaint)
        canvas.drawRoundRect(bottomRect, 28f, 28f, borderPaint)

        val sectionTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E2B657"); textSize = 44f; typeface = Typeface.DEFAULT_BOLD }
        val sectionBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8EEF9"); textSize = 38f }

        val col1X = 120f
        val col2X = 680f
        val col3X = 1080f
        val startY = 1510f

        canvas.drawText("TENTANG SAYA", col1X, startY, sectionTitle)
        drawWrappedText(canvas, "Saya membangun ekosistem komunikasi mesh dan jaringan komunitas yang aman.", col1X, startY + 60f, 500f, sectionBody, 3)

        canvas.drawText("STATISTIK", col2X, startY, sectionTitle)
        canvas.drawText("Koneksi: 128", col2X, startY + 62f, sectionBody)
        canvas.drawText("Komunitas: 15", col2X, startY + 114f, sectionBody)
        canvas.drawText("Trust: ${model.trustScore}/100", col2X, startY + 166f, sectionBody)

        canvas.drawText("KONTAK PUBLIK", col3X, startY, sectionTitle)
        canvas.drawText("WA/CHAT: Tersedia", col3X, startY + 62f, sectionBody)
        drawSingleLineTrimmed(canvas, "Lokasi: ${model.region}", col3X, startY + 114f, 440f, sectionBody)

        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D4A94E")
            textSize = 30f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BFD4EE")
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(model.globalId, width / 2f, 1370f, smallPaint)
        canvas.drawText("KARTU INI AMAN DIBAGIKAN • DATA TERLINDUNGI • PRIVASI TERJAGA", width / 2f, height - 64f, footer)

        return bitmap
    }

    private fun drawChip(canvas: Canvas, rect: RectF, color: Int, text: String, textPaint: Paint) {
        val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(rect, 24f, 24f, chip)
        canvas.drawText(text, rect.centerX(), rect.centerY() + 12f, textPaint)
    }

    private fun drawSingleLineTrimmed(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        var out = text
        if (paint.measureText(out) <= maxWidth) {
            canvas.drawText(out, x, y, paint)
            return
        }
        while (out.length > 4 && paint.measureText("$out...") > maxWidth) {
            out = out.dropLast(1)
        }
        canvas.drawText("$out...", x, y, paint)
    }

    private fun drawWrappedText(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint, maxLines: Int) {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines += current
                current = word
            }
            if (lines.size >= maxLines) break
        }
        if (current.isNotEmpty() && lines.size < maxLines) lines += current
        val visibleLines = lines.take(maxLines)
        for (i in visibleLines.indices) {
            canvas.drawText(visibleLines[i], x, y + (i * (paint.textSize + 10f)), paint)
        }
    }


    private fun verificationBadge(model: ProfessionalCardUiModel): String {
        return when (model.verificationStatus) {
            com.ghalbitnet.meshx2.profile.ProfileVerificationStatus.VALID_SIGNATURE -> "✓ VERIFIED"
            com.ghalbitnet.meshx2.profile.ProfileVerificationStatus.INVALID_SIGNATURE -> "INVALID"
            com.ghalbitnet.meshx2.profile.ProfileVerificationStatus.UNSIGNED -> "UNSIGNED"
            com.ghalbitnet.meshx2.profile.ProfileVerificationStatus.UNKNOWN -> "UNKNOWN"
        }
    }

    private fun loadPhoto(context: Context, photoUri: String?): Bitmap? {
        if (photoUri.isNullOrBlank()) return null
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()
    }
}
