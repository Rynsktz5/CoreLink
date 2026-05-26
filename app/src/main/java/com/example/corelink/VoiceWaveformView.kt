package com.example.corelink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class VoiceWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rect = RectF()
    private val barHeights = FloatArray(30)
    
    var activeColor: Int = Color.BLUE
        set(value) {
            field = value
            invalidate()
        }
        
    var inactiveColor: Int = Color.GRAY
        set(value) {
            field = value
            invalidate()
        }

    var progress: Float = 0.0f
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
            invalidate()
        }

    var onSeekListener: ((Float) -> Unit)? = null

    init {
        generateBars("")
    }

    fun setAudioPath(path: String) {
        generateBars(path)
        invalidate()
    }

    private fun generateBars(seed: String) {
        val hash = seed.hashCode()
        for (i in barHeights.indices) {
            // Generate pseudo-random bar heights between 0.15 and 0.9 based on index and seed hash
            val valAtIdx = abs((hash + i * 29) % 100) / 100f
            barHeights[i] = 0.15f + valAtIdx * 0.75f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val count = barHeights.size
        val gap = 6f
        val totalGaps = gap * (count - 1)
        val barWidth = (w - totalGaps) / count

        for (i in 0 until count) {
            val barHeight = barHeights[i] * h
            val left = i * (barWidth + gap)
            val top = (h - barHeight) / 2f
            val right = left + barWidth
            val bottom = top + barHeight

            val isPassed = (i.toFloat() / count.toFloat()) <= progress
            paint.color = if (isPassed) activeColor else inactiveColor

            rect.set(left, top, right, bottom)
            val radius = barWidth / 2f
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val newProgress = (event.x / width.toFloat()).coerceIn(0f, 1f)
                progress = newProgress
                onSeekListener?.invoke(newProgress)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
