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
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
        strokeJoin = Paint.Join.ROUND
    }

    private data class GraffitiTag(
        val text: String,
        val leftPercent: Float,
        val topPercent: Float,
        val sizeFactor: Float,
        val angle: Float
    )

    // Original Sultan wall positions, using explicit fields for strict Kotlin builds.
    private val graffiti = listOf(
        GraffitiTag("ЕЩЁ ОДНУ СЕРИЮ!", 2f, 5f, 20f, -8f),
        GraffitiTag("КОТ ОДОБРИЛ!", 38f, 3f, 18f, 5f),
        GraffitiTag("ПОП-КОРН ГДЕ?!", 70f, 8f, 24f, -7f),
        GraffitiTag("РАБОТА ПОДОЖДЁТ!", 12f, 18f, 16f, 7f),
        GraffitiTag("ТЫ ЕЩЁ НЕ СПИШЬ?!", 48f, 17f, 22f, -5f),
        GraffitiTag("ПУЛЬТ ПРОПАЛ!", 78f, 25f, 18f, 8f),
        GraffitiTag("СЮЖЕТ КРУЧЕ ЖИЗНИ", 3f, 35f, 23f, 6f),
        GraffitiTag("ТИШЕ! ИДЁТ КИНО!", 30f, 31f, 18f, -9f),
        GraffitiTag("Я ТУТ НА ПЯТЬ МИНУТ", 59f, 35f, 25f, 5f),
        GraffitiTag("НЕ ТРОГАЙ ПОПКОРН", 82f, 43f, 16f, -7f),
        GraffitiTag("ДИВАН МОЙ БОСС", 8f, 52f, 19f, -5f),
        GraffitiTag("СПОКОЙНО, Я СМОТРЮ", 39f, 49f, 23f, 8f),
        GraffitiTag("КИНО ЛЕЧИТ СКУКУ", 68f, 55f, 19f, -8f),
        GraffitiTag("СОСЕДИ ТОЖЕ СМОТРЯТ", 1f, 68f, 18f, 7f),
        GraffitiTag("ГДЕ МОИ ТИТРЫ?!", 24f, 65f, 25f, -5f),
        GraffitiTag("МЫШОНОК В ЗАЛЕ", 52f, 68f, 17f, 8f),
        GraffitiTag("ЗАВТРА РАНО? НЕ СЛЫШАЛ", 78f, 72f, 20f, -6f),
        GraffitiTag("СНАЧАЛА ФИЛЬМ", 12f, 83f, 23f, 5f),
        GraffitiTag("ПЕРЕРЫВ НА ПОПКОРН", 43f, 85f, 19f, -8f),
        GraffitiTag("ЭКРАН ВСЁ ВИДИТ", 70f, 88f, 24f, 6f),
        GraffitiTag("НЕ БУДИ ЗРИТЕЛЯ", 19f, 9f, 13f, 9f),
        GraffitiTag("СМЕХ БЕЗ БИЛЕТА", 56f, 7f, 13f, -9f),
        GraffitiTag("ПЯТЬ МИНУТ ДО ФИНАЛА", 88f, 15f, 11f, 7f),
        GraffitiTag("ФИЛЬМ САМ СЕБЯ НЕ ПОСМОТРИТ", 4f, 25f, 12f, -6f),
        GraffitiTag("КТО ВЫКЛЮЧИЛ СВЕТ?", 27f, 42f, 12f, 8f),
        GraffitiTag("КИНО И ТОЧКА", 48f, 28f, 12f, -7f),
        GraffitiTag("СЮЖЕТ ЗАКРУЧЕН", 73f, 37f, 13f, 6f),
        GraffitiTag("ПОП-КОРН — МОЯ СЛАБОСТЬ", 91f, 57f, 11f, -8f),
        GraffitiTag("ДИВАН В РЕЖИМЕ КИНО", 31f, 77f, 13f, 7f),
        GraffitiTag("НЕ СПОЙЛЕРИ!", 59f, 80f, 12f, -5f),
        GraffitiTag("СЕАНС ДЛЯ СВОИХ", 87f, 88f, 11f, 8f),
        GraffitiTag("УЛЫБНИСЬ, ИДЁТ ЗАПИСЬ", 5f, 92f, 13f, -6f),
        GraffitiTag("ПУЛЬТ — ГЛАВНЫЙ", 36f, 92f, 12f, 7f),
        GraffitiTag("СОН ОТМЕНЯЕТСЯ", 63f, 94f, 13f, -8f),
        GraffitiTag("ЕЩЁ ЧУТЬ-ЧУТЬ", 91f, 3f, 12f, 6f),
        GraffitiTag("СМОТРИ И НЕ МОРГАЙ", 2f, 47f, 12f, -7f)
    )

    private val neonPalette = intArrayOf(
        Color.rgb(82, 234, 255),
        Color.rgb(255, 99, 200),
        Color.rgb(168, 121, 255),
        Color.rgb(85, 240, 199),
        Color.rgb(255, 200, 93),
        Color.rgb(255, 100, 123)
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
            val phrase = item.text
            val leftPercent = item.leftPercent
            val topPercent = item.topPercent
            val sizeFactor = item.sizeFactor
            val angle = item.angle
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

            // Deep enamel extrusion: a dark lower edge gives the lettering real depth.
            textPaint.shader = null
            textPaint.style = Paint.Style.FILL
            textPaint.color = withAlpha(Color.BLACK, 205)
            canvas.drawText(phrase, dp(2.8f), dp(3.4f), textPaint)

            textPaint.style = Paint.Style.STROKE
            textPaint.strokeWidth = dp(7f)
            textPaint.color = withAlpha(color, if (index % 4 == 0) 72 else 58)
            canvas.drawText(phrase, 0f, 0f, textPaint)

            // A second inner bevel and sharp glass highlight keep the graffiti crisp.
            textPaint.strokeWidth = dp(2.2f)
            textPaint.color = withAlpha(color, 190)
            canvas.drawText(phrase, 0f, 0f, textPaint)

            textPaint.style = Paint.Style.FILL
            textPaint.shader = LinearGradient(
                0f,
                -textSize,
                0f,
                dp(2f),
                intArrayOf(
                    withAlpha(Color.WHITE, 235),
                    withAlpha(color, 210),
                    withAlpha(color, 155)
                ),
                floatArrayOf(0f, 0.28f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawText(phrase, 0f, 0f, textPaint)
            textPaint.shader = null

            textPaint.style = Paint.Style.STROKE
            textPaint.strokeWidth = dp(0.8f)
            textPaint.color = withAlpha(Color.WHITE, 190)
            canvas.drawText(phrase, -dp(0.35f), -dp(0.35f), textPaint)
            textPaint.style = Paint.Style.FILL
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
