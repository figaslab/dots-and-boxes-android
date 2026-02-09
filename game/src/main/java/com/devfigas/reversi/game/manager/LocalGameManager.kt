package com.devfigas.reversi.game.manager

import android.os.Handler
import android.os.Looper
import com.devfigas.reversi.game.ai.ReversiAI
import com.devfigas.reversi.game.ai.ReversiAIEngine
import com.devfigas.reversi.game.engine.Position
import com.devfigas.reversi.game.engine.ReversiColor
import com.devfigas.reversi.game.engine.ReversiMove
import com.devfigas.reversi.game.state.ReversiGamePhase
import com.devfigas.reversi.game.state.ReversiGameResult
import com.devfigas.reversi.game.state.ReversiGameState
import com.devfigas.mockpvp.model.GameMode
import com.devfigas.mockpvp.model.User
import kotlin.random.Random

class LocalGameManager(
    onStateChanged: (ReversiGameState) -> Unit,
    onError: (String) -> Unit,
    private val gameMode: GameMode
) : ReversiGameManager(onStateChanged, onError) {

    private val usesAI: Boolean = gameMode == GameMode.CPU || gameMode == GameMode.INTERNET
    private val ai: ReversiAI? = if (usesAI) ReversiAIEngine(level = AI_LEVEL) else null
    private val handler = Handler(Looper.getMainLooper())
    private var cpuColor: ReversiColor = ReversiColor.WHITE

    companion object {
        private const val AI_LEVEL = 5

        // CPU mode: fixed short delay (player knows it's a machine)
        private const val CPU_FIXED_DELAY = 500L

        // Bot (Internet) mode: variable delays to simulate human behavior
        private const val MIN_DELAY_OPENING = 400L      // Fast moves in opening
        private const val MAX_DELAY_OPENING = 1200L
        private const val MIN_DELAY_MIDGAME = 800L      // Slower in midgame (more thinking)
        private const val MAX_DELAY_MIDGAME = 2500L
        private const val MIN_DELAY_ENDGAME = 600L      // Medium speed in endgame
        private const val MAX_DELAY_ENDGAME = 1800L
        private const val OPENING_DISCS_THRESHOLD = 14   // Up to 14 discs on board = opening (4 initial + ~10 moves)
        private const val ENDGAME_DISCS_THRESHOLD = 50   // 50+ discs = endgame

        // Rematch response simulation for INTERNET mode
        private const val MIN_REMATCH_RESPONSE_DELAY = 1000L  // 1 second minimum
        private const val MAX_REMATCH_RESPONSE_DELAY = 3000L  // 3 seconds maximum
        private const val REMATCH_DECLINE_CHANCE = 0.10       // 10% chance to decline
    }

    override fun startGame(myColor: ReversiColor, opponent: User?) {
        android.util.Log.e("LocalGameManager", "startGame: myColor=$myColor, cpuColor before=$cpuColor")
        cpuColor = myColor.opposite()
        android.util.Log.e("LocalGameManager", "startGame: cpuColor after=$cpuColor")

        val state = ReversiGameState.createNew(gameMode, myColor, opponent).copy(
            isUnlimitedTime = gameMode != GameMode.INTERNET,
            timerActive = false  // Timer starts inactive - will be activated after animation
        )

        android.util.Log.e("LocalGameManager", "startGame: state.myColor=${state.myColor}, state.currentTurn=${state.currentTurn}, phase=${state.phase}")
        updateState(state)
        android.util.Log.e("LocalGameManager", "startGame: after updateState, currentState?.myColor=${currentState?.myColor}")

        // If AI plays BLACK (first), make first move
        if (usesAI && cpuColor == ReversiColor.BLACK) {
            android.util.Log.e("LocalGameManager", "startGame: AI plays black (first), scheduling CPU move")
            scheduleCPUMove()
        } else {
            android.util.Log.e("LocalGameManager", "startGame: Player plays first (myColor=$myColor, cpuColor=$cpuColor, usesAI=$usesAI)")
        }
    }

    override fun selectSquare(position: Position) {
        val state = currentState ?: return
        if (state.phase != ReversiGamePhase.PLAYING) return

        // In AI modes, only allow selection when it's player's turn
        if (usesAI && state.currentTurn == cpuColor) {
            return
        }

        super.selectSquare(position)
    }

    override fun onMoveApplied(move: ReversiMove, newState: ReversiGameState) {
        // In AI modes, trigger AI move if it's AI's turn and game is still playing
        if (usesAI &&
            newState.phase == ReversiGamePhase.PLAYING &&
            newState.currentTurn == cpuColor) {
            scheduleCPUMove()
        }
    }

    private fun scheduleCPUMove() {
        val delay = if (gameMode == GameMode.INTERNET) {
            // Bot mode: variable delay to simulate human behavior
            calculateHumanLikeDelay()
        } else {
            // CPU mode: fixed short delay (player knows it's a machine)
            CPU_FIXED_DELAY
        }
        handler.postDelayed({
            makeCPUMove()
        }, delay)
    }

    /**
     * Calculates a human-like delay for Internet (bot) mode based on game phase.
     * Uses disc count on the board to determine phase:
     * - Opening: Few discs - fast moves
     * - Midgame: Medium disc count - slower, more thinking
     * - Endgame: Many discs - medium speed
     */
    private fun calculateHumanLikeDelay(): Long {
        val state = currentState ?: return Random.nextLong(MIN_DELAY_MIDGAME, MAX_DELAY_MIDGAME)

        val discCount = state.board.countTotal()

        return when {
            // Opening phase: up to ~14 discs on board - fast, confident moves
            discCount <= OPENING_DISCS_THRESHOLD -> {
                Random.nextLong(MIN_DELAY_OPENING, MAX_DELAY_OPENING)
            }
            // Endgame: 50+ discs - medium thinking time
            discCount >= ENDGAME_DISCS_THRESHOLD -> {
                Random.nextLong(MIN_DELAY_ENDGAME, MAX_DELAY_ENDGAME)
            }
            // Midgame: complex positions - more thinking required
            else -> {
                Random.nextLong(MIN_DELAY_MIDGAME, MAX_DELAY_MIDGAME)
            }
        }
    }

    private fun makeCPUMove() {
        val state = currentState ?: return
        if (state.phase != ReversiGamePhase.PLAYING) return
        if (state.currentTurn != cpuColor) return

        val move = ai?.selectMove(state)
        if (move != null) {
            applyMove(move)
        } else {
            // AI returned null means no valid moves - pass is auto-handled
            // by the base manager's applyMove logic. This should not normally
            // happen because the base manager already handles auto-pass.
            android.util.Log.w("LocalGameManager", "AI returned null move - no valid moves available")
        }
    }

    override fun resign() {
        val state = currentState ?: return
        val winner = if (usesAI) cpuColor else state.currentTurn.opposite()
        val (blackScore, whiteScore) = com.devfigas.reversi.game.engine.ReversiRules.getScore(state.board)
        updateState(state.copy(
            phase = ReversiGamePhase.GAME_OVER,
            result = ReversiGameResult(winner, ReversiGameResult.Reason.RESIGNATION, blackScore, whiteScore)
        ))
    }

    override fun voteRematch(vote: Boolean) {
        val state = currentState ?: return
        android.util.Log.e("LocalGameManager", "voteRematch CALLED: vote=$vote, currentState.myColor=${state.myColor}, gameMode=$gameMode")

        if (gameMode == GameMode.LOCAL) {
            // In local mode, just restart immediately if vote is yes
            if (vote) {
                android.util.Log.e("LocalGameManager", "voteRematch: LOCAL mode, calling restartGame")
                restartGame()
            }
        } else if (gameMode == GameMode.INTERNET) {
            // INTERNET (Tournament) mode - simulate human-like response with delay
            android.util.Log.e("LocalGameManager", "voteRematch: INTERNET mode, simulating opponent response")

            // Update state to show player voted and transition to waiting phase
            updateState(state.copy(
                myRematchVote = vote,
                phase = if (vote) ReversiGamePhase.WAITING_REMATCH else ReversiGamePhase.GAME_OVER
            ))

            if (vote) {
                // Simulate opponent response with random delay (1-3 seconds)
                val responseDelay = Random.nextLong(MIN_REMATCH_RESPONSE_DELAY, MAX_REMATCH_RESPONSE_DELAY)
                android.util.Log.e("LocalGameManager", "voteRematch: scheduling opponent response in ${responseDelay}ms")

                handler.postDelayed({
                    val currentState = this.currentState ?: return@postDelayed

                    // Small chance opponent declines (10%)
                    val opponentAccepts = Random.nextDouble() >= REMATCH_DECLINE_CHANCE
                    android.util.Log.e("LocalGameManager", "voteRematch: opponent decision - accepts=$opponentAccepts")

                    if (opponentAccepts) {
                        updateState(currentState.copy(
                            opponentRematchVote = true
                        ))
                        restartGame()
                    } else {
                        // Opponent declined - update state to show declined
                        updateState(currentState.copy(
                            opponentRematchVote = false,
                            phase = ReversiGamePhase.GAME_OVER
                        ))
                    }
                }, responseDelay)
            }
        } else {
            // CPU mode - AI always agrees to rematch immediately
            android.util.Log.e("LocalGameManager", "voteRematch: CPU mode, updating state and calling restartGame")
            updateState(state.copy(
                myRematchVote = vote,
                opponentRematchVote = vote
            ))
            if (vote) {
                restartGame()
            }
        }
    }

    override fun restartGame() {
        val state = currentState ?: return
        val oldColor = state.myColor
        val newColor = state.myColor.opposite()
        android.util.Log.e("LocalGameManager", "restartGame: oldColor=$oldColor, newColor=$newColor")
        if (usesAI) {
            cpuColor = newColor.opposite()
            android.util.Log.e("LocalGameManager", "restartGame: usesAI=true, new cpuColor=$cpuColor")
        }
        android.util.Log.e("LocalGameManager", "restartGame: calling startGame with newColor=$newColor")
        startGame(newColor, state.opponent)
    }

    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Check if any player's time has expired and handle it.
     * Called periodically from the UI to check for timeout.
     */
    fun checkAndHandleTimeout() {
        val state = currentState ?: return
        if (state.phase != ReversiGamePhase.PLAYING) return
        if (state.isUnlimitedTime) return  // No timeout in unlimited mode
        if (!state.timerActive) return  // Timer not running yet

        // Check if current player's time is expired
        if (state.isTimeExpired()) {
            val winner = state.currentTurn.opposite()
            val (blackScore, whiteScore) = com.devfigas.reversi.game.engine.ReversiRules.getScore(state.board)
            android.util.Log.d("LocalGameManager", "Time expired for ${state.currentTurn} - $winner wins")

            updateState(state.copy(
                phase = ReversiGamePhase.GAME_OVER,
                result = ReversiGameResult(winner, ReversiGameResult.Reason.TIMEOUT, blackScore, whiteScore)
            ))
        }
    }

    /**
     * Activate the timer. Called after game start animation completes.
     * This ensures the timer doesn't count while the player can't interact.
     */
    fun activateTimer() {
        val state = currentState ?: return
        if (state.timerActive) return  // Already active

        android.util.Log.d("LocalGameManager", "Activating timer")
        updateState(state.copy(
            timerActive = true,
            turnStartTime = System.currentTimeMillis()  // Reset start time to now
        ))
    }
}
