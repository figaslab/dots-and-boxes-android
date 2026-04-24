package com.devfigas.dotsandboxes

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.devfigas.dotsandboxes.game.ai.DotsAndBoxesAIEngine
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesLine
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesMove
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesRules
import com.devfigas.dotsandboxes.game.engine.PlayerColor
import com.devfigas.dotsandboxes.game.manager.DotsAndBoxesGameManager
import com.devfigas.dotsandboxes.game.state.DotsAndBoxesGamePhase
import com.devfigas.dotsandboxes.game.state.DotsAndBoxesGameResult
import com.devfigas.dotsandboxes.game.state.DotsAndBoxesGameState
import com.devfigas.dotsandboxes.ui.DotsAndBoxesBoardView
import com.devfigas.gridgame.model.PlayerSide
import com.devfigas.mockpvp.PvpGameFactoryRegistry
import com.devfigas.mockpvp.activity.GameModeActivity
import com.devfigas.mockpvp.activity.MainActivity
import com.devfigas.mockpvp.activity.TournamentSelectionActivity
import com.devfigas.mockpvp.ads.AdManager
import com.devfigas.mockpvp.analytics.AnalyticsManager
import com.devfigas.mockpvp.emoji.EmojiManager
import com.devfigas.mockpvp.game.PvpGameManagerHolder
import com.devfigas.mockpvp.game.PvpGameResult
import com.devfigas.mockpvp.model.GameMode
import com.devfigas.mockpvp.model.User
import com.devfigas.mockpvp.tournament.ProgressManager
import com.devfigas.mockpvp.tournament.Tournament
import com.devfigas.mockpvp.tournament.WalletManager
import com.devfigas.mockpvp.ui.GameResultDialog
import com.devfigas.mockpvp.ui.LeaveGameDialog
import com.devfigas.mockpvp.ui.ResignDialog
import com.devfigas.mockpvp.ui.VersusDialog
import com.devfigas.mockpvp.ui.WaitingRematchDialog
import ui.devfigas.uikit.customviews.RightDialogLayout
import ui.devfigas.uikit.tutorial.TutorialOverlayView
import com.devfigas.dotsandboxes.tutorial.ScriptedDotsAndBoxesAI
import com.devfigas.dotsandboxes.tutorial.TutorialDirector
import com.devfigas.dotsandboxes.tutorial.TutorialPreferences
import android.content.Intent
import kotlin.random.Random

class DotsAndBoxesGameActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DotsAndBoxesActivity"
        const val EXTRA_MY_COLOR = "extra_my_color"
        const val EXTRA_OPPONENT = "extra_opponent"
        const val EXTRA_GAME_ID = "extra_game_id"
        const val EXTRA_TUTORIAL_MODE = "extra_tutorial_mode"
    }

    // Tutorial state
    private var tutorialMode: Boolean = false
    private var tutorialDirector: TutorialDirector? = null
    private var tutorialOverlay: TutorialOverlayView? = null

    // Views
    private lateinit var btnBack: ImageView
    private lateinit var ivOpponentAvatar: ImageView
    private lateinit var tvOpponentName: TextView
    private lateinit var btnResign: TextView
    private lateinit var btnChat: ImageView
    private lateinit var tvGameStatus: TextView
    private lateinit var tvRedScore: TextView
    private lateinit var tvBlueScore: TextView
    private lateinit var boardView: DotsAndBoxesBoardView
    private lateinit var opponentTimerContainer: View
    private lateinit var tvOpponentTimer: TextView
    private lateinit var myTimerContainer: View
    private lateinit var tvMyTimer: TextView
    private lateinit var bannerContainer: View
    private lateinit var adDisclosureLabel: View
    private lateinit var chatBar: View
    private lateinit var etChatMessage: EditText
    private lateinit var btnSendChat: TextView
    private lateinit var chatBubble: RightDialogLayout
    private lateinit var tvChatMessage: TextView
    private lateinit var snackbarContainer: CoordinatorLayout

    // Game state
    private var gameManager: DotsAndBoxesGameManager? = null
    private var gameMode: GameMode = GameMode.CPU
    private var myColor: PlayerColor = PlayerColor.RED
    private var currentUser: User? = null
    private var opponent: User? = null
    private var gameId: String? = null
    private var tournament: Tournament? = null
    private var isRematch: Boolean = false

    // Dialogs
    private var versusDialog: VersusDialog? = null
    private var resignDialog: ResignDialog? = null
    private var leaveDialog: LeaveGameDialog? = null
    private var gameResultDialog: GameResultDialog? = null
    private var waitingRematchDialog: WaitingRematchDialog? = null

    // Timer
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var lastVibratedSecond: Int = -1

    private val usesPerTurnTimer: Boolean
        get() = !tutorialMode && tournament == null && (gameMode == GameMode.CPU || gameMode == GameMode.INTERNET ||
                gameMode == GameMode.BLUETOOTH || gameMode == GameMode.WIFI)

    // Chat bubble
    private val chatHandler = Handler(Looper.getMainLooper())
    private var chatDismissRunnable: Runnable? = null

    // Flags
    private var isGameStarted = false
    private var isActivityDestroyed = false
    private var gameStartTime: Long = 0
    private var hasShownGameOver = false

    // AI
    private val aiHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dots_and_boxes_game)

        parseIntentExtras()
        bindViews()
        setupListeners()
        setupOpponentInfo()
        setupChatVisibility()
        setupTimerVisibility()

        AnalyticsManager.logScreenView("DotsAndBoxesGame")

        if (tutorialMode) {
            startGame()
            startTutorial()
        } else {
            showVersusAnimation {
                startGame()
                if (usesPerTurnTimer) {
                    (gameManager as? com.devfigas.dotsandboxes.game.manager.LocalGameManager)?.activateTimer()
                    startPerTurnTimerUI()
                }
            }
        }
    }

    private fun startTutorial() {
        val overlay = tutorialOverlay ?: return
        overlay.visibility = View.VISIBLE
        val tutorUser = currentUser
        tutorialDirector = TutorialDirector(
            activity = this,
            overlay = overlay,
            boardView = boardView,
            playLine = { line -> gameManager?.selectLine(line) },
            onFinished = {
                val next = Intent(this, GameModeActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_USER, tutorUser)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(next)
                finish()
            }
        ).also { it.start() }
    }

    private fun parseIntentExtras() {
        tutorialMode = intent.getBooleanExtra(EXTRA_TUTORIAL_MODE, false)

        val gameModeStr = intent.getStringExtra(GameModeActivity.EXTRA_GAME_MODE)
        gameMode = if (tutorialMode) GameMode.CPU
        else if (gameModeStr != null) {
            try { GameMode.valueOf(gameModeStr) } catch (e: Exception) { GameMode.CPU }
        } else GameMode.CPU

        currentUser = intent.getParcelableExtra(MainActivity.EXTRA_USER)
            ?: User(name = "Player", avatar = EmojiManager.DEFAULT_AVATAR_ID)

        opponent = intent.getParcelableExtra(EXTRA_OPPONENT)
        gameId = intent.getStringExtra(EXTRA_GAME_ID)

        myColor = if (tutorialMode) PlayerColor.RED
        else {
            val myColorStr = intent.getStringExtra(EXTRA_MY_COLOR)
            if (myColorStr != null) {
                try { PlayerColor.valueOf(myColorStr) } catch (e: Exception) { randomColor() }
            } else randomColor()
        }

        tournament = if (tutorialMode) null
            else intent.getParcelableExtra(TournamentSelectionActivity.EXTRA_TOURNAMENT)

        if (opponent == null) {
            opponent = when {
                tutorialMode -> User(name = getString(R.string.tutorial_opponent_name), avatar = EmojiManager.DEFAULT_AVATAR_ID)
                gameMode == GameMode.CPU -> User(name = "CPU", avatar = EmojiManager.DEFAULT_AVATAR_ID)
                gameMode == GameMode.LOCAL -> User(name = "Player 2", avatar = EmojiManager.DEFAULT_AVATAR_ID)
                else -> User(name = "Opponent", avatar = EmojiManager.DEFAULT_AVATAR_ID)
            }
        }
    }

    private fun randomColor(): PlayerColor = if (Random.nextBoolean()) PlayerColor.RED else PlayerColor.BLUE

    private fun bindViews() {
        btnBack = findViewById(R.id.btn_back)
        ivOpponentAvatar = findViewById(R.id.iv_opponent_avatar)
        tvOpponentName = findViewById(R.id.tv_opponent_name)
        btnResign = findViewById(R.id.btn_resign)
        btnChat = findViewById(R.id.btn_chat)
        tvGameStatus = findViewById(R.id.tv_game_status)
        tvRedScore = findViewById(R.id.tv_red_score)
        tvBlueScore = findViewById(R.id.tv_blue_score)
        boardView = findViewById(R.id.dots_and_boxes_board_view)
        opponentTimerContainer = findViewById(R.id.opponent_timer_container)
        tvOpponentTimer = findViewById(R.id.tv_opponent_timer)
        myTimerContainer = findViewById(R.id.my_timer_container)
        tvMyTimer = findViewById(R.id.tv_my_timer)
        bannerContainer = findViewById(R.id.banner_container)
        adDisclosureLabel = findViewById(R.id.ad_disclosure_label)
        chatBar = findViewById(R.id.chat_bar)
        etChatMessage = findViewById(R.id.et_chat_message)
        btnSendChat = findViewById(R.id.btn_send_chat)
        chatBubble = findViewById(R.id.chat_bubble)
        tvChatMessage = findViewById(R.id.tv_chat_message)
        snackbarContainer = findViewById(R.id.snackbar_container)
        tutorialOverlay = findViewById(R.id.tutorial_overlay)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { onBackButtonPressed() }
        btnResign.setOnClickListener {
            if (tutorialMode) {
                tutorialDirector?.onSkipRequested()
                TutorialPreferences.markCompleted(this)
                finish()
            } else {
                showResignDialog()
            }
        }
        btnChat.setOnClickListener { toggleChatBar() }
        btnSendChat.setOnClickListener { sendChatMessage() }

        etChatMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendChatMessage()
                true
            } else false
        }

        boardView.setOnLineSelectedListener { line ->
            if (tutorialMode) {
                val allowed = tutorialDirector?.allowedLines()
                if (allowed != null && line !in allowed) return@setOnLineSelectedListener
            }
            selectLine(line)
        }
    }

    private fun setupOpponentInfo() {
        val opp = opponent ?: return
        tvOpponentName.text = opp.name
        val avatarId = opp.avatar.ifEmpty { EmojiManager.DEFAULT_AVATAR_ID }
        val avatarDrawable = EmojiManager.getAvatarDrawable(avatarId)
        ivOpponentAvatar.setImageResource(avatarDrawable)
    }

    private fun setupChatVisibility() {
        val showChat = gameMode.supportsChat()
        btnChat.visibility = if (showChat) View.VISIBLE else View.GONE
        chatBar.visibility = View.GONE
    }

    private fun setupTimerVisibility() {
        val showChessClock = tournament != null
        opponentTimerContainer.visibility = if (showChessClock) View.VISIBLE else View.GONE
        myTimerContainer.visibility = if (showChessClock || usesPerTurnTimer) View.VISIBLE else View.GONE
    }

    private fun showVersusAnimation(onComplete: () -> Unit) {
        val player = currentUser ?: User(name = "Player", avatar = EmojiManager.DEFAULT_AVATAR_ID)
        val opp = opponent ?: User(name = "Opponent", avatar = EmojiManager.DEFAULT_AVATAR_ID)

        val factory = PvpGameFactoryRegistry.get()
        val labels = factory.getSideLabels(this)
        val playerLabel = if (myColor == PlayerColor.RED) labels.first else labels.second
        val opponentLabel = if (myColor == PlayerColor.RED) labels.second else labels.first

        val showDialog = {
            if (!isFinishing && !isDestroyed) {
                versusDialog = VersusDialog(
                    context = this, player = player, opponent = opp,
                    playerSideLabel = playerLabel, opponentSideLabel = opponentLabel,
                    tournament = tournament,
                    onDismissed = {
                        versusDialog = null
                        onComplete()
                    }
                )
                versusDialog?.show()
            }
        }

        // Show interstitial before VersusDialog in tournament mode (first match only, not rematches)
        if (gameMode == GameMode.INTERNET && tournament != null && !isRematch) {
            AdManager.maybeShowInterstitial(this) {
                showDialog()
            }
        } else {
            showDialog()
        }
    }

    private fun startGame() {
        if (isActivityDestroyed) return
        isGameStarted = true
        gameStartTime = System.currentTimeMillis()
        hasShownGameOver = false
        btnResign.visibility = View.VISIBLE
        gameManager = createGameManager()
        AnalyticsManager.logGameStarted(gameMode, "unlimited", myColor.name)
        if (tournament != null) WalletManager.subtractCoins(this, tournament!!.fee)
    }

    private fun createGameManager(): DotsAndBoxesGameManager {
        return when (gameMode) {
            GameMode.CPU -> createCpuGameManager()
            GameMode.LOCAL -> createLocalGameManager()
            GameMode.BLUETOOTH, GameMode.WIFI -> createNetworkGameManager()
            GameMode.INTERNET -> createInternetGameManager()
        }
    }

    private fun createCpuGameManager(): DotsAndBoxesGameManager {
        val ai: com.devfigas.dotsandboxes.game.ai.DotsAndBoxesAI = if (tutorialMode)
            ScriptedDotsAndBoxesAI(TutorialDirector.AI_SCRIPT)
        else
            DotsAndBoxesAIEngine(level = 0)
        val cpuColor = myColor
        val cpuOpponent = opponent
        val manager = object : DotsAndBoxesGameManager(
            onStateChanged = { state -> runOnUiThread { onStateChanged(state) } },
            onError = { error -> runOnUiThread { onError(error) } }
        ) {
            override fun startGame(myColor: PlayerColor, opponent: User?) {
                val state = DotsAndBoxesGameState.createNew(GameMode.CPU, myColor, opponent)
                updateState(state)
                if (myColor != PlayerColor.RED) scheduleAiMove(state)
            }

            override fun resign() {
                val state = currentState ?: return
                val (redScore, blueScore) = DotsAndBoxesRules.getScore(state.board)
                updateState(state.copy(
                    phase = DotsAndBoxesGamePhase.GAME_OVER,
                    result = DotsAndBoxesGameResult(state.myColor.opposite(), DotsAndBoxesGameResult.Reason.RESIGNATION, redScore, blueScore)
                ))
            }

            override fun onMoveApplied(move: DotsAndBoxesMove, newState: DotsAndBoxesGameState, extraTurn: Boolean) {
                if (newState.phase == DotsAndBoxesGamePhase.PLAYING && newState.currentTurn != newState.myColor) {
                    scheduleAiMove(newState)
                }
            }

            fun scheduleAiMove(state: DotsAndBoxesGameState) {
                val delay = 300L + Random.nextLong(500)
                aiHandler.postDelayed({
                    if (isActivityDestroyed) return@postDelayed
                    val cs = this.getState() ?: return@postDelayed
                    if (cs.phase != DotsAndBoxesGamePhase.PLAYING) return@postDelayed
                    if (cs.currentTurn == cs.myColor) return@postDelayed
                    val aiMove = ai.selectMove(cs)
                    if (aiMove != null) applyMove(aiMove)
                }, delay)
            }

            fun initGame(color: PlayerColor, opp: User?) { startGame(color, opp) }
        }
        manager.initGame(cpuColor, cpuOpponent)
        return manager
    }

    private fun createLocalGameManager(): DotsAndBoxesGameManager {
        val localColor = myColor
        val localOpponent = opponent
        val manager = object : DotsAndBoxesGameManager(
            onStateChanged = { state -> runOnUiThread { onStateChanged(state) } },
            onError = { error -> runOnUiThread { onError(error) } }
        ) {
            override fun startGame(myColor: PlayerColor, opponent: User?) {
                val state = DotsAndBoxesGameState.createNew(GameMode.LOCAL, myColor, opponent)
                updateState(state)
            }

            override fun resign() {
                val state = currentState ?: return
                val (redScore, blueScore) = DotsAndBoxesRules.getScore(state.board)
                updateState(state.copy(
                    phase = DotsAndBoxesGamePhase.GAME_OVER,
                    result = DotsAndBoxesGameResult(state.currentTurn.opposite(), DotsAndBoxesGameResult.Reason.RESIGNATION, redScore, blueScore)
                ))
            }

            override fun selectLine(line: DotsAndBoxesLine) {
                val state = currentState ?: return
                if (state.phase != DotsAndBoxesGamePhase.PLAYING) return
                if (state.board.isLineDrawn(line)) return
                val move = DotsAndBoxesMove(line, state.currentTurn)
                applyMove(move)
            }

            fun initGame(color: PlayerColor, opp: User?) { startGame(color, opp) }
        }
        manager.initGame(localColor, localOpponent)
        return manager
    }

    private fun createNetworkGameManager(): DotsAndBoxesGameManager {
        val pvpManager = PvpGameManagerHolder.get()
        PvpGameManagerHolder.setGameActivityActive(true)

        val manager = object : DotsAndBoxesGameManager(
            onStateChanged = { state -> runOnUiThread { onStateChanged(state) } },
            onError = { error -> runOnUiThread { onError(error) } }
        ) {
            override fun startGame(myColor: PlayerColor, opponent: User?) {
                val state = if (gameId != null) {
                    DotsAndBoxesGameState.createForChallenge(gameId!!, gameMode, myColor, opponent)
                } else {
                    DotsAndBoxesGameState.createNew(gameMode, myColor, opponent)
                }
                updateState(state)
            }

            override fun resign() {
                val state = currentState ?: return
                val (redScore, blueScore) = DotsAndBoxesRules.getScore(state.board)
                updateState(state.copy(
                    phase = DotsAndBoxesGamePhase.GAME_OVER,
                    result = DotsAndBoxesGameResult(state.myColor.opposite(), DotsAndBoxesGameResult.Reason.RESIGNATION, redScore, blueScore)
                ))
            }

            override fun leave() {
                val state = currentState ?: return
                val (redScore, blueScore) = DotsAndBoxesRules.getScore(state.board)
                updateState(state.copy(
                    phase = DotsAndBoxesGamePhase.GAME_OVER,
                    result = DotsAndBoxesGameResult(state.myColor.opposite(), DotsAndBoxesGameResult.Reason.OPPONENT_LEFT, redScore, blueScore)
                ))
            }

            fun initGame(color: PlayerColor, opp: User?) { startGame(color, opp) }
        }
        manager.initGame(myColor, opponent)
        return manager
    }

    private fun createInternetGameManager(): DotsAndBoxesGameManager {
        val aiLevel = (tournament?.id ?: Random.nextInt(3, 8)).coerceIn(1, 9)
        val ai = DotsAndBoxesAIEngine(level = aiLevel)
        val manager = object : DotsAndBoxesGameManager(
            onStateChanged = { state -> runOnUiThread { onStateChanged(state) } },
            onError = { error -> runOnUiThread { onError(error) } }
        ) {
            override fun startGame(myColor: PlayerColor, opponent: User?) {
                val state = DotsAndBoxesGameState.createNew(GameMode.INTERNET, myColor, opponent).let {
                    if (tournament != null) {
                        it.copy(
                            timerActive = true,
                            redTimeRemainingMs = DotsAndBoxesGameState.DEFAULT_INITIAL_TIME_MS,
                            blueTimeRemainingMs = DotsAndBoxesGameState.DEFAULT_INITIAL_TIME_MS,
                            isUnlimitedTime = false
                        )
                    } else it
                }
                updateState(state)
                if (myColor != PlayerColor.RED) scheduleBotMove(state)
            }

            override fun resign() {
                val state = currentState ?: return
                val (redScore, blueScore) = DotsAndBoxesRules.getScore(state.board)
                updateState(state.copy(
                    phase = DotsAndBoxesGamePhase.GAME_OVER,
                    result = DotsAndBoxesGameResult(state.myColor.opposite(), DotsAndBoxesGameResult.Reason.RESIGNATION, redScore, blueScore)
                ))
            }

            override fun onMoveApplied(move: DotsAndBoxesMove, newState: DotsAndBoxesGameState, extraTurn: Boolean) {
                if (newState.phase == DotsAndBoxesGamePhase.PLAYING && newState.currentTurn != newState.myColor) {
                    scheduleBotMove(newState)
                }
            }

            override fun sendChatMessage(message: String) {
                val state = currentState ?: return
                updateState(state.copy(lastChatMessage = message, lastChatSender = "me"))
            }

            fun scheduleBotMove(state: DotsAndBoxesGameState) {
                val delay = ai.calculateThinkingTimeMs(state)
                aiHandler.postDelayed({
                    if (isActivityDestroyed) return@postDelayed
                    val cs = this.getState() ?: return@postDelayed
                    if (cs.phase != DotsAndBoxesGamePhase.PLAYING) return@postDelayed
                    if (cs.currentTurn == cs.myColor) return@postDelayed
                    val botMove = ai.selectMove(cs)
                    if (botMove != null) applyMove(botMove)
                }, delay)
            }

            fun initGame(color: PlayerColor, opp: User?) { startGame(color, opp) }
        }
        manager.initGame(myColor, opponent)
        return manager
    }

    // ==================== STATE UPDATES ====================

    private fun onStateChanged(state: DotsAndBoxesGameState) {
        if (isActivityDestroyed) return
        boardView.updateFromState(state)
        updateScoreDisplay(state)
        updateStatusText(state)
        updateTimers(state)
        handleIncomingChat(state)

        if (state.phase == DotsAndBoxesGamePhase.GAME_OVER && !hasShownGameOver) {
            hasShownGameOver = true
            if (!tutorialMode) showGameOverDialog(state)
        }
        if (state.phase == DotsAndBoxesGamePhase.WAITING_REMATCH) {
            showWaitingRematchDialog()
        }

        if (tutorialMode) {
            tutorialDirector?.onGameStateChanged(state)
        }
    }

    private fun onError(error: String) {
        if (isActivityDestroyed) return
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    private fun updateScoreDisplay(state: DotsAndBoxesGameState) {
        tvRedScore.text = state.getRedScore().toString()
        tvBlueScore.text = state.getBlueScore().toString()
    }

    private fun updateStatusText(state: DotsAndBoxesGameState) {
        val statusText = when {
            state.phase == DotsAndBoxesGamePhase.GAME_OVER -> getString(R.string.game_over)
            gameMode == GameMode.LOCAL -> {
                if (state.currentTurn == PlayerColor.RED) {
                    "${getString(R.string.assign_red)} - ${getString(R.string.your_turn)}"
                } else {
                    "${getString(R.string.assign_blue)} - ${getString(R.string.your_turn)}"
                }
            }
            state.isMyTurn() -> getString(R.string.your_turn)
            else -> getString(R.string.opponent_turn)
        }
        tvGameStatus.text = statusText
    }

    // ==================== TIMER ====================

    private fun updateTimers(state: DotsAndBoxesGameState) {
        if (usesPerTurnTimer) return  // handled by startPerTurnTimerUI()
        if (state.isUnlimitedTime || !state.timerActive) {
            opponentTimerContainer.visibility = View.GONE
            myTimerContainer.visibility = View.GONE
            stopTimer()
            return
        }
        opponentTimerContainer.visibility = View.VISIBLE
        myTimerContainer.visibility = View.VISIBLE
        tvMyTimer.text = formatTime(state.myTimeRemainingMs())
        tvOpponentTimer.text = formatTime(state.opponentTimeRemainingMs())
        startTimerCountdown(state)
    }

    private fun startTimerCountdown(state: DotsAndBoxesGameState) {
        stopTimer()
        timerRunnable = object : Runnable {
            override fun run() {
                if (isActivityDestroyed) return
                val cs = gameManager?.getState() ?: return
                if (cs.phase != DotsAndBoxesGamePhase.PLAYING) return
                if (!cs.timerActive || cs.isUnlimitedTime) return

                val elapsed = System.currentTimeMillis() - cs.turnStartTime
                if (cs.isMyTurn()) {
                    val remaining = maxOf(0, cs.myTimeRemainingMs() - elapsed)
                    tvMyTimer.text = formatTime(remaining)
                    tvOpponentTimer.text = formatTime(cs.opponentTimeRemainingMs())
                    if (remaining <= 0) { handleTimeExpired(cs); return }
                } else {
                    val remaining = maxOf(0, cs.opponentTimeRemainingMs() - elapsed)
                    tvOpponentTimer.text = formatTime(remaining)
                    tvMyTimer.text = formatTime(cs.myTimeRemainingMs())
                    if (remaining <= 0) { handleTimeExpired(cs); return }
                }
                timerHandler.postDelayed(this, 100)
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun handleTimeExpired(state: DotsAndBoxesGameState) {
        stopTimer()
        val winner = state.currentTurn.opposite()
        val (redScore, blueScore) = DotsAndBoxesRules.getScore(state.board)
        val newState = state.copy(
            phase = DotsAndBoxesGamePhase.GAME_OVER,
            result = DotsAndBoxesGameResult(winner, DotsAndBoxesGameResult.Reason.TIMEOUT, redScore, blueScore)
        )
        gameManager?.let { manager ->
            try {
                val method = DotsAndBoxesGameManager::class.java.getDeclaredMethod("updateState", DotsAndBoxesGameState::class.java)
                method.isAccessible = true
                method.invoke(manager, newState)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update state for timeout", e)
            }
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun startPerTurnTimerUI() {
        stopTimer()
        lastVibratedSecond = -1
        opponentTimerContainer.visibility = View.GONE
        myTimerContainer.visibility = View.VISIBLE

        timerRunnable = object : Runnable {
            override fun run() {
                if (isActivityDestroyed) return
                val state = gameManager?.getState() ?: return
                if (state.phase != DotsAndBoxesGamePhase.PLAYING) {
                    myTimerContainer.visibility = View.GONE
                    return
                }
                if (!state.timerActive || !state.isMyTurn()) {
                    myTimerContainer.visibility = View.GONE
                    timerHandler.postDelayed(this, 500)
                    return
                }
                myTimerContainer.visibility = View.VISIBLE
                val remainingMs = state.remainingTurnTime()
                tvMyTimer.text = formatTime(remainingMs)

                val seconds = (remainingMs / 1000).toInt()
                val bg = tvMyTimer.background as? GradientDrawable ?: GradientDrawable().also {
                    it.cornerRadius = 8 * resources.displayMetrics.density
                }
                when {
                    seconds < 5 -> {
                        bg.setColor(ContextCompat.getColor(this@DotsAndBoxesGameActivity, R.color.timer_critical))
                        tvMyTimer.background = bg
                        if (seconds != lastVibratedSecond && seconds > 0) {
                            lastVibratedSecond = seconds
                            vibrate(100)
                        }
                    }
                    seconds < 10 -> {
                        bg.setColor(ContextCompat.getColor(this@DotsAndBoxesGameActivity, R.color.timer_warning))
                        tvMyTimer.background = bg
                    }
                    else -> {
                        bg.setColor(ContextCompat.getColor(this@DotsAndBoxesGameActivity, R.color.timer_normal))
                        tvMyTimer.background = bg
                    }
                }

                if (remainingMs > 0) {
                    timerHandler.postDelayed(this, 500)
                } else {
                    (gameManager as? com.devfigas.dotsandboxes.game.manager.LocalGameManager)?.checkAndHandleTimeout()
                }
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    @Suppress("DEPRECATION")
    private fun vibrate(millis: Long) {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(millis)
        }
    }

    // ==================== USER INTERACTION ====================

    private fun selectLine(line: DotsAndBoxesLine) {
        val manager = gameManager ?: return
        val state = manager.getState() ?: return
        if (state.phase != DotsAndBoxesGamePhase.PLAYING) return
        if (gameMode != GameMode.LOCAL && !state.isMyTurn()) return
        manager.selectLine(line)
    }

    // ==================== CHAT ====================

    private fun toggleChatBar() {
        if (chatBar.visibility == View.VISIBLE) {
            chatBar.visibility = View.GONE
            hideKeyboard()
        } else {
            chatBar.visibility = View.VISIBLE
            etChatMessage.requestFocus()
        }
    }

    private fun sendChatMessage() {
        val message = etChatMessage.text.toString().trim()
        if (message.isEmpty()) return
        gameManager?.sendChatMessage(message)
        etChatMessage.text.clear()
        hideKeyboard()
        chatBar.visibility = View.GONE
    }

    private fun handleIncomingChat(state: DotsAndBoxesGameState) {
        val message = state.incomingChatMessage ?: state.lastChatMessage
        if (message.isNullOrEmpty()) return
        showChatBubble(message)
    }

    private fun showChatBubble(message: String) {
        tvChatMessage.text = message
        chatBubble.visibility = View.VISIBLE
        chatDismissRunnable?.let { chatHandler.removeCallbacks(it) }
        chatDismissRunnable = Runnable { chatBubble.visibility = View.GONE }
        chatHandler.postDelayed(chatDismissRunnable!!, 4000)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etChatMessage.windowToken, 0)
    }

    // ==================== DIALOGS ====================

    private fun showResignDialog() {
        if (resignDialog?.isShowing == true) return
        val state = gameManager?.getState() ?: return
        if (state.phase != DotsAndBoxesGamePhase.PLAYING) return

        resignDialog = ResignDialog(
            context = this,
            message = getString(R.string.resign_confirm),
            onContinue = { resignDialog = null },
            onResign = { resignDialog = null; gameManager?.resign() }
        )
        resignDialog?.show()
    }

    private fun showLeaveDialog() {
        if (leaveDialog?.isShowing == true) return
        leaveDialog = LeaveGameDialog(
            context = this,
            message = getString(R.string.leave_confirm),
            onStay = { leaveDialog = null },
            onLeave = { leaveDialog = null; gameManager?.leave(); finish() }
        )
        leaveDialog?.show()
    }

    private fun showGameOverDialog(state: DotsAndBoxesGameState) {
        stopTimer()
        btnResign.visibility = View.GONE

        val result = state.result ?: return
        val player = currentUser ?: User(name = "Player", avatar = EmojiManager.DEFAULT_AVATAR_ID)
        val opp = opponent ?: User(name = "Opponent", avatar = EmojiManager.DEFAULT_AVATAR_ID)

        val playerWon = result.winner == state.myColor
        val isDraw = result.winner == null

        val resultTitle = when {
            isDraw -> getString(R.string.draw)
            playerWon -> getString(R.string.you_win)
            else -> getString(R.string.you_lose)
        }

        val reasonText = when (result.reason) {
            DotsAndBoxesGameResult.Reason.GAME_COMPLETE -> "${result.redScore} - ${result.blueScore}"
            DotsAndBoxesGameResult.Reason.RESIGNATION -> getString(R.string.opponent_resigned)
            DotsAndBoxesGameResult.Reason.TIMEOUT -> getString(R.string.timeout_loss)
            DotsAndBoxesGameResult.Reason.OPPONENT_LEFT -> getString(R.string.opponent_left)
            DotsAndBoxesGameResult.Reason.AGREEMENT -> getString(R.string.draw)
        }

        val pvpResult = PvpGameResult(
            winnerSide = result.winner?.let { if (it == PlayerColor.RED) PlayerSide.FIRST else PlayerSide.SECOND },
            reason = when (result.reason) {
                DotsAndBoxesGameResult.Reason.GAME_COMPLETE -> PvpGameResult.Reason.GAME_RULE
                DotsAndBoxesGameResult.Reason.RESIGNATION -> PvpGameResult.Reason.RESIGNATION
                DotsAndBoxesGameResult.Reason.TIMEOUT -> PvpGameResult.Reason.TIMEOUT
                DotsAndBoxesGameResult.Reason.OPPONENT_LEFT -> PvpGameResult.Reason.OPPONENT_LEFT
                DotsAndBoxesGameResult.Reason.AGREEMENT -> PvpGameResult.Reason.DRAW
            },
            displayText = resultTitle
        )

        val isTournament = tournament != null
        if (isTournament && playerWon) {
            WalletManager.addCoins(this, tournament!!.prize)
            ProgressManager.addVictory(this)
        }

        val durationSeconds = (System.currentTimeMillis() - gameStartTime) / 1000
        AnalyticsManager.logGameEnded(
            gameMode = gameMode,
            result = when { isDraw -> "draw"; playerWon -> "win"; else -> "loss" },
            durationSeconds = durationSeconds,
            movesCount = state.moveHistory.size,
            endReason = result.reason.name
        )

        val showRematch = gameMode != GameMode.BLUETOOTH && gameMode != GameMode.WIFI
        val useRematchTimeout = gameMode == GameMode.INTERNET

        Handler(Looper.getMainLooper()).postDelayed({
            if (isActivityDestroyed) return@postDelayed
            gameResultDialog = GameResultDialog(
                activity = this, player = player, opponent = opp,
                result = pvpResult, resultTitle = resultTitle, reasonText = reasonText,
                playerWon = playerWon, tournament = tournament, isTournamentMode = isTournament,
                showRematchButton = showRematch, useRematchTimeout = useRematchTimeout,
                onRematch = { handleRematch() }, onExit = { handleExit() }
            )
            gameResultDialog?.show()
        }, 800)
    }

    private fun handleRematch() {
        gameResultDialog?.dismiss()
        gameResultDialog = null
        hasShownGameOver = false

        myColor = myColor.opposite()

        if (tournament != null) {
            val balance = WalletManager.getBalance(this)
            if (balance < tournament!!.fee) {
                Toast.makeText(this, R.string.draw, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        isRematch = true
        showVersusAnimation {
            startGame()
            if (usesPerTurnTimer) {
                (gameManager as? com.devfigas.dotsandboxes.game.manager.LocalGameManager)?.activateTimer()
                startPerTurnTimerUI()
            }
        }
    }

    private fun handleExit() {
        gameResultDialog?.dismiss()
        gameResultDialog = null
        finish()
    }

    private fun showWaitingRematchDialog() {
        val opp = opponent ?: return
        if (waitingRematchDialog?.isShowing == true) return
        waitingRematchDialog = WaitingRematchDialog(
            context = this, opponent = opp,
            onCancel = { waitingRematchDialog = null; gameManager?.voteRematch(false) },
            onTimeout = { waitingRematchDialog = null; Toast.makeText(this, getString(R.string.opponent_declined_rematch), Toast.LENGTH_SHORT).show() }
        )
        waitingRematchDialog?.show()
    }

    // ==================== BACK BUTTON ====================

    private fun onBackButtonPressed() {
        val state = gameManager?.getState()
        when {
            state == null -> finish()
            state.phase == DotsAndBoxesGamePhase.GAME_OVER -> finish()
            state.phase == DotsAndBoxesGamePhase.PLAYING -> showLeaveDialog()
            else -> finish()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() { onBackButtonPressed() }

    private fun setupBannerAd() {
        if (tutorialMode) return
        adDisclosureLabel.visibility = View.GONE
        if (tournament == null) {
            AdManager.showBanner(
                this,
                bannerContainer as ViewGroup,
                onShown = { adDisclosureLabel.visibility = View.VISIBLE },
                onFailed = { adDisclosureLabel.visibility = View.GONE }
            )
        }
    }

    override fun onResume() { super.onResume(); setupBannerAd() }
    override fun onPause() { super.onPause(); stopTimer() }

    override fun onDestroy() {
        isActivityDestroyed = true
        super.onDestroy()
        stopTimer()
        aiHandler.removeCallbacksAndMessages(null)
        chatDismissRunnable?.let { chatHandler.removeCallbacks(it) }
        versusDialog?.dismiss()
        resignDialog?.dismiss()
        leaveDialog?.dismiss()
        gameResultDialog?.dismiss()
        waitingRematchDialog?.dismiss()
        if (gameMode.isNetworkMode()) PvpGameManagerHolder.setGameActivityActive(false)
        AdManager.hideBanner()
    }
}
