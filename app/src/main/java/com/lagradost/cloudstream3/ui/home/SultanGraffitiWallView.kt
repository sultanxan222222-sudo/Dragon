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
 * Sultan Cinema's original neon-graffiti wall.
 * It sits behind the app surfaces so the premium translucent glass can reveal it.
 */
class SultanGraffitiWallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
        strokeJoin = Paint.Join.ROUND
    }

    // x/y are the original wall's percentage positions; the third value controls font size.
    private val graffiti = listOf(
        arrayOf("ЕЩЁ ОДНУ СЕРИЮ!", 2f, 5f, 20f, -8f),
        arrayOf("КОТ ОДОБРИЛ!", 38f, 3f, 18f, 5f),
        arrayOf("ПОП-КОРН ГДЕ?!", 70f, 8f, 24f, -7f),
        arrayOf("РАБОТА ПОДОЖДЁТ!", 12f, 18f, 16f, 7f),
        arrayOf("ТЫ ЕЩЁ НЕ СПИШЬ?!", 48f, 17f, 22f, -5f),
        arrayOf("ПУЛЬТ ПРОПАЛ!", 78f, 25f, 18f, 8f),
        arrayOf("СЮЖЕТ КРУЧЕ ЖИЗНИ", 3f, 35f, 23f, 6f),
        arrayOf("ТИШЕ! ИДЁТ КИНО!", 30f, 31f, 18f, -9f),
        arrayOf("Я ТУТ НА ПЯТЬ МИНУТ", 59f, 35f, 25f, 5f),
        arrayOf("НЕ ТРОГАЙ ПОПКОРН", 82f, 43f, 16f, -7f),
        arrayOf("ДИВАН МОЙ БОСС", 8f, 52f, 19f, -5f),
        arrayOf("СПОКОЙНО, Я СМОТРЮ", 39f, 49f, 23f, 8f),
        arrayOf("КИНО ЛЕЧИТ СКУКУ", 68f, 55f, 19f, -8f),
        arrayOf("СОСЕДИ ТОЖЕ СМОТРЯТ", 1f, 68f, 18f, 7f),
        arrayOf("ГДЕ МОИ ТИТРЫ?!", 24f, 65f, 25f, -5f),
        arrayOf("МЫШОНОК В ЗАЛЕ", 52f, 68f, 17f, 8f),
        arrayOf("ЗАВТРА РАНО? НЕ СЛЫШАЛ", 78f, 72f, 20f, -6f),
        arrayOf("СНАЧАЛА ФИЛЬМ", 12f, 83f, 23f, 5f),
        arrayOf("ПЕРЕРЫВ НА ПОПКОРН", 43f, 85f, 19f, -8f),
        arrayOf("ЭКРАН ВСЁ ВИДИТ", 70f, 88f, 24f, 6f),
        arrayOf("НЕ БУДИ ЗРИТЕЛЯ", 19f, 9f, 13f, 9f),
        arrayOf("СМЕХ БЕЗ БИЛЕТА", 56f, 7f, 13f, -9f),
        arrayOf("ПЯТЬ МИНУТ ДО ФИНАЛА", 88f, 15f, 11f, 7f),
        arrayOf("ФИЛЬМ САМ СЕБЯ НЕ ПОСМОТРИТ", 4f, 25f, 12f, -6f),
        arrayOf("КТО ВЫКЛЮЧИЛ СВЕТ?", 27f, 42f, 12f, 8f),
        arrayOf("КИНО И ТОЧКА", 48f, 28f, 12f, -7f),
        arrayOf("СЮЖЕТ ЗАКРУЧЕН", 73f, 37f, 13f, 6f),
        arrayOf("ПОП-КОРН — МОЯ СЛАБОСТЬ", 91f, 57f, 11f, -8f),
        arrayOf("ДИВАН В РЕЖИМЕ КИНО", 31f, 77f, 13f, 7f),
        arrayOf("НЕ СПОЙЛЕРИ!", 59f, 80f, 12f, -5f),
        arrayOf("СЕАНС ДЛЯ СВОИХ", 87f, 88f, 11f, 8f),
        arrayOf("УЛЫБНИСЬ, ИДЁТ ЗАПИСЬ", 5f, 92f, 13f, -6f),
        arrayOf("ПУЛЬТ — ГЛАВНЫЙ", 36f, 92f, 12f, 7f),
        arrayOf("СОН ОТМЕНЯЕТСЯ", 63f, 94f, 13f, -8f),
        arrayOf("ЕЩЁ ЧУТЬ-ЧУТЬ", 91f, 3f, 12f, 6f),
        arrayOf("СМОТРИ И НЕ МОРГАЙ", 2f, 47f, 12f, -7f)
    )

    private val neonPalette = intArrayOf(
        Color.rgb(68, 220, 255),
        Color.rgb(255, 83, 190),
        Color.rgb(172, 126, 255),
        Color.rgb(72, 238, 191),
        Color.rgb(255, 202, 92),
        Color.rgb(255, 132, 83)
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
            intArrayOf(withAlpha(color, 82), withAlpha(color, 26), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
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

        linePaint.color = 0x18FFFFFF
        linePaint.strokeWidth = dp(1f)
        val step = dp(46f)
        var x = -h
        while (x < w + h) {
            canvas.drawLine(x, 0f, x + h * 0.56f, h, linePaint)
            x += step
        }

        linePaint.color = 0x28D6AE61
        linePaint.strokeWidth = dp(1.2f)
        canvas.drawCircle(w * 0.89f, h * 0.18f, min(w, h) * 0.12f, linePaint)
        canvas.drawCircle(w * 0.89f, h * 0.18f, min(w, h) * 0.155f, linePaint)

        graffiti.forEachIndexed { index, item ->
            val phrase = item[0] as String
            val leftPercent = item[1] as Float
            val topPercent = item[2] as Float
            val sizeFactor = item[3] as Float
            val angle = item[4] as Float
            val textSize = (sizeFactor * 0.28f * w / 100f).coerceIn(dp(9f), dp(34f))

            textPaint.textSize = textSize
            textPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
            val measuredWidth = max(1f, textPaint.measureText(phrase))
            val maxTextWidth = w * 0.64f
            val scaleX = min(1f, maxTextWidth / measuredWidth)
            val renderedWidth = measuredWidth * scaleX
            val desiredLeft = w * leftPercent / 100f
            val safeLeft = min(desiredLeft, max(dp(2f), w - renderedWidth - dp(4f)))
            val baseline = (h * topPercent / 100f).coerceIn(textSize, h - dp(3f))
            val color = neonPalette[index % neonPalette.size]

            canvas.save()
            canvas.translate(safeLeft, baseline)
            canvas.rotate(angle)
            canvas.scale(scaleX, 1f)

            textPaint.shader = null
            textPaint.style = Paint.Style.STROKE
            textPaint.strokeWidth = dp(5f)
            textPaint.color = withAlpha(color, if (index % 4 == 0) 65 else 48)
            canvas.drawText(phrase, 0f, 0f, textPaint)

            textPaint.strokeWidth = dp(1.2f)
            textPaint.color = withAlpha(Color.WHITE, 130)
            canvas.drawText(phrase, 0f, 0f, textPaint)

            textPaint.style = Paint.Style.FILL
            textPaint.shader = LinearGradient(
                0f,
                -textSize,
                0f,
                dp(2f),
                intArrayOf(withAlpha(Color.WHITE, 205), withAlpha(color, 158), withAlpha(color, 124)),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawText(phrase, 0f, 0f, textPaint)
            textPaint.shader = null
            canvas.restore()
        }

        glowPaint.style = Paint.Style.FILL
        val sparkPoints = arrayOf(
            0.12f to 0.22f, 0.32f to 0.09f, 0.78f to 0.24f,
            0.91f to 0.53f, 0.24f to 0.68f, 0.70f to 0.91f
        )
        sparkPoints.forEachIndexed { index, point ->
            glowPaint.color = withAlpha(if (index % 2 == 0) Color.WHITE else 0xFFD6AE61.toInt(), 95)
            canvas.drawCircle(w * point.first, h * point.second, dp(if (index % 2 == 0) 1.5f else 2f), glowPaint)
        }
    }
}
