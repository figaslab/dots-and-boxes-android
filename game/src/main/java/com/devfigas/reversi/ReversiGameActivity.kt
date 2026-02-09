package com.devfigas.reversi

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.devfigas.gridgame.model.GridPosition
import com.devfigas.gridgame.model.PlayerSide
import com.devfigas.mockpvp.PvpGameFactoryRegistry
import com.devfigas.mockpvp.activity.GameModeActivity
import com.devfigas.mockpvp.activity.MainActivity
import com.devfigas.mockpvp.activity.TournamentSelectionActivity
import com.devfigas.mockpvp.ads.AdManager
import com.devfigas.mockpvp.analytics.AnalyticsManager
import com.devfigas.mockpvp.bot.BotProvider
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
import com.devfigas.reversi.game.ai.ReversiAIEngine
import com.devfigas.reversi.game.engine.Position
import com.devfigas.reversi.game.engine.ReversiColor
import com.devfigas.reversi.game.engine.ReversiRules
import com.devfigas.reversi.game.manager.ReversiGameManager
import com.devfigas.reversi.game.state.ReversiGamePhase
import com.devfigas.reversi.game.state.ReversiGameResult
import com.devfigas.reversi.game.state.ReversiGameState
import com.devfigas.reversi.ui.ReversiBoardView
import com.google.android.material.snackbar.Snackbar
import ui.devfigas.uikit.customviews.RightDialogLayout
import kotlin.random.Random

class ReversiGameActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ReversiGameActivity"
        const val EXTRA_MY_COLOR = "extra_my_color"
        const val EXTRA_OPPONENT = "extra_opponent"
        const val EXTRA_GAME_ID = "extra_game_id"
    }

    // Views
    private lateinit var btnBack: ImageView
    private lateinit var ivOpponentAvatar: ImageView
    private lateinit var tvOpponentName: TextView
    private lateinit var btnResign: TextView
    private lateinit var btnChat: ImageView
    private lateinit var tvGameStatus: TextView
    private lateinit var tvBlackScore: TextView
    private lateinit var tvWhiteScore: TextView
    private lateinit var boardView: ReversiBoardView
    private lateinit var opponentTimerContainer: View
    private lateinit var tvOpponentTimer: TextView
    private lateinit var myTimerContainer: View
    private lateinit var tvMyTimer: TextView
    private lateinit var bannerContainer: View
    private lateinit var chatBar: View
    private lateinit var etChatMessage: EditText
    private lateinit var btnSendChat: TextView
    private lateinit var chatBubble: RightDialogLayout
    private lateinit var tvChatMessage: TextView
    private lateinit var statusDiscIndicator: View
    private lateinit var snackbarContainer: CoordinatorLayout

    // Game state
    private var gameManager: ReversiGameManager? = null
    private var gameMode: GameMode = GameMode.CPU
    private var myColor: ReversiColor = ReversiColor.BLACK
    private var currentUser: User? = null
    private var opponent: User? = null
    private var gameId: String? = null
    private var tournament: Tournament? = null

    // Dialogs
    private var versusDialog: VersusDialog? = null
    private var resignDialog: ResignDialog? = null
    private var leaveDialog: LeaveGameDialog? = null
    private var gameResultDialog: GameResultDialog? = null
    private var waitingRematchDialog: WaitingRematchDialog? = null

    // Timer
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    // Chat bubble
    private val chatHandler = Handler(Looper.getMainLooper())
    private var chatDismissRunnable: Runnable? = null

    // Flags
    private var isGameStarted = false
    private var isActivityDestroyed = false
    private var gameStartTime: Long = 0
    private var hasShownGameOver = false
    private var lastPassSnackbar: Snackbar? = null

    // AI
    private val aiHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reversi_game)

        parseIntentExtras()
        bindViews()
        setupListeners()
        setupOpponentInfo()
        setupChatVisibility()
        setupTimerVisibility()

        // Log screen view
        AnalyticsManager.logScreenView("ReversiGame")

        // Show versus animation, then start game
        showVersusAnimation {
            startGame()
        }
    }

    // ==================== INITIALIZATION ====================

    private fun parseIntentExtras() {
        val gameModeStr = intent.getStringExtra(GameModeActivity.EXTRA_GAME_MODE)
        gameMode = if (gameModeStr != null) {
            try { GameMode.valueOf(gameModeStr) } catch (e: Exception) { GameMode.CPU }
        } else {
            GameMode.CPU
        }

        currentUser = intent.getParcelableExtra(MainActivity.EXTRA_USER)
            ?: User(name = "Player", avatar = EmojiManager.DEFAULT_AVATAR_ID)

        opponent = intent.getParcelableExtra(EXTRA_OPPONENT)

        gameId = intent.getStringExtra(EXTRA_GAME_ID)

        val myColorStr = intent.getStringExtra(EXTRA_MY_COLOR)
        myColor = if (myColorStr != null) {
            try { ReversiColor.valueOf(myColorStr) } catch (e: Exception) { randomColor() }
        } else {
            randomColor()
        }

        tournament = intent.getParcelableExtra(TournamentSelectionActivity.EXTRA_TOURNAMENT)

        // Generate default opponent for CPU/LOCAL modes
        if (opponent == null) {
            opponent = when (gameMode) {
                GameMode.CPU -> {
                    User(name = "CPU", avatar = EmojiManager.DEFAULT_AVATAR_ID)
                }
                GameMode.LOCAL -> User(name = "Player 2", avatar = EmojiManager.DEFAULT_AVATAR_ID)
                else -> User(name = "Opponent", avatar = EmojiManager.DEFAULT_AVATAR_ID)
            }
        }
    }

    private fun randomColor(): ReversiColor {
        return if (Random.nextBoolean()) ReversiColor.BLACK else ReversiColor.WHITE
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btn_back)
        ivOpponentAvatar = findViewById(R.id.iv_opponent_avatar)
        tvOpponentName = findViewById(R.id.tv_opponent_name)
        btnResign = findViewById(R.id.btn_resign)
        btnChat = findViewById(R.id.btn_chat)
        tvGameStatus = findViewById(R.id.tv_game_status)
        tvBlackScore = findViewById(R.id.tv_black_score)
        tvWhiteScore = findViewById(R.id.tv_white_score)
        boardView = findViewById(R.id.reversi_board_view)
        opponentTimerContainer = findViewById(R.id.opponent_timer_container)
        tvOpponentTimer = findViewById(R.id.tv_opponent_timer)
        myTimerContainer = findViewById(R.id.my_timer_container)
        tvMyTimer = findViewById(R.id.tv_my_timer)
        bannerContainer = findViewById(R.id.banner_container)
        chatBar = findViewById(R.id.chat_bar)
        etChatMessage = findViewById(R.id.et_chat_message)
        btnSendChat = findViewById(R.id.btn_send_chat)
        chatBubble = findViewById(R.id.chat_bubble)
        tvChatMessage = findViewById(R.id.tv_chat_message)
        statusDiscIndicator = findViewById(R.id.status_disc_indicator)
        snackbarContainer = findViewById(R.id.snackbar_container)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { onBackButtonPressed() }

        btnResign.setOnClickListener { showResignDialog() }

        btnChat.setOnClickListener { toggleChatBar() }

        btnSendChat.setOnClickListener { sendChatMessage() }

        etChatMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendChatMessage()
                true
            } else false
        }

        boardView.setOnSquareClickListener { position ->
            selectSquare(position)
        }

        // Flip the board if playing as white so black is always at the top
        boardView.setFlipped(myColor == ReversiColor.WHITE)
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
        chatBar.visibility = View.GONE // Hidden by default, toggled by chat button
    }

    private fun setupTimerVisibility() {
        // Timers are shown for tournament mode
        val showTimers = tournament != null
        opponentTimerContainer.visibility = if (showTimers) View.VISIBLE else View.GONE
        myTimerContainer.visibility = if (showTimers) View.VISIBLE else View.GONE
    }

    // ==================== VERSUS ANIMATION ====================

    private fun showVersusAnimation(onComplete: () -> Unit) {
        val player = currentUser ?: User(name = "Player", avatar = EmojiManager.DEFAULT_AVATAR_ID)
        val opp = opponent ?: User(name = "Opponent", avatar = EmojiManager.DEFAULT_AVATAR_ID)

        val factory = PvpGameFactoryRegistry.get()
        val labels = factory.getSideLabels(this)
        val playerLabel = if (myColor == ReversiColor.BLACK) labels.first else labels.second
        val opponentLabel = if (myColor == ReversiColor.BLACK) labels.second else labels.first

        versusDialog = VersusDialog(
            context = this,
            player = player,
            opponent = opp,
            playerSideLabel = playerLabel,
            opponentSideLabel = opponentLabel,
            tournament = tournament,
            onDismissed = {
                versusDialog = null
                onComplete()
            }
        )
        versusDialog?.show()
    }

    // ==================== GAME MANAGEMENT ====================

    private fun startGame() {
        if (isActivityDestroyed) return

        isGameStarted = true
        gameStartTime = System.currentTimeMillis()
        hasShownGameOver = false

        // Show resign button during play
        btnResign.visibility = View.VISIBLE

        // Create the game manager based on mode
        gameManager = createGameManager()

        // Log game start
        AnalyticsManager.logGameStarted(gameMode, "unlimited", myColor.name)

        // Deduct tournament fee if applicable
        if (tournament != null) {
            WalletManager.subtractCoins(this, tournament!!.fee)
        }
    }

    private fun createGameManager(): ReversiGameManager {
        return when (gameMode) {
            GameMode.CPU -> createCpuGameManager()
            GameMode.LOCAL -> createLocalGameManager()
            GameMode.BLUETOOTH, GameMode.WIFI -> createNetworkGameManager()
            GameMode.INTERNET -> createInternetGameManager()
        }
    }

    private fun createCpuGameManager(): ReversiGameManager {
        val ai = ReversiAIEngine(level = 0)
        val cpuColor = myColor
        val cpuOpponent = opponent
        val manager = object : ReversiGameManager(
            onStateChanged = { state -> runOnUiThread { onStateChanged(state) } },
            onError = { error -> runOnUiThread { onError(error) } }
        ) {
            override fun startGame(myColor: ReversiColor, opponent: User?) {
                val state = ReversiGameState.createNew(
                    gameMode = GameMode.CPU,
                    myColor = myColor,
                    opponent = opponent
                )
                updateState(state)

                // If AI goes first (player is white), trigger AI move
                if (myColor != ReversiColor.BLACK) {
                    scheduleAiMove(state)
                }
            }

            override fun resign() {
                val state = currentState ?: return
                val (blackScore, whiteScore) = ReversiRules.getScore(state.board)
                val newState = state.copy(
                    phase = ReversiGamePhase.GAME_OVER,
                    result = ReversiGameResult(
                        winner = state.myColor.opposite(),
                        reason = ReversiGameResult.Reason.RESIGNATION,
                        blackScore = blackScore,
                        whiteScore = whiteScore
                    )
                )
                updateState(newState)
            }

            override fun onMoveApplied(move: com.devfigas.reversi.game.engine.ReversiMove, newState: ReversiGameState) {
                if (newState.phase == ReversiGamePhase.PLAYING && newState.currentTurn != newState.myColor) {
                    scheduleAiMove(newState)
                }
            }

            fun scheduleAiMove(state: ReversiGameState) {
                // CPU is a declared bot: fast response (300-800ms), not human-like
                val delay = 300L + Random.nextLong(500)
                aiHandler.postDelayed({
                    if (isActivityDestroyed) return@postDelayed
                    val currentState = this.getState() ?: return@postDelayed
                    if (currentState.phase != ReversiGamePhase.PLAYING) return@postDelayed
                    if (currentState.currentTurn == currentState.myColor) return@postDelayed

                    val aiMove = ai.selectMove(currentState)
                    if (aiMove != null) {
                        applyMove(aiMove)
                    }
                }, delay)
            }

            fun initGame(color: ReversiColor, opp: User?) {
                startGame(color, opp)
            }
        }
        manager.initGame(cpuColor, cpuOpponent)
        return manager
    }

    private fun createLocalGameManager(): ReversiGameManager {
        val localColor = myColor
        val localOpponent = opponent
        val manager = object : ReversiGameManager(
            onStateChanged = { state -> runOnUiThread { onStateChanged(state) } },
            onError = { error -> runOnUiThread { onError(error) } }
        ) {
            override fun startGame(myColor: ReversiColor, opponent: User?) {
                val state = ReversiGameState.createNew(
                    gameMode = GameMode.LOCAL,
                    myColor = myColor,
                    opponent = opponent
                )
                updateState(state)
            }

            override fun resign() {
                val state = currentState ?: return
                val (blackScore, whiteScore) = ReversiRules.getScore(state.board)
                val newState = state.copy(
                    phase = ReversiGamePhase.GAME_OVER,
                    result = ReversiGameResult(
                        winner = state.currentTurn.opposite(),
                        reason = ReversiGameResult.Reason.RESIGNATION,
                        blackScore = blackScore,
                        whiteScore = whiteScore
                    )
                )
                updateState(newState)
            }

            override fun selectSquare(position: Position) {
                // In local mode, both sides can play
                val state = currentState ?: return
                if (state.phase != ReversiGamePhase.PLAYING) return
                if (position !in state.validMoves) return

                val validMoves = ReversiRules.getValidMoves(state.board, state.currentTurn)
                val move = validMoves.find { it.position == position } ?: return
                applyMove(move)
            }

            fun initGame(color: ReversiColor, opp: User?) {
                startGame(color, opp)
            }
        }
        manager.initGame(localColor, localOpponent)
        return manager
    }

    private fun createNetworkGameManager(): ReversiGameManager {
        // Retrieve the existing network game manager from PvpGameManagerHolder
        val pvpManager = PvpGameManagerHolder.get()
        PvpGameManagerHolder.setGameActivityActive(true)

        // Create a wrapper that delegates to the real network manager
        val manager = object : ReversiGameManager(
            onStateChanged = { state -> runOnUiThread { onStateChanged(state) } },
            onError = { error -> runOnUiThread { onError(error) } }
        ) {
            override fun startGame(myColor: ReversiColor, opponent: User?) {
                val state = if (gameId != null) {
                    ReversiGameState.createForChallenge(
                        gameId = gameId!!,
                        gameMode = gameMode,
                        myColor = myColor,
                        opponent = opponent
                    )
                } else {
                    ReversiGameState.createNew(
                        gameMode = gameMode,
                        myColor = myColor,
                        opponent = opponent
                    )
                }
                updateState(state)
            }

            override fun resign() {
                val state = currentState ?: return
                val (blackScore, whiteScore) = ReversiRules.getScore(state.board)
                val newState = state.copy(
                    phase = ReversiGamePhase.GAME_OVER,
                    result = ReversiGameResult(
                        winner = state.myColor.opposite(),
                        reason = ReversiGameResult.Reason.RESIGNATION,
                        blackScore = blackScore,
                        whiteScore = whiteScore
                    )
                )
                updateState(newState)
            }

            override fun leave() {
                val state = currentState ?: return
                val (blackScore, whiteScore) = ReversiRules.getScore(state.board)
                val newState = state.copy(
                    phase = ReversiGamePhase.GAME_OVER,
                    result = ReversiGameResult(
                        winner = state.myColor.opposite(),
                        reason = ReversiGameResult.Reason.OPPONENT_LEFT,
                        blackScore = blackScore,
                        whiteScore = whiteScore
                    )
                )
                updateState(newState)
            }

            fun initGame(color: ReversiColor, opp: User?) {
                startGame(color, opp)
            }
        }
        manager.initGame(myColor, opponent)
        return manager
    }

    private fun createInternetGameManager(): ReversiGameManager {
        // AI level matches tournament tier (1-9), fallback to mid-range
        val aiLevel = (tournament?.id ?: Random.nextInt(3, 8)).coerceIn(1, 9)
        val ai = ReversiAIEngine(level = aiLevel)
        val manager = object : ReversiGameManager(
            onStateChanged = { state -> runOnUiThread { onStateChanged(state) } },
            onError = { error -> runOnUiThread { onError(error) } }
        ) {
            override fun startGame(myColor: ReversiColor, opponent: User?) {
                val state = ReversiGameState.createNew(
                    gameMode = GameMode.INTERNET,
                    myColor = myColor,
                    opponent = opponent
                ).let {
                    if (tournament != null) {
                        it.copy(
                            timerActive = true,
                            blackTimeRemainingMs = ReversiGameState.DEFAULT_INITIAL_TIME_MS,
                            whiteTimeRemainingMs = ReversiGameState.DEFAULT_INITIAL_TIME_MS,
                            isUnlimitedTime = false
                        )
                    } else it
                }
                updateState(state)

                // If bot goes first (player is white), trigger bot move
                if (myColor != ReversiColor.BLACK) {
                    scheduleBotMove(state)
                }
            }

            override fun resign() {
                val state = currentState ?: return
                val (blackScore, whiteScore) = ReversiRules.getScore(state.board)
                val newState = state.copy(
                    phase = ReversiGamePhase.GAME_OVER,
                    result = ReversiGameResult(
                        winner = state.myColor.opposite(),
                        reason = ReversiGameResult.Reason.RESIGNATION,
                        blackScore = blackScore,
                        whiteScore = whiteScore
                    )
                )
                updateState(newState)
            }

            override fun onMoveApplied(move: com.devfigas.reversi.game.engine.ReversiMove, newState: ReversiGameState) {
                if (newState.phase == ReversiGamePhase.PLAYING && newState.currentTurn != newState.myColor) {
                    scheduleBotMove(newState)
                }
            }

            override fun sendChatMessage(message: String) {
                // Simulate chat echo for internet mode
                val state = currentState ?: return
                updateState(state.copy(
                    lastChatMessage = message,
                    lastChatSender = "me"
                ))
            }

            fun scheduleBotMove(state: ReversiGameState) {
                // Human-like timing based on position complexity and AI level
                val delay = ai.calculateThinkingTimeMs(state)
                aiHandler.postDelayed({
                    if (isActivityDestroyed) return@postDelayed
                    val currentState = this.getState() ?: return@postDelayed
                    if (currentState.phase != ReversiGamePhase.PLAYING) return@postDelayed
                    if (currentState.currentTurn == currentState.myColor) return@postDelayed

                    val botMove = ai.selectMove(currentState)
                    if (botMove != null) {
                        applyMove(botMove)
                    }
                }, delay)
            }

            fun initGame(color: ReversiColor, opp: User?) {
                startGame(color, opp)
            }
        }
        manager.initGame(myColor, opponent)
        return manager
    }

    // ==================== STATE UPDATES ====================

    private fun onStateChanged(state: ReversiGameState) {
        if (isActivityDestroyed) return

        Log.d(TAG, "onStateChanged: phase=${state.phase}, turn=${state.currentTurn}, myColor=${state.myColor}")

        // Update board
        boardView.updateFromState(state)

        // Update scores
        updateScoreDisplay(state)

        // Update status text
        updateStatusText(state)

        // Update timers
        updateTimers(state)

        // Handle pass notification
        handlePassNotification(state)

        // Handle chat
        handleIncomingChat(state)

        // Handle game over
        if (state.phase == ReversiGamePhase.GAME_OVER && !hasShownGameOver) {
            hasShownGameOver = true
            showGameOverDialog(state)
        }

        // Handle rematch
        if (state.phase == ReversiGamePhase.WAITING_REMATCH) {
            showWaitingRematchDialog()
        }
    }

    private fun onError(error: String) {
        if (isActivityDestroyed) return
        Log.e(TAG, "Game error: $error")
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    private fun updateScoreDisplay(state: ReversiGameState) {
        tvBlackScore.text = state.getBlackScore().toString()
        tvWhiteScore.text = state.getWhiteScore().toString()
    }

    private fun updateStatusText(state: ReversiGameState) {
        val statusText = when {
            state.phase == ReversiGamePhase.GAME_OVER -> getString(R.string.game_over)
            state.validMoves.isEmpty() && state.phase == ReversiGamePhase.PLAYING -> {
                getString(R.string.no_valid_moves)
            }
            gameMode == GameMode.LOCAL -> {
                if (state.currentTurn == ReversiColor.BLACK) {
                    "${getString(R.string.assign_black)} - ${getString(R.string.your_turn)}"
                } else {
                    "${getString(R.string.assign_white)} - ${getString(R.string.your_turn)}"
                }
            }
            state.isMyTurn() -> getString(R.string.your_turn)
            else -> getString(R.string.opponent_turn)
        }
        tvGameStatus.text = statusText

        // Show disc indicator for the current player's color
        if (state.phase == ReversiGamePhase.PLAYING) {
            statusDiscIndicator.visibility = View.VISIBLE
            val currentColor = if (gameMode == GameMode.LOCAL) {
                state.currentTurn
            } else {
                state.myColor
            }
            statusDiscIndicator.setBackgroundResource(
                if (currentColor == ReversiColor.BLACK) R.drawable.disc_black_small
                else R.drawable.disc_white_small
            )
        } else {
            statusDiscIndicator.visibility = View.GONE
        }
    }

    private fun handlePassNotification(state: ReversiGameState) {
        if (state.consecutivePasses > 0 && state.phase == ReversiGamePhase.PLAYING) {
            val message = if (state.isMyTurn()) {
                getString(R.string.opponent_passed)
            } else {
                getString(R.string.no_valid_moves)
            }
            showPassSnackbar(message)
        }
    }

    private fun showPassSnackbar(message: String) {
        lastPassSnackbar?.dismiss()
        lastPassSnackbar = Snackbar.make(snackbarContainer, message, Snackbar.LENGTH_SHORT)
        lastPassSnackbar?.show()
    }

    // ==================== TIMER ====================

    private fun updateTimers(state: ReversiGameState) {
        if (state.isUnlimitedTime || !state.timerActive) {
            opponentTimerContainer.visibility = View.GONE
            myTimerContainer.visibility = View.GONE
            stopTimer()
            return
        }

        opponentTimerContainer.visibility = View.VISIBLE
        myTimerContainer.visibility = View.VISIBLE

        val myTime = state.myTimeRemainingMs()
        val opponentTime = state.opponentTimeRemainingMs()

        tvMyTimer.text = formatTime(myTime)
        tvOpponentTimer.text = formatTime(opponentTime)

        // Start live timer countdown
        startTimerCountdown(state)
    }

    private fun startTimerCountdown(state: ReversiGameState) {
        stopTimer()

        timerRunnable = object : Runnable {
            override fun run() {
                if (isActivityDestroyed) return
                val currentState = gameManager?.getState() ?: return
                if (currentState.phase != ReversiGamePhase.PLAYING) return
                if (!currentState.timerActive || currentState.isUnlimitedTime) return

                val elapsed = System.currentTimeMillis() - currentState.turnStartTime
                val isMyTurn = currentState.isMyTurn()

                if (isMyTurn) {
                    val remaining = maxOf(0, currentState.myTimeRemainingMs() - elapsed)
                    tvMyTimer.text = formatTime(remaining)
                    tvOpponentTimer.text = formatTime(currentState.opponentTimeRemainingMs())

                    if (remaining <= 0) {
                        handleTimeExpired(currentState)
                        return
                    }
                } else {
                    val remaining = maxOf(0, currentState.opponentTimeRemainingMs() - elapsed)
                    tvOpponentTimer.text = formatTime(remaining)
                    tvMyTimer.text = formatTime(currentState.myTimeRemainingMs())

                    if (remaining <= 0) {
                        handleTimeExpired(currentState)
                        return
                    }
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

    private fun handleTimeExpired(state: ReversiGameState) {
        stopTimer()
        val loser = state.currentTurn
        val winner = loser.opposite()
        val (blackScore, whiteScore) = ReversiRules.getScore(state.board)
        val newState = state.copy(
            phase = ReversiGamePhase.GAME_OVER,
            result = ReversiGameResult(
                winner = winner,
                reason = ReversiGameResult.Reason.TIMEOUT,
                blackScore = blackScore,
                whiteScore = whiteScore
            )
        )
        gameManager?.let { manager ->
            // Use reflection to call updateState since it's protected
            try {
                val method = ReversiGameManager::class.java.getDeclaredMethod("updateState", ReversiGameState::class.java)
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

    // ==================== USER INTERACTION ====================

    private fun selectSquare(gridPosition: GridPosition) {
        val manager = gameManager ?: return
        val state = manager.getState() ?: return

        if (state.phase != ReversiGamePhase.PLAYING) return

        // In non-local modes, only allow moves on player's turn
        if (gameMode != GameMode.LOCAL && !state.isMyTurn()) return

        val position = Position(gridPosition.row, gridPosition.col)
        manager.selectSquare(position)
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

    private fun handleIncomingChat(state: ReversiGameState) {
        val message = state.incomingChatMessage ?: state.lastChatMessage
        if (message == null || message.isEmpty()) return

        showChatBubble(message)
    }

    private fun showChatBubble(message: String) {
        tvChatMessage.text = message
        chatBubble.visibility = View.VISIBLE

        // Auto-dismiss after 4 seconds
        chatDismissRunnable?.let { chatHandler.removeCallbacks(it) }
        chatDismissRunnable = Runnable {
            chatBubble.visibility = View.GONE
        }
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
        if (state.phase != ReversiGamePhase.PLAYING) return

        resignDialog = ResignDialog(
            context = this,
            message = getString(R.string.resign_confirm),
            onContinue = {
                resignDialog = null
            },
            onResign = {
                resignDialog = null
                gameManager?.resign()
            }
        )
        resignDialog?.show()
    }

    private fun showLeaveDialog() {
        if (leaveDialog?.isShowing == true) return

        leaveDialog = LeaveGameDialog(
            context = this,
            message = getString(R.string.leave_confirm),
            onStay = {
                leaveDialog = null
            },
            onLeave = {
                leaveDialog = null
                gameManager?.leave()
                finish()
            }
        )
        leaveDialog?.show()
    }

    private fun showGameOverDialog(state: ReversiGameState) {
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
            ReversiGameResult.Reason.GAME_COMPLETE -> "${result.blackScore} - ${result.whiteScore}"
            ReversiGameResult.Reason.RESIGNATION -> getString(R.string.opponent_resigned)
            ReversiGameResult.Reason.TIMEOUT -> getString(R.string.timeout_loss)
            ReversiGameResult.Reason.OPPONENT_LEFT -> getString(R.string.opponent_left)
            ReversiGameResult.Reason.AGREEMENT -> getString(R.string.draw)
        }

        val pvpResult = PvpGameResult(
            winnerSide = result.winner?.let { if (it == ReversiColor.BLACK) PlayerSide.FIRST else PlayerSide.SECOND },
            reason = when (result.reason) {
                ReversiGameResult.Reason.GAME_COMPLETE -> PvpGameResult.Reason.GAME_RULE
                ReversiGameResult.Reason.RESIGNATION -> PvpGameResult.Reason.RESIGNATION
                ReversiGameResult.Reason.TIMEOUT -> PvpGameResult.Reason.TIMEOUT
                ReversiGameResult.Reason.OPPONENT_LEFT -> PvpGameResult.Reason.OPPONENT_LEFT
                ReversiGameResult.Reason.AGREEMENT -> PvpGameResult.Reason.DRAW
            },
            displayText = resultTitle
        )

        // Handle tournament prize
        val isTournament = tournament != null
        if (isTournament && playerWon) {
            WalletManager.addCoins(this, tournament!!.prize)
            ProgressManager.addVictory(this)
        }

        // Log game end
        val durationSeconds = (System.currentTimeMillis() - gameStartTime) / 1000
        AnalyticsManager.logGameEnded(
            gameMode = gameMode,
            result = when {
                isDraw -> "draw"
                playerWon -> "win"
                else -> "loss"
            },
            durationSeconds = durationSeconds,
            movesCount = state.moveHistory.size,
            endReason = result.reason.name
        )

        val showRematch = gameMode != GameMode.BLUETOOTH && gameMode != GameMode.WIFI
        val useRematchTimeout = gameMode == GameMode.INTERNET

        // Small delay for the board to finish animating
        Handler(Looper.getMainLooper()).postDelayed({
            if (isActivityDestroyed) return@postDelayed

            gameResultDialog = GameResultDialog(
                activity = this,
                player = player,
                opponent = opp,
                result = pvpResult,
                resultTitle = resultTitle,
                reasonText = reasonText,
                playerWon = playerWon,
                tournament = tournament,
                isTournamentMode = isTournament,
                showRematchButton = showRematch,
                useRematchTimeout = useRematchTimeout,
                onRematch = { handleRematch() },
                onExit = { handleExit() }
            )
            gameResultDialog?.show()
        }, 800)
    }

    private fun handleRematch() {
        gameResultDialog?.dismiss()
        gameResultDialog = null
        hasShownGameOver = false

        // Swap colors
        myColor = myColor.opposite()
        boardView.setFlipped(myColor == ReversiColor.WHITE)

        // Deduct fee again for tournament
        if (tournament != null) {
            val balance = WalletManager.getBalance(this)
            if (balance < tournament!!.fee) {
                Toast.makeText(this, R.string.draw, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        // Show versus animation again
        showVersusAnimation {
            startGame()
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
            context = this,
            opponent = opp,
            onCancel = {
                waitingRematchDialog = null
                gameManager?.voteRematch(false)
            },
            onTimeout = {
                waitingRematchDialog = null
                Toast.makeText(this, getString(R.string.opponent_declined_rematch), Toast.LENGTH_SHORT).show()
            }
        )
        waitingRematchDialog?.show()
    }

    // ==================== BACK BUTTON ====================

    private fun onBackButtonPressed() {
        val state = gameManager?.getState()

        when {
            state == null -> finish()
            state.phase == ReversiGamePhase.GAME_OVER -> finish()
            state.phase == ReversiGamePhase.PLAYING -> {
                if (gameMode.isNetworkMode()) {
                    showLeaveDialog()
                } else {
                    showLeaveDialog()
                }
            }
            else -> finish()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        onBackButtonPressed()
    }

    // ==================== BANNER ADS ====================

    private fun setupBannerAd() {
        if (tournament == null) {
            AdManager.showBanner(this, bannerContainer as ViewGroup)
        }
    }

    // ==================== LIFECYCLE ====================

    override fun onResume() {
        super.onResume()
        setupBannerAd()
    }

    override fun onPause() {
        super.onPause()
        stopTimer()
    }

    override fun onDestroy() {
        isActivityDestroyed = true
        super.onDestroy()

        // Clean up
        stopTimer()
        aiHandler.removeCallbacksAndMessages(null)
        chatDismissRunnable?.let { chatHandler.removeCallbacks(it) }

        versusDialog?.dismiss()
        resignDialog?.dismiss()
        leaveDialog?.dismiss()
        gameResultDialog?.dismiss()
        waitingRematchDialog?.dismiss()

        // Release network manager holder
        if (gameMode.isNetworkMode()) {
            PvpGameManagerHolder.setGameActivityActive(false)
        }

        AdManager.hideBanner()
    }
}
