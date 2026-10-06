package com.raffelino.rechenwuerfel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.random.Random

/** Zeigt den Rechenwürfel und spielt die Würfel-Animation ab. */
class DiceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var face: DiceFace = DiceFace.PLUS
        set(value) { field = value; invalidate() }

    private var rolling = false
    private var angle = 0f
    private var scale = 1f
    private val handler = Handler(Looper.getMainLooper())
    private var roller: Runnable? = null

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF37474F.toInt()
    }
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val rect = RectF()

    val isRolling: Boolean get() = rolling

    /** Seiten, die während der Würfel-Animation durchlaufen werden. */
    var faces: List<DiceFace> = DiceFace.values().toList()
        set(value) { field = if (value.isEmpty()) DiceFace.values().toList() else value }

    /** Würfelt und zeigt am Ende [result]. */
    fun roll(result: DiceFace, onDone: () -> Unit) {
        roller?.let { handler.removeCallbacks(it) }
        rolling = true
        val start = SystemClock.uptimeMillis()
        val total = 1200L
        val r = object : Runnable {
            override fun run() {
                val elapsed = SystemClock.uptimeMillis() - start
                if (elapsed >= total) {
                    face = result
                    angle = 0f
                    scale = 1f
                    rolling = false
                    roller = null
                    invalidate()
                    animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(120).withEndAction { onDone() }.start()
                    }.start()
                } else {
                    face = faces[Random.nextInt(faces.size)]
                    angle = Random.nextFloat() * 50f - 25f
                    scale = 0.85f + Random.nextFloat() * 0.25f
                    invalidate()
                    handler.postDelayed(this, 60L + elapsed / 6)
                }
            }
        }
        roller = r
        handler.post(r)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val half = size * 0.44f * scale
        val corner = half * 0.35f
        borderPaint.strokeWidth = size * 0.03f

        canvas.save()
        canvas.rotate(angle, cx, cy)
        rect.set(cx - half + size * 0.03f, cy - half + size * 0.05f, cx + half + size * 0.03f, cy + half + size * 0.05f)
        canvas.drawRoundRect(rect, corner, corner, shadowPaint)
        rect.set(cx - half, cy - half, cx + half, cy + half)
        canvas.drawRoundRect(rect, corner, corner, bodyPaint)
        borderPaint.color = face.color
        canvas.drawRoundRect(rect, corner, corner, borderPaint)

        symbolPaint.color = face.color
        symbolPaint.textSize = if (face.operation != null) half * 1.5f else half * 1.1f
        val baseline = cy - (symbolPaint.descent() + symbolPaint.ascent()) / 2f
        canvas.drawText(face.symbol, cx, baseline, symbolPaint)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        roller?.let { handler.removeCallbacks(it) }
        super.onDetachedFromWindow()
    }
}
