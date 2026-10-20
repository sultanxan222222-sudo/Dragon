package com.infernadragon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.os.SystemClock
import kotlin.math.sin
import kotlin.math.min

class DragonView(context: Context) : View(context) {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val started = SystemClock.uptimeMillis()
    private var biteAt = 0L
    private var biteX = 0f
    private var biteY = 0f

    init { setLayerType(View.LAYER_TYPE_SOFTWARE, null) }

    fun triggerBiteAtCenter() {
        biteX = width / 2f
        biteY = height / 2f
        biteAt = SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val t = (SystemClock.uptimeMillis() - started) / 1000f
        val scale = min(w, h) / 390f
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for (side in 0..1) {
            val x = if (side == 0) 0f else w
            glow.shader = RadialGradient(x, h * (0.28f + 0.44f * (0.5f + 0.5f * sin(t * 0.8f + side))), w * 0.24f,
                intArrayOf(Color.argb(65, 255, 42, 0), Color.argb(22, 255, 22, 0), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawRect(if (side == 0) 0f else w * .72f, 0f, if (side == 0) w * .28f else w, h, glow)
            glow.shader = null
        }
        val path = Path()
        val cx = w * (0.5f + 0.22f * sin(t * 0.47f))
        val cy = h * (0.48f + 0.25f * sin(t * 0.31f + 1f))
        val length = min(w, h) * 0.72f
        for (i in 0..90) {
            val u = i / 90f
            val x = cx + (u - .5f) * length + sin(t * 1.2f + u * 9f) * w * .07f
            val y = cy + sin(t * 1.1f + u * 9f) * h * .13f + (u - .5f) * h * .10f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        body.strokeWidth = 20f * scale
        body.color = Color.rgb(55, 8, 15)
        canvas.drawPath(path, body)
        body.strokeWidth = 13f * scale
        body.color = Color.rgb(126, 15, 25)
        canvas.drawPath(path, body)
        body.strokeWidth = 3.5f * scale
        body.color = Color.rgb(255, 83, 27)
        canvas.drawPath(path, body)
        for (i in 0..25) {
            val u = i / 25f
            val x = cx + (u - .5f) * length + sin(t * 1.2f + u * 9f) * w * .07f
            val y = cy + sin(t * 1.1f + u * 9f) * h * .13f + (u - .5f) * h * .10f
            fill.color = if (i % 3 == 0) Color.rgb(255, 92, 24) else Color.rgb(203, 34, 31)
            canvas.drawCircle(x, y - 5f * scale, (2.4f + (i % 2)) * scale, fill)
        }
        var hx = cx + sin(t * 1.2f) * w * .07f + length * .50f
        var hy = cy + sin(t * 1.1f + 4.5f) * h * .13f + h * .05f
        val biting = SystemClock.uptimeMillis() - biteAt < 500
        if (biting) {
            val p = ((SystemClock.uptimeMillis() - biteAt) / 500f).coerceIn(0f, 1f)
            hx += (biteX - hx) * (if (p < .45f) p / .45f else (1f - p) / .55f)
            hy += (biteY - hy) * (if (p < .45f) p / .45f else (1f - p) / .55f)
        }
        canvas.save(); canvas.translate(hx, hy); canvas.rotate(sin(t * 1.1f) * 25f)
        val horn = Path().apply { moveTo(-8f*scale, -7f*scale); quadTo(-24f*scale, -35f*scale, -29f*scale, -39f*scale); quadTo(-22f*scale, -16f*scale, -2f*scale, 0f) }
        fill.color = Color.rgb(24, 8, 12); canvas.drawPath(horn, fill)
        canvas.save(); canvas.scale(1f, -1f); canvas.drawPath(horn, fill); canvas.restore()
        fill.color = Color.rgb(74, 8, 17)
        val head = Path().apply { moveTo(-20f*scale, -15f*scale); cubicTo(-9f*scale, -30f*scale, 14f*scale, -24f*scale, 24f*scale, -8f*scale); lineTo(38f*scale, 0f); lineTo(20f*scale, 9f*scale); cubicTo(7f*scale, 23f*scale, -13f*scale, 17f*scale, -20f*scale, 5f*scale); close() }
        canvas.drawPath(head, fill)
        detail.color = Color.rgb(255, 91, 25); detail.strokeWidth = 2f*scale
        canvas.drawLine(-4f*scale, -10f*scale, 9f*scale, -7f*scale, detail)
        fill.color = Color.rgb(255, 183, 39); canvas.drawCircle(6f*scale, -7f*scale, 3.2f*scale, fill)
        fill.color = Color.BLACK; canvas.drawCircle(7f*scale, -7f*scale, 1.3f*scale, fill)
        val teeth = Path().apply { moveTo(14f*scale, 7f*scale); lineTo(19f*scale, 14f*scale); lineTo(21f*scale, 7f*scale); lineTo(28f*scale, 5f*scale); lineTo(30f*scale, 1f*scale); close() }
        fill.color = Color.rgb(255, 226, 181); canvas.drawPath(teeth, fill)
        detail.color = Color.rgb(255, 95, 36); detail.strokeWidth = 1.4f*scale
        canvas.drawLine(13f*scale, 4f*scale, 35f*scale, 18f*scale, detail)
        canvas.drawLine(13f*scale, -2f*scale, 37f*scale, -12f*scale, detail)
        canvas.restore()
        if (biting) {
            val p = (SystemClock.uptimeMillis() - biteAt) / 500f
            fill.color = Color.argb(((1f-p)*180).toInt().coerceIn(0,180), 255, 75, 0)
            canvas.drawCircle(biteX, biteY, (12f + p*48f)*scale, fill)
        }
        for (i in 0..16) {
            val phase = t * (0.8f + i % 4 * .16f) + i * 2.13f
            val x = (i * 73f + sin(phase) * 24f * scale) % w
            val y = (h * ((phase * .12f + i * .173f) % 1f))
            fill.color = Color.argb((80 + 130 * (0.5f + .5f * sin(phase))).toInt().coerceIn(0,255), 255, 75, 15)
            canvas.drawCircle(x, y, (1f + (i % 3)) * scale, fill)
        }
        postInvalidateOnAnimation()
    }
}
