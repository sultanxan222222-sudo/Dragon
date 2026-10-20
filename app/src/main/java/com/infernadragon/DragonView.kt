package com.infernadragon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.concurrent.thread

/**
 * Opaque, connected crimson dragon drawn with vector paths.
 * Only the ambient glow and embers use transparency.
 */
class DragonView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val started = SystemClock.uptimeMillis()
    private var biteAt = 0L
    private var biteX = 0f
    private var biteY = 0f
    private var wasGrowling = false
    private var lastGrowlStartedAt = 0L

    init { setLayerType(View.LAYER_TYPE_SOFTWARE, null) }

    fun triggerBiteAtCenter() {
        biteX = width * .52f
        biteY = height * .48f
        biteAt = SystemClock.uptimeMillis()
        invalidate()
    }

    /** Short synthesized low growl; generated locally so no external audio asset is required. */
    private fun playGrowl() {
        thread(name = "dragon-growl", isDaemon = true) {
            val sampleRate = 22050
            val count = (sampleRate * .72f).toInt()
            val samples = ShortArray(count)
            for (i in 0 until count) {
                val x = i.toFloat() / count
                val xd = x.toDouble()
                val envelope = sin(PI * x).toFloat().coerceAtLeast(0f).let { it * it }
                val wobble = 1f + .12f * sin(2.0 * PI * 5.0 * xd).toFloat()
                val phase = 2.0 * PI * (58.0 * xd * .72 + 9.0 * xd * xd)
                val rumble = sin(phase).toFloat() * .62f +
                    sin(phase * 1.91).toFloat() * .23f +
                    sin(phase * 3.17).toFloat() * .10f +
                    sin(i * 0.173).toFloat() * .05f
                samples[i] = (rumble * envelope * wobble * 10500f).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            var track: AudioTrack? = null
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
                track = AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(samples.size * 2)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep(760L)
            } catch (_: Exception) {
                // Silent fallback if audio output is unavailable.
            } finally {
                track?.release()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val now = SystemClock.uptimeMillis()
        val t = (now - started) / 1000f
        // Keep the full silhouette smaller than the screen so it can travel without filling the display.
        val s = min(w, h) / 390f * .72f

        // Atmospheric edge glow: effects only, never a translucent dragon body.
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        glow.shader = RadialGradient(w * .82f, h * .45f, min(w, h) * .42f,
            intArrayOf(Color.argb(46, 255, 28, 8), Color.argb(15, 160, 0, 0), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, glow)
        glow.shader = null

        val biteElapsed = now - biteAt
        val biting = biteElapsed >= 0L && biteElapsed <= 620L
        val p = (biteElapsed / 620f).coerceIn(0f, 1f)
        val lunge = if (biting) sin(p * Math.PI).toFloat() else 0f

        // A repeating living-creature routine: crawl, fly across, perch, glare/growl, fly back.
        val cycle = t % 24f
        val growling = cycle >= 12f && cycle < 15f
        var direction = 1f
        val baseX: Float
        val baseY: Float
        when {
            cycle < 4f -> {
                val q = cycle / 4f
                baseX = w * (.38f + .50f * q)
                baseY = h * (.17f + .018f * sin(t * 4.2f))
            }
            cycle < 9f -> {
                val q = (cycle - 4f) / 5f
                direction = -1f
                baseX = w * (.90f - .65f * q)
                baseY = h * (.36f + .075f * sin(q * Math.PI).toFloat())
            }
            cycle < 12f -> {
                baseX = w * .25f
                baseY = h * (.17f + .012f * sin(t * 2.5f))
            }
            cycle < 15f -> {
                baseX = w * .25f
                baseY = h * (.17f + .008f * sin(t * 7f))
            }
            cycle < 20f -> {
                val q = (cycle - 15f) / 5f
                baseX = w * (.25f + .65f * q)
                baseY = h * (.35f - .06f * sin(q * Math.PI).toFloat())
            }
            else -> {
                val q = (cycle - 20f) / 4f
                direction = -1f
                baseX = w * (.88f - .50f * q)
                baseY = h * (.17f + .016f * sin(t * 4f))
            }
        }
        if (growling && !wasGrowling && now - lastGrowlStartedAt > 1800L) {
            lastGrowlStartedAt = now
            playGrowl()
        }
        wasGrowling = growling

        val hx = baseX + (if (biting) (biteX - baseX) * .34f * lunge else 0f)
        val headShake = if (growling) sin(t * 21f) * 3.2f * s else 0f
        val hy = baseY + (if (biting) (biteY - baseY) * .28f * lunge else 0f) +
            sin(t * 1.1f) * 3f * s + headShake

        canvas.save()
        // Scale and turn the complete connected creature as one composition.
        canvas.translate(hx, hy)
        canvas.scale(direction * s, s)

        // Tail and body form one continuous S-curve into the neck.
        val bodyPath = Path().apply {
            moveTo(-330f, 86f + sin(t * 1.2f) * 9f)
            cubicTo(-286f, 46f, -262f, 6f + sin(t) * 7f, -226f, 28f)
            cubicTo(-192f, 49f, -198f, 105f + sin(t * .9f) * 8f, -154f, 103f)
            cubicTo(-105f, 101f, -113f, 53f + sin(t * 1.1f) * 6f, -79f, 48f)
            cubicTo(-54f, 44f, -51f, 22f, -27f, 14f)
        }
        stroke.color = Color.rgb(22, 5, 10)
        stroke.strokeWidth = 35f
        canvas.drawPath(bodyPath, stroke)
        stroke.color = Color.rgb(104, 8, 22)
        stroke.strokeWidth = 27f
        canvas.drawPath(bodyPath, stroke)
        stroke.color = Color.rgb(183, 24, 31)
        stroke.strokeWidth = 17f
        canvas.drawPath(bodyPath, stroke)
        stroke.color = Color.rgb(255, 81, 27)
        stroke.strokeWidth = 2.5f
        canvas.drawPath(bodyPath, stroke)

        // Distinct pointed tail tip.
        val tail = Path().apply {
            moveTo(-305f, 72f); lineTo(-357f, 94f + sin(t * 1.2f) * 9f)
            lineTo(-325f, 55f); close()
        }
        fill.color = Color.rgb(65, 5, 17)
        canvas.drawPath(tail, fill)
        stroke.color = Color.rgb(211, 37, 30); stroke.strokeWidth = 2f
        canvas.drawPath(tail, stroke)

        // Two visible bat-like wings, attached to the back.
        val wingShift = sin(t * 2.0f) * 7f
        val wing = Path().apply {
            moveTo(-190f, 45f)
            lineTo(-222f, -38f + wingShift)
            lineTo(-205f, -21f + wingShift)
            lineTo(-181f, -73f + wingShift)
            lineTo(-176f, -28f + wingShift)
            lineTo(-143f, -48f + wingShift)
            lineTo(-153f, 3f)
            lineTo(-124f, 42f)
            close()
        }
        fill.color = Color.rgb(39, 5, 15)
        canvas.drawPath(wing, fill)
        stroke.color = Color.rgb(157, 19, 29); stroke.strokeWidth = 3f
        canvas.drawPath(wing, stroke)
        stroke.color = Color.rgb(113, 14, 25); stroke.strokeWidth = 2f
        canvas.drawLine(-222f, -38f + wingShift, -153f, 3f, stroke)
        canvas.drawLine(-181f, -73f + wingShift, -153f, 3f, stroke)
        canvas.drawLine(-176f, -28f + wingShift, -153f, 3f, stroke)

        // Back spines make the silhouette unmistakably dragon-like.
        for (i in 0..8) {
            val x = -235f + i * 22f
            val y = if (i < 3) 20f else 46f
            val spine = Path().apply {
                moveTo(x - 7f, y + 1f); lineTo(x, y - 17f); lineTo(x + 8f, y + 1f); close()
            }
            fill.color = Color.rgb(31, 4, 11)
            canvas.drawPath(spine, fill)
            stroke.color = Color.rgb(237, 47, 28); stroke.strokeWidth = 1.5f
            canvas.drawPath(spine, stroke)
        }

        // Raised scale plates along the visible upper side of the body.
        for (i in 0..13) {
            val u = i / 13f
            val x = -310f + u * 270f
            val y = 75f - 43f * sin(u * Math.PI).toFloat() + 15f * sin(u * 2.2f * Math.PI).toFloat()
            val plate = Path().apply {
                moveTo(x - 6f, y + 2f); lineTo(x, y - 6f); lineTo(x + 7f, y + 2f)
                lineTo(x, y + 5f); close()
            }
            fill.color = if (i % 3 == 0) Color.rgb(255, 91, 30) else Color.rgb(211, 39, 35)
            canvas.drawPath(plate, fill)
        }

        // Neck flows directly into the skull.
        val neck = Path().apply {
            moveTo(-43f, 25f)
            cubicTo(-29f, 20f, -27f, 10f, -17f, 5f)
            lineTo(-4f, 4f)
            lineTo(-4f, 23f)
            cubicTo(-20f, 30f, -32f, 34f, -43f, 25f)
            close()
        }
        fill.color = Color.rgb(76, 5, 17)
        canvas.drawPath(neck, fill)
        stroke.color = Color.rgb(190, 28, 31); stroke.strokeWidth = 2.5f
        canvas.drawPath(neck, stroke)

        // Skull, muzzle and lower jaw are separate solid shapes with a clear mouth gap.
        val skull = Path().apply {
            moveTo(-26f, 5f)
            cubicTo(-30f, -13f, -19f, -30f, -2f, -31f)
            cubicTo(13f, -32f, 24f, -23f, 28f, -13f)
            lineTo(43f, -7f)
            lineTo(37f, 1f)
            lineTo(17f, 4f)
            lineTo(7f, 13f)
            cubicTo(-6f, 17f, -19f, 14f, -26f, 5f)
            close()
        }
        fill.color = Color.rgb(86, 7, 18)
        canvas.drawPath(skull, fill)
        stroke.color = Color.rgb(226, 40, 32); stroke.strokeWidth = 2.5f
        canvas.drawPath(skull, stroke)

        // Horns.
        val horns = Path().apply {
            moveTo(-17f, -22f); cubicTo(-35f, -34f, -38f, -49f, -30f, -58f)
            cubicTo(-29f, -42f, -19f, -35f, -7f, -29f); close()
            moveTo(0f, -29f); cubicTo(-4f, -43f, 2f, -52f, 10f, -57f)
            cubicTo(7f, -42f, 11f, -35f, 16f, -28f); close()
        }
        fill.color = Color.rgb(25, 7, 11)
        canvas.drawPath(horns, fill)
        stroke.color = Color.rgb(153, 25, 28); stroke.strokeWidth = 1.8f
        canvas.drawPath(horns, stroke)

        // Eye socket, glowing amber eye and slit pupil.
        fill.color = Color.rgb(25, 1, 4)
        canvas.drawOval(-2f, -22f, 17f, -7f, fill)
        fill.color = Color.rgb(255, 183, 35)
        canvas.drawOval(2f, -19f, 14f, -10f, fill)
        fill.color = Color.BLACK
        canvas.drawOval(7f, -19f, 10f, -10f, fill)
        fill.color = Color.rgb(255, 244, 187)
        canvas.drawCircle(5f, -16f, 1.8f, fill)

        // Nostril and fangs on the upper muzzle.
        fill.color = Color.rgb(18, 2, 5)
        canvas.drawCircle(31f, -9f, 2.3f, fill)
        val fangs = Path().apply {
            moveTo(13f, 0f); lineTo(17f, 10f); lineTo(20f, 1f); close()
            moveTo(27f, -2f); lineTo(30f, 6f); lineTo(33f, -3f); close()
        }
        fill.color = Color.rgb(255, 226, 185)
        canvas.drawPath(fangs, fill)

        // Open lower jaw; it moves down during the bite.
        val jawDrop = if (biting) 9f * sin(p * Math.PI).toFloat() else if (growling) 7f + 3f * sin(t * 18f) else 1.5f
        val jaw = Path().apply {
            moveTo(2f, 7f + jawDrop)
            cubicTo(13f, 10f + jawDrop, 28f, 9f + jawDrop, 39f, 2f + jawDrop)
            lineTo(32f, 18f + jawDrop)
            cubicTo(20f, 25f + jawDrop, 8f, 20f + jawDrop, 2f, 7f + jawDrop)
            close()
        }
        fill.color = Color.rgb(53, 3, 13)
        canvas.drawPath(jaw, fill)
        stroke.color = Color.rgb(219, 42, 31); stroke.strokeWidth = 2f
        canvas.drawPath(jaw, stroke)
        val lowerFangs = Path().apply {
            moveTo(18f, 13f + jawDrop); lineTo(20f, 7f + jawDrop); lineTo(23f, 14f + jawDrop); close()
            moveTo(29f, 9f + jawDrop); lineTo(31f, 4f + jawDrop); lineTo(33f, 10f + jawDrop); close()
        }
        fill.color = Color.rgb(255, 228, 191)
        canvas.drawPath(lowerFangs, fill)

        canvas.restore()

        // Bite flash stays separate from the solid creature.
        if (biting) {
            fill.color = Color.argb(((1f - p) * 130f).toInt().coerceIn(0, 130), 255, 67, 8)
            canvas.drawCircle(biteX, biteY, (14f + p * 36f) * s, fill)
        }

        // A few small embers; no smoke veil over the dragon.
        for (i in 0..11) {
            val phase = t * (0.7f + (i % 4) * .17f) + i * 2.1f
            val x = (w * .5f + sin(phase) * w * .42f + w) % w
            val y = (h * ((phase * .09f + i * .19f) % 1f))
            fill.color = Color.argb((75 + 110 * (.5f + .5f * sin(phase))).toInt().coerceIn(0, 185), 255, 70, 14)
            canvas.drawCircle(x, y, (1f + (i % 2)) * s, fill)
        }
        postInvalidateOnAnimation()
    }
}
