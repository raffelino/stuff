package com.raffelino.rechenwuerfel

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Zeichnet das Spielbrett als Schlangenpfad und die Spielfiguren darauf.
 * Feld 0 ist Start (unten links), das letzte Feld ist das Ziel.
 */
class BoardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var fields = 30
    private var players: List<Player> = emptyList()
    private val animPos = FloatArray(4)
    private var movingPlayer = -1
    private var animator: ValueAnimator? = null

    var currentPlayer = 0
        set(value) { field = value; invalidate() }

    private var cols = 6
    private var rows = 5
    private var cell = 0f
    private var offX = 0f
    private var offY = 0f

    private val cellColors = intArrayOf(
        0xFFFFF59D.toInt(), 0xFFB3E5FC.toInt(), 0xFFF8BBD0.toInt(), 0xFFC8E6C9.toInt(), 0xFFFFE0B2.toInt(),
    )
    private val startColor = 0xFF81C784.toInt()
    private val goalColor = 0xFFFFD54F.toInt()

    private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0x66FFFFFF
    }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x33000000
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99000000.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDD000000.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val tokenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tokenStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFFC107.toInt()
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val roadPath = Path()
    private val rect = RectF()

    fun setup(fields: Int, players: List<Player>) {
        this.fields = max(2, fields)
        this.players = players
        for (i in players.indices) animPos[i] = players[i].position.toFloat()
        computeLayout()
        invalidate()
    }

    /** Setzt die gezeichneten Positionen auf die Positionen der Spieler zurück. */
    fun resetPositions() {
        animator?.cancel()
        movingPlayer = -1
        for (i in players.indices) animPos[i] = players[i].position.toFloat()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeLayout()
    }

    private fun computeLayout() {
        val w = (width - paddingLeft - paddingRight).toFloat()
        val h = (height - paddingTop - paddingBottom).toFloat()
        if (w <= 0f || h <= 0f) return
        val ideal = sqrt(fields * w / h).roundToInt()
        cols = ideal.coerceIn(3, 12).coerceAtMost(fields)
        rows = ceil(fields / cols.toFloat()).toInt()
        cell = min(w / cols, h / rows)
        offX = paddingLeft + (w - cols * cell) / 2f
        offY = paddingTop + (h - rows * cell) / 2f

        roadPath.reset()
        for (i in 0 until fields) {
            val c = cellCenter(i)
            if (i == 0) roadPath.moveTo(c.x, c.y) else roadPath.lineTo(c.x, c.y)
        }
        roadPaint.strokeWidth = cell * 0.55f
        cellStroke.strokeWidth = max(1f, cell * 0.02f)
        numberPaint.textSize = cell * 0.22f
        labelPaint.textSize = cell * 0.20f
        tokenStroke.strokeWidth = max(2f, cell * 0.035f)
        ringPaint.strokeWidth = max(2f, cell * 0.05f)
    }

    private fun cellCenter(index: Int): PointF {
        val r = index / cols
        val c0 = index % cols
        val c = if (r % 2 == 0) c0 else cols - 1 - c0
        val row = rows - 1 - r
        return PointF(offX + (c + 0.5f) * cell, offY + (row + 0.5f) * cell)
    }

    /** Position auf dem Pfad zwischen zwei Feldern (für die Bewegungs-Animation). */
    private fun pathPoint(pos: Float): PointF {
        val i = floor(pos).toInt().coerceIn(0, fields - 1)
        val j = (i + 1).coerceAtMost(fields - 1)
        val t = (pos - i).coerceIn(0f, 1f)
        val a = cellCenter(i)
        val b = cellCenter(j)
        return PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cell <= 0f) return

        canvas.drawPath(roadPath, roadPaint)

        val half = cell * 0.43f
        val corner = cell * 0.18f
        for (i in 0 until fields) {
            val c = cellCenter(i)
            rect.set(c.x - half, c.y - half, c.x + half, c.y + half)
            cellPaint.color = when (i) {
                0 -> startColor
                fields - 1 -> goalColor
                else -> cellColors[i % cellColors.size]
            }
            canvas.drawRoundRect(rect, corner, corner, cellPaint)
            canvas.drawRoundRect(rect, corner, corner, cellStroke)
            when (i) {
                0 -> canvas.drawText("START", c.x, c.y - half + labelPaint.textSize * 1.1f, labelPaint)
                fields - 1 -> canvas.drawText("🏁 ZIEL", c.x, c.y - half + labelPaint.textSize * 1.1f, labelPaint)
                else -> canvas.drawText(i.toString(), c.x, c.y - half + numberPaint.textSize * 1.05f, numberPaint)
            }
        }

        drawTokens(canvas)
    }

    private fun drawTokens(canvas: Canvas) {
        if (players.isEmpty()) return
        val radius = cell * 0.2f
        emojiPaint.textSize = radius * 1.35f

        // Figuren auf demselben Feld werden leicht versetzt gezeichnet.
        val order = players.indices.sortedBy { if (it == movingPlayer) 1 else 0 }
        for (idx in order) {
            val pos = animPos[idx]
            val p = pathPoint(pos)
            val onSameCell = players.indices.filter { it != movingPlayer && animPos[it] == pos }
            val slot = onSameCell.indexOf(idx)
            val (dx, dy) = if (idx == movingPlayer || onSameCell.size <= 1) 0f to 0f else offsetFor(slot, onSameCell.size)
            val cx = p.x + dx * cell
            val cy = p.y + (dy + 0.06f) * cell

            if (idx == currentPlayer) {
                canvas.drawCircle(cx, cy, radius * 1.25f, ringPaint)
            }
            tokenPaint.color = players[idx].color
            canvas.drawCircle(cx, cy, radius, tokenPaint)
            canvas.drawCircle(cx, cy, radius, tokenStroke)
            canvas.drawText(players[idx].figure, cx, cy + emojiPaint.textSize * 0.36f, emojiPaint)
        }
    }

    private fun offsetFor(slot: Int, count: Int): Pair<Float, Float> = when (count) {
        2 -> if (slot == 0) -0.17f to 0.05f else 0.17f to 0.05f
        3 -> when (slot) { 0 -> -0.18f to -0.08f; 1 -> 0.18f to -0.08f; else -> 0f to 0.18f }
        else -> when (slot) { 0 -> -0.17f to -0.12f; 1 -> 0.17f to -0.12f; 2 -> -0.17f to 0.18f; else -> 0.17f to 0.18f }
    }

    /**
     * Bewegt die Figur von [from] nach [to] Schritt für Schritt.
     * [onStep] wird bei jedem erreichten Feld aufgerufen, [onEnd] am Schluss.
     */
    fun animateMove(playerIndex: Int, from: Int, to: Int, onStep: (Int) -> Unit, onEnd: () -> Unit) {
        animator?.cancel()
        if (to <= from) { onEnd(); return }
        movingPlayer = playerIndex
        var lastStep = from
        val anim = ValueAnimator.ofFloat(from.toFloat(), to.toFloat())
        anim.duration = (380L * (to - from)).coerceAtMost(3000L)
        anim.interpolator = AccelerateDecelerateInterpolator()
        anim.addUpdateListener { a ->
            val v = a.animatedValue as Float
            animPos[playerIndex] = v
            val reached = floor(v + 0.001f).toInt()
            while (lastStep < reached) {
                lastStep++
                onStep(lastStep)
            }
            invalidate()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                animPos[playerIndex] = to.toFloat()
                movingPlayer = -1
                animator = null
                invalidate()
                onEnd()
            }
        })
        animator = anim
        anim.start()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
