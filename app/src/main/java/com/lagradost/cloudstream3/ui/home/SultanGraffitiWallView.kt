package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Sultan Cinema's neon-graffiti wall.
 *
 * Drawn once as a lightweight native View behind the regular CloudStream UI so the
 * translucent glass surfaces can reveal the original Sultan Cinema identity.
 */
class SultanGraffitiWallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
    }

    private data class Graffiti(
        val text: String,
        val x: Float,
        val y: Float,
        val size: Float,
        val angle: Float,
        val color: Int
    )

    private fun dp(value: Float): Float = value * density

    private val tags = listOf(
        Graffiti("ЕЩЁ ОДНУ СЕРИЮ!", 0.035f, 0.13f, 27f, -7f, Color.rgb(77, 220, 255)),
        Graffiti("КОТ ОДОБРИЛ!", 0.57f, 0.19f, 25f, 6f, Color.rgb(255, 91, 193)),
        Graffiti("ПОПКОРН ГДЕ?!", 0.08f, 0.34f, 22f, 7f, Color.rgb(255, 194, 78)),
        Graffiti("СЮЖЕТ КРУЧЕ ЖИЗНИ", 0.38f, 0.42f, 25f, -5f, Color.rgb(164, 121, 255)),
        Graffiti("ТИШЕ! ИДЁТ КИНО!", 0.06f, 0.56f, 24f, 5f, Color.rgb(78, 238, 197)),
        Graffiti("ПУЛЬТ ПРОПАЛ!", 0.61f, 0.61f, 24f, -7f, Color.rgb(255, 99, 126)),
        Graffiti("СЕАНС ДЛЯ СВОИХ", 0.10f, 0.77f, 25f, -4f, Color.rgb(83, 218, 255)),
        Graffiti("НЕ СПОЙЛЕРИ!", 0.64f, 0.83f, 22f, 6f, Color.rgb(255, 187, 74)),
        Graffiti("КИНО И ТОЧКА", 0.35f, 0.94f, 23f, -4f, Color.rgb(185, 126, 255)),
        Graffiti("ЕЩЁ ЧУТЬ-ЧУТЬ", 0.72f, 0.37f, 18f, 7f, Color.rgb(255, 99, 194)),
        Graffiti("ГДЕ МОИ ТИТРЫ?!", 0.02f, 0.91f, 17f, -6f, Color.rgb(91, 232, 202)),
        Graffiti("ДИВАН — МОЙ БОСС", 0.54f, 0.29f, 18f, 5f, Color.rgb(255, 186, 84))
    )

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun drawGlow(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, color: Int) {
        glowPaint.shader = RadialGradient(
            centerX,
            centerY,
            radius,
            intArrayOf(withAlpha(color, 74), withAlpha(color, 24), Color.TRANSPARENT),
            floatArrayOf(0f, 0.46f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)
        glowPaint.shader = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawColor(Color.rgb(7, 8, 13))

        drawGlow(canvas, w * 0.18f, h * 0.14f, max(w, h) * 0.56f, Color.rgb(32, 119, 165))
        drawGlow(canvas, w * 0.85f, h * 0.35f, max(w, h) * 0.48f, Color.rgb(151, 58, 133))
        drawGlow(canvas, w * 0.51f, h * 0.91f, max(w, h) * 0.44f, Color.rgb(153, 107, 44))

        // Fine architectural lines lend the wall a polished, cinematic depth.
        linePaint.color = 0x16FFFFFF
        linePaint.strokeWidth = dp(1f)
        val step = dp(46f)
        var x = -h
        while (x < w + h) {
            canvas.drawLine(x, 0f, x + h * 0.56f, h, linePaint)
            x += step
        }
        linePaint.color = 0x1CD6AE61
        linePaint.strokeWidth = dp(1.2f)
        canvas.drawCircle(w * 0.89f, h * 0.18f, min(w, h) * 0.12f, linePaint)
        canvas.drawCircle(w * 0.89f, h * 0.18f, min(w, h) * 0.155f, linePaint)

        val sizeScale = min(1f, w / dp(390f))
        tags.forEachIndexed { index, tag ->
            val textSize = dp(tag.size) * max(0.72f, sizeScale)
            textPaint.textSize = textSize
            textPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
            val measured = max(1f, textPaint.measureText(tag.text))
            val maxWidth = w * 0.72f
            val scaleX = min(1f, maxWidth / measured)

            canvas.save()
            canvas.translate(w * tag.x, h * tag.y)
            canvas.rotate(tag.angle)
            canvas.scale(scaleX, 1f)

            // Outer neon bloom, crisp glass edge, then an enamel-like inner face.
            textPaint.shader = null
            textPaint.style = Paint.Style.STROKE
            textPaint.strokeJoin = Paint.Join.ROUND
            textPaint.strokeWidth = dp(6f)
            textPaint.color = withAlpha(tag.color, if (index % 3 == 0) 48 else 34)
            canvas.drawText(tag.text, 0f, 0f, textPaint)

            textPaint.strokeWidth = dp(1.35f)
            textPaint.color = withAlpha(Color.WHITE, 115)
            canvas.drawText(tag.text, 0f, 0f, textPaint)

            textPaint.style = Paint.Style.FILL
            textPaint.shader = LinearGradient(
                0f,
                -textSize,
                0f,
                dp(2f),
                intArrayOf(withAlpha(Color.WHITE, 190), withAlpha(tag.color, 142), withAlpha(tag.color, 112)),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawText(tag.text, 0f, 0f, textPaint)
            textPaint.shader = null
            canvas.restore()
        }

        // Small reflected points of light across the wall.
        glowPaint.style = Paint.Style.FILL
        val sparkPoints = arrayOf(
            0.12f to 0.22f, 0.32f to 0.09f, 0.78f to 0.24f,
            0.91f to 0.53f, 0.24f to 0.68f, 0.70f to 0.91f
        )
        sparkPoints.forEachIndexed { i, point ->
            glowPaint.color = withAlpha(if (i % 2 == 0) Color.WHITE else 0xFFD6AE61.toInt(), 85)
            canvas.drawCircle(w * point.first, h * point.second, dp(if (i % 2 == 0) 1.5f else 2f), glowPaint)
        }
    }
}
