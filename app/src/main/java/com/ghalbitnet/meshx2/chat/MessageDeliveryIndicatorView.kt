package com.ghalbitnet.meshx2.chat

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.animation.doOnEnd

class MessageDeliveryIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = dp(8f)
    }
    private var deliveryState: ChatDeliveryState = ChatDeliveryState.DRAFT
    private var edited: Boolean = false
    private var deleted: Boolean = false

    init {
        minimumWidth = dp(18f).toInt()
        minimumHeight = dp(12f).toInt()
        alpha = 0.96f
        Log.d("GHALBIT-UI-PERF", "delivery indicator lightweight")
    }

    fun setDeliveryState(state: ChatDeliveryState, animate: Boolean = true) {
        if (deliveryState == state) {
            Log.d("GHALBIT-UI-PERF", "skipped relayout")
            return
        }
        val previous = deliveryState
        deliveryState = state
        Log.d("GHALBIT-DELIVERY-UI", "state=${state.dbValue}")
        if (animate && previous == ChatDeliveryState.DELIVERED_REMOTE && state == ChatDeliveryState.READ_REMOTE) {
            ValueAnimator.ofFloat(0.55f, 1f).apply {
                duration = 160L
                addUpdateListener {
                    alpha = it.animatedValue as Float
                    invalidate()
                }
                doOnEnd { Log.d("GHALBIT-DELIVERY-ANIM", "read transition") }
            }.start()
        } else {
            invalidate()
        }
    }

    fun setReadState(read: Boolean) {
        Log.d("GHALBIT-DELIVERY-UI", "read=$read")
        setDeliveryState(if (read) ChatDeliveryState.READ_REMOTE else ChatDeliveryState.DELIVERED_REMOTE)
    }

    fun setFailed(failed: Boolean) {
        Log.d("GHALBIT-DELIVERY-UI", "pending=${!failed}")
        if (failed) setDeliveryState(ChatDeliveryState.FAILED_FINAL)
    }

    fun setPending(pending: Boolean) {
        Log.d("GHALBIT-DELIVERY-UI", "pending=$pending")
        if (pending) setDeliveryState(ChatDeliveryState.QUEUED_LOCAL)
    }

    fun setEdited(edited: Boolean) {
        this.edited = edited
        invalidate()
    }

    fun setDeleted(deleted: Boolean) {
        this.deleted = deleted
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cy = h / 2f
        val radius = dp(3f)
        val palette = palette(deliveryState)
        fill.color = palette.fill
        stroke.color = palette.stroke
        stroke.strokeWidth = dp(1.35f)
        labelPaint.color = palette.stroke

        when {
            deleted -> drawDeleted(canvas, w, h)
            edited -> drawEdited(canvas, w, h)
            deliveryState == ChatDeliveryState.FAILED_FINAL || deliveryState == ChatDeliveryState.MEDIA_EXPIRED || deliveryState == ChatDeliveryState.EXPIRED_REMOTE -> drawFailed(canvas, w, h, radius)
            deliveryState == ChatDeliveryState.DRAFT || deliveryState == ChatDeliveryState.DRAFT_TEXT || deliveryState == ChatDeliveryState.DRAFT_MEDIA || deliveryState == ChatDeliveryState.DRAFT_FILE || deliveryState == ChatDeliveryState.REVIEW_READY || deliveryState == ChatDeliveryState.EDITING_DRAFT || deliveryState == ChatDeliveryState.QUEUED_LOCAL || deliveryState == ChatDeliveryState.PENDING || deliveryState == ChatDeliveryState.WAITING_FOR_PEER || deliveryState == ChatDeliveryState.WAITING_FOR_ROUTE || deliveryState == ChatDeliveryState.RELAY_CONFIG_REQUIRED || deliveryState == ChatDeliveryState.MEDIA_UPLOADING || deliveryState == ChatDeliveryState.MEDIA_RESUMING -> {
                canvas.drawCircle(dp(6f), cy, radius, stroke)
            }
            deliveryState == ChatDeliveryState.ACCEPTED_BY_RELAY || deliveryState == ChatDeliveryState.QUEUED_REMOTE || deliveryState == ChatDeliveryState.MEDIA_QUEUED_REMOTE -> {
                canvas.drawCircle(dp(6f), cy, radius, fill)
            }
            deliveryState == ChatDeliveryState.SENT_LOCAL || deliveryState == ChatDeliveryState.SENT_INTERNET || deliveryState == ChatDeliveryState.DELIVERED || deliveryState == ChatDeliveryState.DELIVERED_REMOTE || deliveryState == ChatDeliveryState.MEDIA_DELIVERED_REMOTE -> {
                drawDoubleCheck(canvas, palette.stroke, w, h, false)
            }
            deliveryState == ChatDeliveryState.READ || deliveryState == ChatDeliveryState.READ_REMOTE || deliveryState == ChatDeliveryState.MEDIA_READ_REMOTE -> {
                drawDoubleCheck(canvas, palette.stroke, w, h, true)
            }
            else -> canvas.drawCircle(dp(6f), cy, radius, stroke)
        }
    }

    private fun drawDoubleCheck(canvas: Canvas, color: Int, width: Float, height: Float, read: Boolean) {
        stroke.color = color
        stroke.strokeWidth = dp(if (read) 1.55f else 1.35f)
        val first = Path().apply {
            moveTo(width - dp(14f), height / 2f)
            lineTo(width - dp(11f), height - dp(3.5f))
            lineTo(width - dp(7f), dp(3.5f))
        }
        val second = Path().apply {
            moveTo(width - dp(9f), height / 2f)
            lineTo(width - dp(6f), height - dp(3.5f))
            lineTo(width - dp(2f), dp(3.5f))
        }
        canvas.drawPath(first, stroke)
        canvas.drawPath(second, stroke)
    }

    private fun drawFailed(canvas: Canvas, width: Float, height: Float, radius: Float) {
        canvas.drawCircle(width - dp(6f), height / 2f, radius + dp(0.4f), fill)
        stroke.color = Color.WHITE
        stroke.strokeWidth = dp(1.2f)
        canvas.drawLine(width - dp(6f), dp(3f), width - dp(6f), height - dp(5f), stroke)
        canvas.drawPoint(width - dp(6f), height - dp(2.5f), stroke)
    }

    private fun drawEdited(canvas: Canvas, width: Float, height: Float) {
        canvas.drawText("ed", width - dp(14f), height - dp(2f), labelPaint)
    }

    private fun drawDeleted(canvas: Canvas, width: Float, height: Float) {
        stroke.strokeWidth = dp(1.3f)
        canvas.drawLine(width - dp(14f), dp(3f), width - dp(2f), height - dp(3f), stroke)
        canvas.drawLine(width - dp(14f), height - dp(3f), width - dp(2f), dp(3f), stroke)
    }

    private fun palette(state: ChatDeliveryState): Palette {
        return when (state) {
            ChatDeliveryState.DRAFT,
            ChatDeliveryState.DRAFT_TEXT,
            ChatDeliveryState.DRAFT_MEDIA,
            ChatDeliveryState.DRAFT_FILE,
            ChatDeliveryState.REVIEW_READY,
            ChatDeliveryState.EDITING_DRAFT -> Palette(0x00000000, 0xFF8FA4B8.toInt())
            ChatDeliveryState.QUEUED_LOCAL,
            ChatDeliveryState.WAITING_FOR_ROUTE,
            ChatDeliveryState.RELAY_CONFIG_REQUIRED,
            ChatDeliveryState.PENDING,
            ChatDeliveryState.WAITING_FOR_PEER,
            ChatDeliveryState.MEDIA_UPLOADING,
            ChatDeliveryState.MEDIA_RESUMING -> Palette(0x00000000, 0xFFBCCBDD.toInt())
            ChatDeliveryState.ACCEPTED_BY_RELAY,
            ChatDeliveryState.QUEUED_REMOTE,
            ChatDeliveryState.MEDIA_QUEUED_REMOTE -> Palette(0xFFDFF7F7.toInt(), 0xFF5ABFC7.toInt())
            ChatDeliveryState.DELIVERED,
            ChatDeliveryState.DELIVERED_REMOTE,
            ChatDeliveryState.SENT_LOCAL,
            ChatDeliveryState.SENT_INTERNET,
            ChatDeliveryState.MEDIA_DELIVERED_REMOTE -> Palette(0x00000000, 0xFF54CDA3.toInt())
            ChatDeliveryState.READ,
            ChatDeliveryState.READ_REMOTE,
            ChatDeliveryState.MEDIA_READ_REMOTE -> Palette(0x00000000, 0xFF39A8FF.toInt())
            ChatDeliveryState.FAILED_FINAL,
            ChatDeliveryState.MEDIA_EXPIRED,
            ChatDeliveryState.EXPIRED_REMOTE -> Palette(0xFFE98E8E.toInt(), 0xFFC75A5A.toInt())
            ChatDeliveryState.DELETE_REQUESTED_REMOTE,
            ChatDeliveryState.DELETED_LOCAL,
            ChatDeliveryState.DELETED_REMOTE -> Palette(0x00000000, 0xFFD3A36A.toInt())
            ChatDeliveryState.EDIT_REQUESTED_REMOTE,
            ChatDeliveryState.EDITED_REMOTE -> Palette(0x00000000, 0xFF8AD7FF.toInt())
            else -> Palette(0x00000000, 0xFF8FA4B8.toInt())
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private data class Palette(val fill: Int, val stroke: Int)
}
