package com.alp.walkbreak

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Circular countdown ring: a soft track plus an arc for the time left, with a glowing head dot. */
class ProgressRing @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val stroke = 14f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = stroke
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    /** 0..1 — share of the phase still remaining. */
    var progress = 1f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    var trackColor: Int
        get() = trackPaint.color
        set(value) { trackPaint.color = value; invalidate() }

    var arcColor: Int
        get() = arcPaint.color
        set(value) { arcPaint.color = value; invalidate() }

    var headColor: Int
        get() = headPaint.color
        set(value) { headPaint.color = value; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Always square.
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (h == 0) w else min(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val pad = stroke / 2 + 6 * density
        rect.set(pad, pad, width - pad, height - pad)
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)

        val sweep = 360f * progress
        if (sweep > 0.5f) {
            canvas.drawArc(rect, -90f, sweep, false, arcPaint)
            val angle = Math.toRadians((-90f + sweep).toDouble())
            val r = rect.width() / 2
            val cx = rect.centerX() + r * cos(angle).toFloat()
            val cy = rect.centerY() + r * sin(angle).toFloat()
            canvas.drawCircle(cx, cy, stroke * 0.42f, headPaint)
        }
    }
}
