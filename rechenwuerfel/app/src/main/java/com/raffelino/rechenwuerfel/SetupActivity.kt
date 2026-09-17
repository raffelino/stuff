package com.raffelino.rechenwuerfel

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Spieler anlegen (Name + Figur) und Spieleinstellungen festlegen. */
class SetupActivity : BaseActivity() {

    private class PlayerRow(val root: View, val name: EditText, val figureBar: LinearLayout, val badge: TextView) {
        var figure: String = ""
        val figureButtons = ArrayList<TextView>()
    }

    private var playerCount = 2
    private val rows = ArrayList<PlayerRow>()
    private lateinit var countButtons: List<Button>
    private lateinit var playersContainer: LinearLayout

    private lateinit var editRange: EditText
    private lateinit var editSeconds: EditText
    private lateinit var editFields: EditText
    private lateinit var editStepsPlus: EditText
    private lateinit var editStepsMinus: EditText
    private lateinit var editStepsTimes: EditText
    private lateinit var editStepsDivide: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        playersContainer = findViewById(R.id.playersContainer)
        editRange = findViewById(R.id.editRange)
        editSeconds = findViewById(R.id.editSeconds)
        editFields = findViewById(R.id.editFields)
        editStepsPlus = findViewById(R.id.editStepsPlus)
        editStepsMinus = findViewById(R.id.editStepsMinus)
        editStepsTimes = findViewById(R.id.editStepsTimes)
        editStepsDivide = findViewById(R.id.editStepsDivide)

        val prefs = getSharedPreferences(GameSettings.PREFS, Context.MODE_PRIVATE)
        playerCount = prefs.getInt(GameSettings.KEY_PLAYER_COUNT, 2).coerceIn(1, 4)

        countButtons = listOf(
            findViewById(R.id.btnCount1), findViewById(R.id.btnCount2),
            findViewById(R.id.btnCount3), findViewById(R.id.btnCount4),
        )
        countButtons.forEachIndexed { i, b ->
            b.setOnClickListener { click(); setPlayerCount(i + 1) }
        }

        val inflater = LayoutInflater.from(this)
        for (i in 0 until 4) {
            val v = inflater.inflate(R.layout.item_player_setup, playersContainer, false)
            val row = PlayerRow(
                v, v.findViewById(R.id.editName), v.findViewById(R.id.figureBar), v.findViewById(R.id.playerBadge),
            )
            row.badge.text = getString(R.string.player_n, i + 1)
            (row.badge.background.mutate() as? GradientDrawable)?.setColor(Figures.COLORS[i])
            row.name.setText(prefs.getString(GameSettings.KEY_PLAYER_NAME + i, getString(R.string.player_n, i + 1)))
            val savedFigure = prefs.getString(GameSettings.KEY_PLAYER_FIGURE + i, null)
            row.figure = if (savedFigure != null && savedFigure in Figures.ALL) savedFigure else Figures.ALL[i]
            buildFigureBar(row, i)
            playersContainer.addView(v)
            rows.add(row)
        }
        // Doppelte Figuren aus alten Einstellungen auflösen.
        for (i in rows.indices) {
            if (rows.take(i).any { it.figure == rows[i].figure }) {
                rows[i].figure = Figures.ALL.first { f -> rows.none { it.figure == f } }
            }
        }
        refreshFigureBars()
        setPlayerCount(playerCount)

        val s = GameSettings.load(this)
        editRange.setText(s.numberRange.toString())
        editSeconds.setText(s.secondsPerTask.toString())
        editFields.setText(s.boardFields.toString())
        editStepsPlus.setText(s.stepsPlus.toString())
        editStepsMinus.setText(s.stepsMinus.toString())
        editStepsTimes.setText(s.stepsTimes.toString())
        editStepsDivide.setText(s.stepsDivide.toString())

        bindChip(R.id.chipRange20, editRange, 20)
        bindChip(R.id.chipRange50, editRange, 50)
        bindChip(R.id.chipRange100, editRange, 100)
        bindChip(R.id.chipRange1000, editRange, 1000)
        bindChip(R.id.chipTime10, editSeconds, 10)
        bindChip(R.id.chipTime20, editSeconds, 20)
        bindChip(R.id.chipTime30, editSeconds, 30)
        bindChip(R.id.chipTime60, editSeconds, 60)

        findViewById<Button>(R.id.btnStart).setOnClickListener { click(); startGame() }
    }

    private fun bindChip(id: Int, target: EditText, value: Int) {
        findViewById<Button>(id).setOnClickListener {
            click()
            target.setText(value.toString())
        }
    }

    private fun setPlayerCount(count: Int) {
        playerCount = count
        countButtons.forEachIndexed { i, b -> b.isSelected = (i + 1 == count) }
        rows.forEachIndexed { i, r -> r.root.visibility = if (i < count) View.VISIBLE else View.GONE }
    }

    private fun buildFigureBar(row: PlayerRow, playerIndex: Int) {
        val size = (resources.displayMetrics.density * 48).toInt()
        val margin = (resources.displayMetrics.density * 4).toInt()
        for (f in Figures.ALL) {
            val t = TextView(this)
            t.text = f
            t.textSize = 26f
            t.gravity = android.view.Gravity.CENTER
            t.setBackgroundResource(R.drawable.bg_figure)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.setMargins(margin, margin, margin, margin)
            t.layoutParams = lp
            t.setOnClickListener {
                val takenBy = rows.indexOfFirst { it.figure == f }
                if (takenBy != -1 && takenBy != playerIndex && takenBy < playerCount) {
                    // Figuren tauschen
                    rows[takenBy].figure = row.figure
                }
                row.figure = f
                click()
                refreshFigureBars()
            }
            row.figureBar.addView(t)
            row.figureButtons.add(t)
        }
    }

    private fun refreshFigureBars() {
        for ((pi, row) in rows.withIndex()) {
            for ((fi, btn) in row.figureButtons.withIndex()) {
                val f = Figures.ALL[fi]
                val takenByOther = rows.withIndex().any { (i, r) -> i != pi && i < playerCount && r.figure == f }
                btn.isSelected = row.figure == f
                btn.alpha = if (takenByOther) 0.3f else 1f
            }
        }
    }

    private fun readInt(e: EditText, fallback: Int): Int = e.text.toString().trim().toIntOrNull() ?: fallback

    private fun startGame() {
        val defaults = GameSettings()
        val settings = GameSettings(
            numberRange = readInt(editRange, defaults.numberRange),
            secondsPerTask = readInt(editSeconds, defaults.secondsPerTask),
            boardFields = readInt(editFields, defaults.boardFields),
            stepsPlus = readInt(editStepsPlus, defaults.stepsPlus),
            stepsMinus = readInt(editStepsMinus, defaults.stepsMinus),
            stepsTimes = readInt(editStepsTimes, defaults.stepsTimes),
            stepsDivide = readInt(editStepsDivide, defaults.stepsDivide),
        ).sanitized()
        GameSettings.save(this, settings)

        val names = ArrayList<String>()
        val figures = ArrayList<String>()
        val editor = getSharedPreferences(GameSettings.PREFS, Context.MODE_PRIVATE).edit()
        editor.putInt(GameSettings.KEY_PLAYER_COUNT, playerCount)
        for (i in 0 until playerCount) {
            val row = rows[i]
            var name = row.name.text.toString().trim()
            if (name.isEmpty()) name = getString(R.string.player_n, i + 1)
            names.add(name)
            figures.add(row.figure)
            editor.putString(GameSettings.KEY_PLAYER_NAME + i, name)
            editor.putString(GameSettings.KEY_PLAYER_FIGURE + i, row.figure)
        }
        editor.apply()

        if (figures.toSet().size != figures.size) {
            Toast.makeText(this, R.string.error_duplicate_figure, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, GameActivity::class.java)
        intent.putStringArrayListExtra(GameActivity.EXTRA_NAMES, names)
        intent.putStringArrayListExtra(GameActivity.EXTRA_FIGURES, figures)
        intent.putExtra(GameActivity.EXTRA_RANGE, settings.numberRange)
        intent.putExtra(GameActivity.EXTRA_SECONDS, settings.secondsPerTask)
        intent.putExtra(GameActivity.EXTRA_FIELDS, settings.boardFields)
        intent.putExtra(GameActivity.EXTRA_STEPS, intArrayOf(settings.stepsPlus, settings.stepsMinus, settings.stepsTimes, settings.stepsDivide))
        startActivity(intent)
    }
}
