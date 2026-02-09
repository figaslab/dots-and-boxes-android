package com.devfigas.dotsandboxes.game.manager

import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesLine
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesMove
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesRules
import com.devfigas.dotsandboxes.game.engine.PlayerColor
import com.devfigas.dotsandboxes.game.state.DotsAndBoxesGamePhase
import com.devfigas.dotsandboxes.game.state.DotsAndBoxesGameResult
import com.devfigas.dotsandboxes.game.state.DotsAndBoxesGameState
import com.devfigas.mockpvp.model.User

abstract class DotsAndBoxesGameManager(
    onStateChanged: (DotsAndBoxesGameState) -> Unit,
    onError: (String) -> Unit
) {
    protected var currentState: DotsAndBoxesGameState? = null

    protected var stateCallback: (DotsAndBoxesGameState) -> Unit = onStateChanged
    protected var errorCallback: (String) -> Unit = onError

    open fun updateCallbacks(
        onStateChanged: (DotsAndBoxesGameState) -> Unit,
        onError: (String) -> Unit
    ) {
        this.stateCallback = onStateChanged
        this.errorCallback = onError
        currentState?.let { stateCallback(it) }
    }

    protected abstract fun startGame(myColor: PlayerColor, opponent: User?)

    open fun selectLine(line: DotsAndBoxesLine) {
        val state = currentState ?: return
        if (state.phase != DotsAndBoxesGamePhase.PLAYING) return

        // Check if line is valid (not already drawn)
        if (state.board.isLineDrawn(line)) return

        val move = DotsAndBoxesMove(line, state.currentTurn)
        applyMove(move)
    }

    protected open fun applyMove(move: DotsAndBoxesMove) {
        val state = currentState ?: return

        val result = DotsAndBoxesRules.applyMove(state.board, move)

        // If extra turn (completed a box), keep current turn; otherwise switch
        val nextTurn = if (result.extraTurn) state.currentTurn else state.currentTurn.opposite()

        // Calculate new time remaining
        val now = System.currentTimeMillis()
        val elapsed = if (state.timerActive) now - state.turnStartTime else 0L

        val newRedTime: Long
        val newBlueTime: Long
        if (state.currentTurn == PlayerColor.RED) {
            newRedTime = maxOf(0, state.redTimeRemainingMs - elapsed + state.incrementMs)
            newBlueTime = state.blueTimeRemainingMs
        } else {
            newRedTime = state.redTimeRemainingMs
            newBlueTime = maxOf(0, state.blueTimeRemainingMs - elapsed + state.incrementMs)
        }

        val gameOver = DotsAndBoxesRules.isGameOver(result.board)

        var newState = state.copy(
            board = result.board,
            currentTurn = nextTurn,
            moveHistory = state.moveHistory + move,
            turnStartTime = now,
            redTimeRemainingMs = newRedTime,
            blueTimeRemainingMs = newBlueTime
        )

        if (gameOver) {
            val winner = DotsAndBoxesRules.getWinner(result.board)
            val (redScore, blueScore) = DotsAndBoxesRules.getScore(result.board)
            newState = newState.copy(
                phase = DotsAndBoxesGamePhase.GAME_OVER,
                result = DotsAndBoxesGameResult(
                    winner = winner,
                    reason = DotsAndBoxesGameResult.Reason.GAME_COMPLETE,
                    redScore = redScore,
                    blueScore = blueScore
                )
            )
        }

        updateState(newState)
        onMoveApplied(move, newState, result.extraTurn)
    }

    protected open fun onMoveApplied(move: DotsAndBoxesMove, newState: DotsAndBoxesGameState, extraTurn: Boolean) {
        // Override in subclasses
    }

    abstract fun resign()

    open fun leave() {
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
            restartGame()
        }
    }

    protected open fun restartGame() {
        val state = currentState ?: return
        val newColor = state.myColor.opposite()
        startGame(newColor, state.opponent)
    }

    open fun sendChatMessage(message: String) {
        // No-op in base class
    }

    open fun resetGame() {
        currentState = null
    }

    protected fun updateState(newState: DotsAndBoxesGameState) {
        currentState = newState
        stateCallback(newState)
    }

    protected fun notifyError(error: String) {
        errorCallback(error)
    }

    fun getState(): DotsAndBoxesGameState? = currentState
}
