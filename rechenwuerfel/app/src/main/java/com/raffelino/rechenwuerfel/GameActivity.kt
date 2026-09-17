package com.raffelino.rechenwuerfel

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.min
import kotlin.random.Random

/** Der eigentliche Spielablauf: würfeln, rechnen, ziehen, gewinnen. */
class GameActivity : BaseActivity() {

    companion object {
        const val EXTRA_NAMES = "names"
        const val EXTRA_FIGURES = "figures"
        const val EXTRA_RANGE = "range"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_FIELDS = "fields"
        const val EXTRA_STEPS = "steps"
    }

    private lateinit var settings: GameSettings
    private val players = ArrayList<Player>()
    private var current = 0
    private var task: MathTask? = null
    private var currentFace: DiceFace? = null
    private val answer = StringBuilder()
    private var timer: TaskTimer? = null
    private var timeLeftMs = 0L
    private var busy = false
    private var maxAnswerDigits = 3

    private lateinit var boardView: BoardView
    private lateinit var diceView: DiceView
    private lateinit var textTurn: TextView
    private lateinit var btnMusic: Button
    private lateinit var btnSfx: Button

    private lateinit var panelRoll: View
    private lateinit var panelJoker: View
    private lateinit var panelTask: View
    private lateinit var panelMessage: View
    private lateinit var panelWin: View

    private lateinit var btnRoll: Button
    private lateinit var textDiceHint: TextView
    private lateinit var textQuestion: TextView
    private lateinit var textAnswer: TextView
    private lateinit var textTaskInfo: TextView
    private lateinit var textTimeLeft: TextView
    private lateinit var progressTime: ProgressBar
    private lateinit var textMessage: TextView
    private lateinit var btnNext: Button
    private lateinit var textWinner: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        val names = intent.getStringArrayListExtra(EXTRA_NAMES) ?: arrayListOf("Spieler 1", "Spieler 2")
        val figures = intent.getStringArrayListExtra(EXTRA_FIGURES) ?: arrayListOf("🐸", "🦊")
        val steps = intent.getIntArrayExtra(EXTRA_STEPS) ?: intArrayOf(1, 2, 3, 4)
        settings = GameSettings(
            numberRange = intent.getIntExtra(EXTRA_RANGE, 100),
            secondsPerTask = intent.getIntExtra(EXTRA_SECONDS, 30),
            boardFields = intent.getIntExtra(EXTRA_FIELDS, 30),
            stepsPlus = steps[0], stepsMinus = steps[1], stepsTimes = steps[2], stepsDivide = steps[3],
        ).sanitized()
        maxAnswerDigits = settings.numberRange.toString().length + 1

        for (i in names.indices) {
            players.add(Player(names[i], figures.getOrElse(i) { Figures.ALL[i] }, Figures.COLORS[i % Figures.COLORS.size]))
        }

        bindViews()
        boardView.setup(settings.boardFields, players)
        boardView.currentPlayer = 0
        updateHeader()
        showPanel(panelRoll)
        updateAudioButtons()
    }

    private fun bindViews() {
        boardView = findViewById(R.id.boardView)
        diceView = findViewById(R.id.diceView)
        textTurn = findViewById(R.id.textTurn)
        btnMusic = findViewById(R.id.btnMusic)
        btnSfx = findViewById(R.id.btnSfx)
        panelRoll = findViewById(R.id.panelRoll)
        panelJoker = findViewById(R.id.panelJoker)
        panelTask = findViewById(R.id.panelTask)
        panelMessage = findViewById(R.id.panelMessage)
        panelWin = findViewById(R.id.panelWin)
        btnRoll = findViewById(R.id.btnRoll)
        textDiceHint = findViewById(R.id.textDiceHint)
        textQuestion = findViewById(R.id.textQuestion)
        textAnswer = findViewById(R.id.textAnswer)
        textTaskInfo = findViewById(R.id.textTaskInfo)
        textTimeLeft = findViewById(R.id.textTimeLeft)
        progressTime = findViewById(R.id.progressTime)
        textMessage = findViewById(R.id.textMessage)
        btnNext = findViewById(R.id.btnNext)
        textWinner = findViewById(R.id.textWinner)

        btnRoll.setOnClickListener { rollDice() }
        btnNext.setOnClickListener { click(); nextPlayer() }
        btnMusic.setOnClickListener {
            SoundManager.setMusicEnabled(!SoundManager.musicEnabled)
            updateAudioButtons()
        }
        btnSfx.setOnClickListener {
            SoundManager.setSfxEnabled(!SoundManager.sfxEnabled)
            updateAudioButtons()
            click()
        }

        findViewById<Button>(R.id.btnJokerPlus).setOnClickListener { chooseJoker(Operation.PLUS) }
        findViewById<Button>(R.id.btnJokerMinus).setOnClickListener { chooseJoker(Operation.MINUS) }
        findViewById<Button>(R.id.btnJokerTimes).setOnClickListener { chooseJoker(Operation.TIMES) }
        findViewById<Button>(R.id.btnJokerDivide).setOnClickListener { chooseJoker(Operation.DIVIDE) }

        val keyIds = intArrayOf(
            R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
            R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9,
        )
        keyIds.forEachIndexed { digit, id ->
            findViewById<Button>(id).setOnClickListener { onDigit(digit) }
        }
        findViewById<Button>(R.id.keyBack).setOnClickListener { onBackspace() }
        findViewById<Button>(R.id.keyOk).setOnClickListener { submitAnswer() }

        findViewById<Button>(R.id.btnPlayAgain).setOnClickListener { click(); restartGame() }
        findViewById<Button>(R.id.btnMenu).setOnClickListener { click(); finish() }

        val stepsHint = getString(
            R.string.steps_hint,
            settings.stepsPlus, settings.stepsMinus, settings.stepsTimes, settings.stepsDivide,
        )
        findViewById<TextView>(R.id.textStepsHint).text = stepsHint
    }

    private fun updateAudioButtons() {
        btnMusic.text = if (SoundManager.musicEnabled) "🎵" else "🔇"
        btnSfx.text = if (SoundManager.sfxEnabled) "🔊" else "🔈"
        btnMusic.alpha = if (SoundManager.musicEnabled) 1f else 0.5f
        btnSfx.alpha = if (SoundManager.sfxEnabled) 1f else 0.5f
    }

    private val player: Player get() = players[current]

    /** Positionen aller Spieler (für Tests). */
    internal val playerPositions: List<Int> get() = players.map { it.position }

    /** Index des Spielers, der gerade dran ist (für Tests). */
    internal val currentPlayerIndex: Int get() = current

    private fun updateHeader() {
        textTurn.text = getString(R.string.turn_of, player.figure, player.name)
        textTurn.setTextColor(player.color)
        boardView.currentPlayer = current
    }

    private fun showPanel(panel: View) {
        for (p in listOf(panelRoll, panelJoker, panelTask, panelMessage, panelWin)) {
            p.visibility = if (p === panel) View.VISIBLE else View.GONE
        }
    }

    // ---------------------------------------------------------------- Würfeln

    private fun rollDice() {
        if (busy || diceView.isRolling) return
        busy = true
        btnRoll.isEnabled = false
        textDiceHint.text = getString(R.string.rolling)
        SoundManager.play(SoundManager.Sfx.DICE)
        val face = DiceRoller.roll()
        diceView.roll(face) { onDiceResult(face) }
    }

    private fun onDiceResult(face: DiceFace) {
        currentFace = face
        textDiceHint.text = face.label
        when (face) {
            DiceFace.SKIP -> {
                SoundManager.play(SoundManager.Sfx.SKIP)
                showMessage(getString(R.string.msg_skip, player.name), true)
            }
            DiceFace.JOKER -> {
                SoundManager.play(SoundManager.Sfx.JOKER)
                showPanel(panelJoker)
            }
            else -> startTask(face.operation!!)
        }
    }

    private fun chooseJoker(op: Operation) {
        click()
        startTask(op)
    }

    // ---------------------------------------------------------------- Aufgabe

    private fun startTask(op: Operation) {
        val t = TaskGenerator.generate(op, settings.numberRange)
        task = t
        answer.setLength(0)
        textQuestion.text = t.question
        textAnswer.text = "_"
        textTaskInfo.text = resources.getQuantityString(
            R.plurals.task_info, settings.stepsFor(op), op.label, settings.stepsFor(op),
        )
        showPanel(panelTask)
        startTimer()
    }

    private fun startTimer(remainingMs: Long = settings.secondsPerTask * 1000L) {
        timer?.cancel()
        val totalMs = settings.secondsPerTask * 1000L
        progressTime.max = 1000
        progressTime.progress = (remainingMs * 1000 / totalMs).toInt()
        var lastSecondTicked = -1
        timer = TaskTimer(
            totalMs = remainingMs,
            tickMs = 50L,
            onTick = { millisUntilFinished ->
                timeLeftMs = millisUntilFinished
                progressTime.progress = (millisUntilFinished * 1000 / totalMs).toInt()
                val secs = ((millisUntilFinished + 999) / 1000).toInt()
                textTimeLeft.text = getString(R.string.seconds_left, secs)
                if (secs in 1..5 && secs != lastSecondTicked) {
                    lastSecondTicked = secs
                    SoundManager.play(SoundManager.Sfx.TICK, 0.6f)
                }
            },
            onFinish = { onTimeout() },
        ).start()
    }

    private fun onDigit(d: Int) {
        if (task == null) return
        if (answer.length >= maxAnswerDigits) return
        click()
        if (answer.length == 1 && answer[0] == '0') answer.setLength(0) // keine führenden Nullen
        answer.append(d)
        textAnswer.text = answer.toString()
    }

    private fun onBackspace() {
        if (answer.isEmpty()) return
        click()
        answer.setLength(answer.length - 1)
        textAnswer.text = if (answer.isEmpty()) "_" else answer.toString()
    }

    private fun submitAnswer() {
        val t = task ?: return
        if (answer.isEmpty()) return
        timer?.cancel()
        timer = null
        task = null
        val given = answer.toString().toIntOrNull()
        if (given == t.result) {
            onCorrect(t)
        } else {
            onWrong(t)
        }
    }

    private fun onTimeout() {
        val t = task ?: return
        timer = null
        task = null
        SoundManager.play(SoundManager.Sfx.TIMEOUT)
        showMessage(getString(R.string.msg_timeout, t.solution), true)
    }

    private fun onWrong(t: MathTask) {
        SoundManager.play(SoundManager.Sfx.WRONG)
        showMessage(getString(R.string.msg_wrong, t.solution), true)
    }

    private fun onCorrect(t: MathTask) {
        SoundManager.play(SoundManager.Sfx.CORRECT)
        val steps = settings.stepsFor(t.op)
        val from = player.position
        val to = min(from + steps, settings.boardFields - 1)
        showMessage(resources.getQuantityString(R.plurals.msg_correct, steps, steps), false)
        val idx = current
        boardView.animateMove(idx, from, to,
            onStep = { SoundManager.play(SoundManager.Sfx.STEP, 0.8f) },
            onEnd = {
                players[idx].position = to
                if (to >= settings.boardFields - 1) {
                    onWin(players[idx])
                } else {
                    btnNext.visibility = View.VISIBLE
                }
            })
    }

    // ---------------------------------------------------------------- Ablauf

    private fun showMessage(text: String, showNext: Boolean) {
        textMessage.text = text
        btnNext.visibility = if (showNext) View.VISIBLE else View.INVISIBLE
        showPanel(panelMessage)
    }

    private fun nextPlayer() {
        busy = false
        current = (current + 1) % players.size
        updateHeader()
        btnRoll.isEnabled = true
        textDiceHint.text = getString(R.string.tap_to_roll)
        showPanel(panelRoll)
    }

    private fun onWin(winner: Player) {
        SoundManager.play(SoundManager.Sfx.WIN)
        textWinner.text = getString(R.string.msg_winner, winner.figure, winner.name)
        textWinner.setTextColor(winner.color)
        showPanel(panelWin)
    }

    private fun restartGame() {
        timer?.cancel()
        timer = null
        task = null
        busy = false
        for (p in players) p.position = 0
        boardView.resetPositions()
        current = Random.nextInt(players.size)
        updateHeader()
        btnRoll.isEnabled = true
        textDiceHint.text = getString(R.string.tap_to_roll)
        showPanel(panelRoll)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (panelWin.visibility == View.VISIBLE) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.quit_title)
            .setMessage(R.string.quit_text)
            .setPositiveButton(R.string.quit_yes) { _, _ -> finish() }
            .setNegativeButton(R.string.quit_no, null)
            .show()
    }

    override fun onPause() {
        // Bei Unterbrechung (Anruf, Home-Taste) wird die Zeit angehalten.
        if (task != null && timer != null) {
            timer?.cancel()
            timer = null
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (task != null && timer == null) {
            startTimer(timeLeftMs.coerceAtLeast(1000L))
        }
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
