package com.ghalbitnet.meshx2.token

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.server.FirebaseEconomySyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoucherSheetActivity : AppCompatActivity() {

    private enum class VoucherFilter {
        ALL,
        READY,
        USED,
        EXPIRED
    }

    companion object {
        private const val EXTRA_GLOBAL_ID = "issuer_global_id"

        private data class PrintFooterInfo(
            val batchId: String,
            val operatorName: String,
            val locationName: String,
            val note: String
        )

        fun createIntent(
            context: Context,
            issuerGlobalId: String
        ): Intent {
            return Intent(context, VoucherSheetActivity::class.java).apply {
                putExtra(EXTRA_GLOBAL_ID, issuerGlobalId)
            }
        }
    }

    private lateinit var issuerGlobalId: String
    private lateinit var txtVoucherSummary: TextView
    private lateinit var voucherContainer: LinearLayout
    private lateinit var btnVoucherFilterAll: TextView
    private lateinit var btnVoucherFilterReady: TextView
    private lateinit var btnVoucherFilterUsed: TextView
    private lateinit var btnVoucherFilterExpired: TextView
    private lateinit var btnVoucherPrint: TextView
    private var brandBitmap: Bitmap? = null
    private var activeFilter: VoucherFilter = VoucherFilter.ALL
    private var currentDisplayedVouchers: List<VoucherQrManager.VoucherRecord> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voucher_sheet)

        issuerGlobalId = intent.getStringExtra(EXTRA_GLOBAL_ID).orEmpty()
        txtVoucherSummary = findViewById(R.id.txtVoucherSummary)
        voucherContainer = findViewById(R.id.voucherContainer)
        btnVoucherFilterAll = findViewById(R.id.btnVoucherFilterAll)
        btnVoucherFilterReady = findViewById(R.id.btnVoucherFilterReady)
        btnVoucherFilterUsed = findViewById(R.id.btnVoucherFilterUsed)
        btnVoucherFilterExpired = findViewById(R.id.btnVoucherFilterExpired)
        btnVoucherPrint = findViewById(R.id.btnVoucherPrint)

        btnVoucherFilterAll.setOnClickListener {
            activeFilter = VoucherFilter.ALL
            renderVouchers()
        }
        btnVoucherFilterReady.setOnClickListener {
            activeFilter = VoucherFilter.READY
            renderVouchers()
        }
        btnVoucherFilterUsed.setOnClickListener {
            activeFilter = VoucherFilter.USED
            renderVouchers()
        }
        btnVoucherFilterExpired.setOnClickListener {
            activeFilter = VoucherFilter.EXPIRED
            renderVouchers()
        }
        btnVoucherPrint.setOnClickListener {
            printCurrentVouchers()
        }

        brandBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_ghalbitx_logo)

        renderVouchers()
    }

    override fun onResume() {
        super.onResume()
        renderVouchers()
    }

    private fun renderVouchers() {
        val vouchers =
            VoucherQrManager.vouchersForIssuer(this, issuerGlobalId)
        val available =
            vouchers.count {
                !it.redeemed && !it.revoked && it.expiresAt > System.currentTimeMillis()
            }
        val redeemed = vouchers.size - available

        txtVoucherSummary.text =
            getString(
                R.string.voucher_sheet_summary,
                vouchers.size,
                available,
                redeemed
            )

        val filteredVouchers =
            vouchers.filter { voucher ->
                val expired = !voucher.redeemed && !voucher.revoked && System.currentTimeMillis() > voucher.expiresAt
                when (activeFilter) {
                    VoucherFilter.ALL -> true
                    VoucherFilter.READY -> !voucher.redeemed && !voucher.revoked && !expired
                    VoucherFilter.USED -> voucher.redeemed
                    VoucherFilter.EXPIRED -> voucher.revoked || expired
                }
            }
        currentDisplayedVouchers = filteredVouchers
        updateFilterLabels()

        voucherContainer.removeAllViews()
        if (filteredVouchers.isEmpty()) {
            voucherContainer.addView(
                TextView(this).apply {
                    text = getString(
                        if (vouchers.isEmpty()) {
                            R.string.voucher_sheet_empty
                        } else {
                            R.string.voucher_sheet_filter_empty
                        }
                    )
                    setTextColor(Color.parseColor("#E8F1F8"))
                    textSize = 14f
                }
            )
            return
        }

        val formatter =
            SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        filteredVouchers.forEach { voucher ->
            val card =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(20, 20, 20, 20)
                    setBackgroundColor(Color.parseColor("#101820"))
                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = 16
                        }
                }

            card.addView(
                TextView(this).apply {
                    text = getString(R.string.voucher_sheet_amount, voucher.amount)
                    setTextColor(Color.parseColor("#9FE870"))
                    textSize = 18f
                }
            )
            card.addView(
                TextView(this).apply {
                    text = getString(
                        R.string.voucher_sheet_status,
                        when {
                            voucher.redeemed -> getString(R.string.voucher_sheet_status_used)
                            voucher.revoked -> getString(R.string.voucher_sheet_status_revoked)
                            System.currentTimeMillis() > voucher.expiresAt -> getString(R.string.voucher_sheet_status_expired)
                            else -> getString(R.string.voucher_sheet_status_ready)
                        }
                    )
                    setTextColor(Color.WHITE)
                    textSize = 14f
                }
            )
            card.addView(
                TextView(this).apply {
                    text = getString(
                        R.string.voucher_sheet_serial,
                        VoucherQrManager.buildDisplaySerial(voucher)
                    )
                    setTextColor(Color.parseColor("#9FE870"))
                    textSize = 13f
                }
            )
            card.addView(
                TextView(this).apply {
                    text = getString(R.string.voucher_sheet_code, voucher.code.take(16))
                    setTextColor(Color.parseColor("#FFD54F"))
                    textSize = 13f
                }
            )
            card.addView(
                TextView(this).apply {
                    text = getString(
                        R.string.voucher_sheet_created,
                        formatVoucherDate(voucher.createdAt, formatter)
                    )
                    setTextColor(Color.parseColor("#E8F1F8"))
                    textSize = 13f
                }
            )
            card.addView(
                TextView(this).apply {
                    text = getString(
                        R.string.voucher_sheet_expires,
                        formatVoucherDate(voucher.expiresAt, formatter)
                    )
                    setTextColor(Color.parseColor("#E8F1F8"))
                    textSize = 13f
                }
            )
            card.addView(
                TextView(this).apply {
                    text =
                        when (FirebaseEconomySyncManager.statusForVoucherCode(this@VoucherSheetActivity, voucher.code)?.state) {
                            FirebaseEconomySyncManager.SyncState.PENDING ->
                                getString(R.string.voucher_sync_status_pending)
                            FirebaseEconomySyncManager.SyncState.SYNCED ->
                                getString(R.string.voucher_sync_status_synced)
                            FirebaseEconomySyncManager.SyncState.CONFLICT ->
                                getString(R.string.voucher_sync_status_conflict)
                            FirebaseEconomySyncManager.SyncState.FAILED ->
                                getString(R.string.voucher_sync_status_failed)
                            null ->
                                getString(R.string.voucher_sync_status_ready)
                        }
                    setTextColor(Color.parseColor("#64B5F6"))
                    textSize = 13f
                }
            )

            if (voucher.redeemed && voucher.redeemedAt > 0L) {
                card.addView(
                    TextView(this).apply {
                        text = getString(
                            R.string.voucher_sheet_redeemed,
                            formatter.format(Date(voucher.redeemedAt)),
                            voucher.redeemedByGlobalId
                        )
                        setTextColor(Color.parseColor("#E8F1F8"))
                        textSize = 13f
                    }
                )
            }
            if (!voucher.redeemed && !voucher.revoked && System.currentTimeMillis() <= voucher.expiresAt) {
                card.addView(
                    TextView(this).apply {
                        text = getString(R.string.voucher_sheet_revoke_button)
                        setTextColor(Color.parseColor("#FFD54F"))
                        textSize = 14f
                        setPadding(0, 10, 0, 10)
                        setOnClickListener {
                            this@VoucherSheetActivity.lifecycleScope.launch {
                                val result =
                                    withContext(Dispatchers.IO) {
                                        VoucherQrManager.revokeVoucher(
                                            this@VoucherSheetActivity,
                                            issuerGlobalId,
                                            voucher.code
                                        )
                                    }
                                android.widget.Toast.makeText(
                                    this@VoucherSheetActivity,
                                    if (result.success) {
                                        getString(R.string.voucher_revoke_success, result.amount)
                                    } else {
                                        getString(R.string.voucher_revoke_failed)
                                    },
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                renderVouchers()
                            }
                        }
                    }
                )
            }

            buildVoucherQrBitmap(voucher.code)?.let { qrBitmap ->
                card.addView(
                    ImageView(this).apply {
                        setImageBitmap(qrBitmap)
                        setBackgroundColor(Color.WHITE)
                        adjustViewBounds = true
                        setPadding(20, 20, 20, 20)
                    }
                )
            }

            voucherContainer.addView(card)
        }
    }

    private fun formatVoucherDate(
        timestamp: Long,
        formatter: SimpleDateFormat
    ): String {
        return if (timestamp <= 0L) {
            getString(R.string.voucher_sheet_date_unknown)
        } else {
            formatter.format(Date(timestamp))
        }
    }

    private fun updateFilterLabels() {
        btnVoucherFilterAll.isEnabled = activeFilter != VoucherFilter.ALL
        btnVoucherFilterReady.isEnabled = activeFilter != VoucherFilter.READY
        btnVoucherFilterUsed.isEnabled = activeFilter != VoucherFilter.USED
        btnVoucherFilterExpired.isEnabled = activeFilter != VoucherFilter.EXPIRED
    }

    private fun printCurrentVouchers() {
        if (currentDisplayedVouchers.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.voucher_print_empty),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val printManager =
            getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(
            getString(R.string.voucher_print_job_name),
            VoucherPrintAdapter(this, currentDisplayedVouchers, false, null),
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
        )
    }

    private fun printCompactByAmount(amount: Double) {
        val vouchers =
            currentDisplayedVouchers.filter { kotlin.math.abs(it.amount - amount) < 0.0001 }
        if (vouchers.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.voucher_print_nominal_empty, amount),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val printManager =
            getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(
            getString(R.string.voucher_print_job_name_compact, amount),
            VoucherPrintAdapter(this, vouchers, true, null),
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
        )
    }

    private fun showStorePrintDialog() {
        if (currentDisplayedVouchers.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.voucher_print_empty),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }
        val operatorInput = EditText(this).apply {
            hint = getString(R.string.voucher_print_store_operator_hint)
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
        }
        val locationInput = EditText(this).apply {
            hint = getString(R.string.voucher_print_store_location_hint)
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
        }
        val noteInput = EditText(this).apply {
            hint = getString(R.string.voucher_print_store_note_hint)
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            minLines = 2
        }
        container.addView(operatorInput)
        container.addView(locationInput)
        container.addView(noteInput)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.voucher_print_store_title))
            .setView(container)
            .setPositiveButton(getString(R.string.voucher_print_store_confirm)) { _, _ ->
                printStoreVouchers(
                    PrintFooterInfo(
                        batchId = buildPrintBatchId(currentDisplayedVouchers),
                        operatorName = operatorInput.text?.toString().orEmpty().trim(),
                        locationName = locationInput.text?.toString().orEmpty().trim(),
                        note = noteInput.text?.toString().orEmpty().trim()
                    )
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun printStoreVouchers(footerInfo: PrintFooterInfo) {
        val printManager =
            getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(
            getString(R.string.voucher_print_job_name_store),
            VoucherPrintAdapter(this, currentDisplayedVouchers, false, footerInfo),
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
        )
    }

    private fun buildVoucherQrBitmap(code: String): Bitmap? {
        return try {
            val bitMatrix: BitMatrix =
                MultiFormatWriter().encode(
                    VoucherQrManager.buildPayload(code),
                    BarcodeFormat.QR_CODE,
                    720,
                    720
                )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] =
                        if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildPrintBatchId(vouchers: List<VoucherQrManager.VoucherRecord>): String {
        val printMoment = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        val suffix = vouchers.firstOrNull()?.code?.takeLast(4)?.uppercase().orEmpty()
        return "BATCH-$printMoment-$suffix"
    }

    private inner class VoucherPrintAdapter(
        private val context: Context,
        private val vouchers: List<VoucherQrManager.VoucherRecord>,
        private val compactMode: Boolean,
        private val footerInfo: PrintFooterInfo?
    ) : PrintDocumentAdapter() {

        private var attributes: PrintAttributes? = null
        private val batchId: String = footerInfo?.batchId ?: buildPrintBatchId(vouchers)

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            attributes = newAttributes
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }

            val cardsPerPage = if (compactMode) 4 else 2
            val pageCount =
                ((vouchers.size + cardsPerPage - 1) / cardsPerPage).coerceAtLeast(1)

            callback.onLayoutFinished(
                PrintDocumentInfo.Builder(getString(R.string.voucher_print_document_name))
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(pageCount)
                    .build(),
                true
            )
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal,
            callback: WriteResultCallback
        ) {
            val printAttributes = attributes ?: run {
                callback.onWriteFailed("Print attributes missing")
                return
            }

            val document = android.print.pdf.PrintedPdfDocument(context, printAttributes)
            try {
                val cardsPerPage = if (compactMode) 4 else 2
                val pageCount = ((vouchers.size + cardsPerPage - 1) / cardsPerPage).coerceAtLeast(1)
                for (pageIndex in 0 until pageCount) {
                    if (cancellationSignal.isCanceled) {
                        callback.onWriteCancelled()
                        document.close()
                        return
                    }

                    val page = document.startPage(pageIndex)
                    drawVoucherPage(page.canvas, page.info.pageWidth, page.info.pageHeight, pageIndex)
                    document.finishPage(page)
                }

                FileOutputStream(destination.fileDescriptor).use { output ->
                    document.writeTo(output)
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (t: Throwable) {
                callback.onWriteFailed(t.message)
            } finally {
                document.close()
            }
        }

        private fun drawVoucherPage(
            canvas: Canvas,
            pageWidth: Int,
            pageHeight: Int,
            pageIndex: Int
        ) {
            val backgroundPaint = Paint().apply { color = Color.WHITE }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), backgroundPaint)

            val margin = 48f
            val gap = 24f
            val cardsPerPage = if (compactMode) 4 else 2
            val headerHeight = if (compactMode) 80f else 110f
            val footerHeight = if (footerInfo != null) 120f else 0f
            val totalGaps = gap * (cardsPerPage - 1)
            val cardHeight = (pageHeight - (margin * 2) - headerHeight - footerHeight - totalGaps) / cardsPerPage.toFloat()
            val cardWidth = pageWidth - (margin * 2)

            val start = pageIndex * cardsPerPage
            val end = minOf(start + cardsPerPage, vouchers.size)
            val printDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            val nominalSummary =
                if (vouchers.map { it.amount }.distinct().size == 1) {
                    getString(R.string.voucher_print_header_nominal_single, vouchers.firstOrNull()?.amount ?: 0.0)
                } else {
                    getString(R.string.voucher_print_header_nominal_mixed)
                }

            drawPrintHeader(
                canvas = canvas,
                left = margin,
                top = margin,
                right = pageWidth - margin,
                pageIndex = pageIndex,
                pageCount = ((vouchers.size + cardsPerPage - 1) / cardsPerPage).coerceAtLeast(1),
                voucherCount = vouchers.size,
                nominalSummary = nominalSummary,
                printDate = printDate
            )

            for (index in start until end) {
                val slot = index - start
                val top = margin + headerHeight + slot * (cardHeight + gap)
                drawVoucherCard(
                    canvas,
                    RectF(margin, top, margin + cardWidth, top + cardHeight),
                    vouchers[index]
                )
            }

            if (footerInfo != null) {
                drawPrintFooter(
                    canvas = canvas,
                    left = margin,
                    right = pageWidth - margin,
                    bottom = pageHeight - margin,
                    footerInfo = footerInfo
                )
            }
        }

        private fun drawPrintHeader(
            canvas: Canvas,
            left: Float,
            top: Float,
            right: Float,
            pageIndex: Int,
            pageCount: Int,
            voucherCount: Int,
            nominalSummary: String,
            printDate: String
        ) {
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F1720")
                textSize = if (compactMode) 24f else 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#31414F")
                textSize = if (compactMode) 14f else 18f
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D6DEE6")
                strokeWidth = 2f
            }

            var baseline = top + if (compactMode) 24f else 30f
            canvas.drawText(getString(R.string.app_name), left, baseline, titlePaint)
            baseline += if (compactMode) 22f else 28f
            canvas.drawText(getString(R.string.voucher_print_header_title, nominalSummary), left, baseline, bodyPaint)
            baseline += if (compactMode) 18f else 24f
            canvas.drawText(
                getString(
                    R.string.voucher_print_header_meta,
                    printDate,
                    voucherCount,
                    pageIndex + 1,
                    pageCount
                ),
                left,
                baseline,
                bodyPaint
            )
            baseline += if (compactMode) 16f else 22f
            canvas.drawText(
                getString(R.string.voucher_print_header_batch, batchId),
                left,
                baseline,
                bodyPaint
            )
            brandBitmap?.let { bitmap ->
                val logoSize = if (compactMode) 42 else 56
                val logoLeft = (right - logoSize).toInt()
                val logoTop = top.toInt()
                val logoRect = Rect(logoLeft, logoTop, logoLeft + logoSize, logoTop + logoSize)
                canvas.drawBitmap(bitmap, null, logoRect, null)
            }
            val lineY = baseline + if (compactMode) 12f else 16f
            canvas.drawLine(left, lineY, right, lineY, linePaint)
        }

        private fun drawPrintFooter(
            canvas: Canvas,
            left: Float,
            right: Float,
            bottom: Float,
            footerInfo: PrintFooterInfo
        ) {
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E2933")
                textSize = 17f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#31414F")
                textSize = 15f
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D6DEE6")
                strokeWidth = 2f
            }

            val top = bottom - 106f
            canvas.drawLine(left, top, right, top, linePaint)
            var baseline = top + 24f
            canvas.drawText(getString(R.string.voucher_print_store_title), left, baseline, titlePaint)
            baseline += 22f
            canvas.drawText(
                getString(R.string.voucher_print_footer_batch, footerInfo.batchId),
                left,
                baseline,
                bodyPaint
            )
            baseline += 20f
            canvas.drawText(
                getString(
                    R.string.voucher_print_footer_operator,
                    footerInfo.operatorName.ifBlank { "-" }
                ),
                left,
                baseline,
                bodyPaint
            )
            baseline += 20f
            canvas.drawText(
                getString(
                    R.string.voucher_print_footer_location,
                    footerInfo.locationName.ifBlank { "-" }
                ),
                left,
                baseline,
                bodyPaint
            )
            baseline += 20f
            canvas.drawText(
                getString(
                    R.string.voucher_print_footer_note,
                    footerInfo.note.ifBlank { "-" }
                ),
                left,
                baseline,
                bodyPaint
            )
            canvas.drawText(
                getString(R.string.voucher_print_footer_signature),
                right - 280f,
                bottom - 18f,
                bodyPaint
            )
        }

        private fun drawVoucherCard(
            canvas: Canvas,
            rect: RectF,
            voucher: VoucherQrManager.VoucherRecord
        ) {
            val cardPaint = Paint().apply { color = Color.parseColor("#F5F7FA") }
            val borderPaint = Paint().apply {
                color = Color.parseColor("#202833")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(rect, 18f, 18f, cardPaint)
            drawSecurityPattern(canvas, rect)
            canvas.drawRoundRect(rect, 18f, 18f, borderPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F1720")
                textSize = if (compactMode) 22f else 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#24303B")
                textSize = if (compactMode) 14f else 18f
            }
            val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#3E4C59")
                textSize = if (compactMode) 12f else 16f
            }
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#DDEBFF")
            }
            val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#15314B")
                textSize = if (compactMode) 12f else 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val expired = !voucher.redeemed && !voucher.revoked && System.currentTimeMillis() > voucher.expiresAt
            val status =
                when {
                    voucher.redeemed -> context.getString(R.string.voucher_sheet_status_used)
                    voucher.revoked -> context.getString(R.string.voucher_sheet_status_revoked)
                    expired -> context.getString(R.string.voucher_sheet_status_expired)
                    else -> context.getString(R.string.voucher_sheet_status_ready)
                }

            val left = rect.left + if (compactMode) 18f else 24f
            var baseline = rect.top + if (compactMode) 30f else 42f
            val badgeWidth = if (compactMode) 102f else 132f
            val badgeHeight = if (compactMode) 24f else 28f
            val badgeLeft = left
            val badgeTop = rect.bottom - badgeHeight - if (compactMode) 18f else 22f
            canvas.drawRoundRect(
                RectF(badgeLeft, badgeTop, badgeLeft + badgeWidth, badgeTop + badgeHeight),
                12f,
                12f,
                badgePaint
            )
            canvas.drawText(
                getString(R.string.voucher_print_security_badge),
                badgeLeft + 12f,
                badgeTop + (if (compactMode) 16f else 19f),
                badgeTextPaint
            )
            canvas.drawText(context.getString(R.string.voucher_sheet_amount, voucher.amount), left, baseline, titlePaint)
            baseline += if (compactMode) 22f else 30f
            canvas.drawText(context.getString(R.string.voucher_sheet_status, status), left, baseline, bodyPaint)
            baseline += if (compactMode) 18f else 28f
            canvas.drawText(
                context.getString(
                    R.string.voucher_sheet_serial,
                    VoucherQrManager.buildDisplaySerial(voucher)
                ),
                left,
                baseline,
                codePaint
            )
            baseline += if (compactMode) 16f else 26f
            canvas.drawText(context.getString(R.string.voucher_sheet_code, voucher.code.take(16)), left, baseline, codePaint)
            baseline += if (compactMode) 16f else 26f
            canvas.drawText(
                context.getString(
                    R.string.voucher_sheet_created,
                    formatVoucherDate(voucher.createdAt, formatter)
                ),
                left,
                baseline,
                codePaint
            )
            baseline += if (compactMode) 16f else 26f
            canvas.drawText(
                context.getString(
                    R.string.voucher_sheet_expires,
                    formatVoucherDate(voucher.expiresAt, formatter)
                ),
                left,
                baseline,
                codePaint
            )

            val qrBitmap = buildVoucherQrBitmap(voucher.code)
            if (qrBitmap != null) {
                val qrSize = if (compactMode) 124 else 196
                val qrLeft = (rect.right - qrSize - if (compactMode) 18f else 24f).toInt()
                val qrTop = (rect.top + if (compactMode) 18f else 24f).toInt()
                val qrRect = Rect(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize)
                canvas.drawBitmap(qrBitmap, null, qrRect, null)
            }
        }

        private fun drawSecurityPattern(
            canvas: Canvas,
            rect: RectF
        ) {
            val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E8EEF5")
                strokeWidth = if (compactMode) 2f else 3f
                alpha = if (compactMode) 80 else 95
            }
            val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D5E2EF")
                textSize = if (compactMode) 28f else 44f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                alpha = if (compactMode) 70 else 85
            }

            var x = rect.left - rect.height()
            while (x < rect.right) {
                canvas.drawLine(x, rect.bottom, x + rect.height(), rect.top, stripePaint)
                x += if (compactMode) 28f else 36f
            }

            canvas.save()
            canvas.rotate(-18f, rect.centerX(), rect.centerY())
            canvas.drawText(
                getString(R.string.voucher_print_watermark),
                rect.left + if (compactMode) 24f else 36f,
                rect.centerY(),
                watermarkPaint
            )
            canvas.restore()
        }
    }
}
