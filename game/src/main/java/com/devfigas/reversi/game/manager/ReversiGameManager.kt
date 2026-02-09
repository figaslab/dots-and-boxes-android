package com.devfigas.reversi.game.manager

import com.devfigas.reversi.game.engine.Position
import com.devfigas.reversi.game.engine.ReversiColor
import com.devfigas.reversi.game.engine.ReversiMove
import com.devfigas.reversi.game.engine.ReversiRules
import com.devfigas.reversi.game.state.ReversiGamePhase
import com.devfigas.reversi.game.state.ReversiGameResult
import com.devfigas.reversi.game.state.ReversiGameState
import com.devfigas.mockpvp.model.User

abstract class ReversiGameManager(
    onStateChanged: (ReversiGameState) -> Unit,
    onError: (String) -> Unit
) {
    protected var currentState: ReversiGameState? = null

    // Mutable callbacks to allow Activity transitions to update them
    protected var stateCallback: (ReversiGameState) -> Unit = onStateChanged
    protected var errorCallback: (String) -> Unit = onError

    /**
     * Update callbacks when transitioning between Activity/Fragment.
     * This allows the same GameManager instance to be reused.
     */
    open fun updateCallbacks(
        onStateChanged: (ReversiGameState) -> Unit,
        onError: (String) -> Unit
    ) {
        this.stateCallback = onStateChanged
        this.errorCallback = onError
        // Re-emit current state to new callback
        currentState?.let { stateCallback(it) }
    }

    protected abstract fun startGame(myColor: ReversiColor, opponent: User?)

    /**
     * Select a square on the board. In Reversi, clicking an empty square that
     * is a valid move immediately places the disc (one-click interaction).
     * If the clicked position is NOT a valid move, nothing happens.
     */
    open fun selectSquare(position: Position) {
        val state = currentState ?: return
        if (state.phase != ReversiGamePhase.PLAYING) return

        // Check if this position is a valid move
        if (position !in state.validMoves) return

        // Find the full ReversiMove for this position
        val validMoves = ReversiRules.getValidMoves(state.board, state.currentTurn)
        val move = validMoves.find { it.position == position } ?: return

        applyMove(move)
    }

    protected open fun applyMove(move: ReversiMove) {
        val state = currentState ?: return

        // Apply the move: place piece and flip discs
        val newBoard = ReversiRules.applyMove(state.board, move)

        // Switch turn to opposite color
        var nextTurn = state.currentTurn.opposite()
        var consecutivePasses = 0 // Reset consecutive passes since a move was made

        // Calculate new time remaining for the player who just moved
        val now = System.currentTimeMillis()
        val elapsed = if (state.timerActive) now - state.turnStartTime else 0L

        val newWhiteTime: Long
        val newBlackTime: Long
        if (state.currentTurn == ReversiColor.WHITE) {
            newWhiteTime = maxOf(0, state.whiteTimeRemainingMs - elapsed + state.incrementMs)
            newBlackTime = state.blackTimeRemainingMs
        } else {
            newWhiteTime = state.whiteTimeRemainingMs
            newBlackTime = maxOf(0, state.blackTimeRemainingMs - elapsed + state.incrementMs)
        }

        // Calculate valid moves for the next player
        var nextValidMoves = ReversiRules.getValidMoves(newBoard, nextTurn)

        // If next player has no valid moves, auto-pass
        if (nextValidMoves.isEmpty()) {
            consecutivePasses++
            nextTurn = nextTurn.opposite()
            nextValidMoves = ReversiRules.getValidMoves(newBoard, nextTurn)

            // If the original player also has no valid moves, game is over
            if (nextValidMoves.isEmpty()) {
                consecutivePasses++
            }
        }

        // Check for game over: 2 consecutive passes or board is full
        val gameOver = consecutivePasses >= 2 || newBoard.isFull()

        var newState = state.copy(
            board = newBoard,
            currentTurn = nextTurn,
            moveHistory = state.moveHistory + move,
            consecutivePasses = consecutivePasses,
            validMoves = if (gameOver) emptyList() else nextValidMoves.map { it.position },
            turnStartTime = now,
            whiteTimeRemainingMs = newWhiteTime,
            blackTimeRemainingMs = newBlackTime
        )

        if (gameOver) {
            val winner = ReversiRules.getWinner(newBoard)
            val (blackScore, whiteScore) = ReversiRules.getScore(newBoard)
            newState = newState.copy(
                phase = ReversiGamePhase.GAME_OVER,
                result = ReversiGameResult(
                    winner = winner,
                    reason = ReversiGameResult.Reason.GAME_COMPLETE,
                    blackScore = blackScore,
                    whiteScore = whiteScore
                )
            )
        }

        updateState(newState)
        onMoveApplied(move, newState)
    }

    /**
     * Hook for subclasses to add behavior after a move is applied
     * (e.g., trigger AI move, send network message).
     */
    protected open fun onMoveApplied(move: ReversiMove, newState: ReversiGameState) {
        // Override in subclasses
    }

    abstract fun resign()

    open fun leave() {
        // Default implementation: just resign
        // NetworkGameManager will override to send LEAVE instead of RESIGN
        resign()
    }

    open fun voteRematch(vote: Boolean) {
        val state = currentState ?: return
        updateState(state.copy(myRematchVote = vote))
        checkRematchVotes()
    }

    protected open fun checkRematchVotes() {
        val state = currentState ?: return
        if (state.myRematchVote == true && state.opponentRematchVote == true) {
            // Both voted yes - restart game with swapped colors
            restartGame()
        } else if (state.myRematchVote == false || state.opponentRematchVote == false) {
            // Someone voted no - end
        }
    }

    protected open fun restartGame() {
        val state = currentState ?: return
        val newColor = state.myColor.opposite()
        startGame(newColor, state.opponent)
    }

    open fun sendChatMessage(message: String) {
        // No-op in base class - override in NetworkGameManager
    }

    open fun resetGame() {
        currentState = null
    }

    protected fun updateState(newState: ReversiGameState) {
        android.util.Log.e("ReversiGameManager", "updateState: phase=${newState.phase}, myColor=${newState.myColor}, currentTurn=${newState.currentTurn}")
        currentState = newState
        stateCallback(newState)
    }

    protected fun notifyError(error: String) {
        errorCallback(error)
    }

    fun getState(): ReversiGameState? = currentState
}
