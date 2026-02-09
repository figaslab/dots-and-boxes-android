package com.devfigas.reversi.game.state

import com.devfigas.mockpvp.model.GameMode
import com.devfigas.mockpvp.model.User
import com.devfigas.reversi.game.engine.Position
import com.devfigas.reversi.game.engine.ReversiBoard
import com.devfigas.reversi.game.engine.ReversiColor
import com.devfigas.reversi.game.engine.ReversiMove
import com.devfigas.reversi.game.engine.ReversiRules
import java.util.UUID

enum class SyncStatus {
    SYNCED,
    WAITING_CONFIRMATION,
    SYNCING
}

data class ReversiGameState(
    val gameId: String,
    val board: ReversiBoard,
    val currentTurn: ReversiColor,
    val phase: ReversiGamePhase,
    val gameMode: GameMode,
    val myColor: ReversiColor,
    val opponent: User?,
    val moveHistory: List<ReversiMove> = emptyList(),
    val consecutivePasses: Int = 0,
    val validMoves: List<Position> = emptyList(),
    val result: ReversiGameResult? = null,
    val myRematchVote: Boolean? = null,
    val opponentRematchVote: Boolean? = null,
    val lastChatMessage: String? = null,
    val lastChatSender: String? = null,
    val incomingChatMessage: String? = null,
    // Sync fields
    val isHost: Boolean = false,
    val moveNum: Int = 0,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    // Timer fields
    val turnStartTime: Long = System.currentTimeMillis(),
    val turnTimeoutMs: Long = TURN_TIMEOUT_MS,
    val blackTimeRemainingMs: Long = DEFAULT_INITIAL_TIME_MS,
    val whiteTimeRemainingMs: Long = DEFAULT_INITIAL_TIME_MS,
    val incrementMs: Long = DEFAULT_INCREMENT_MS,
    val isUnlimitedTime: Boolean = false,
    val timerActive: Boolean = false
) {

    fun isMyTurn(): Boolean = currentTurn == myColor

    fun myTimeRemainingMs(): Long =
        if (myColor == ReversiColor.BLACK) blackTimeRemainingMs else whiteTimeRemainingMs

    fun opponentTimeRemainingMs(): Long =
        if (myColor == ReversiColor.BLACK) whiteTimeRemainingMs else blackTimeRemainingMs

    fun currentPlayerTimeRemainingMs(): Long =
        if (currentTurn == ReversiColor.BLACK) blackTimeRemainingMs else whiteTimeRemainingMs

    fun isTimeExpired(): Boolean {
        if (isUnlimitedTime) return false
        return currentPlayerTimeRemainingMs() <= 0
    }

    fun getBlackScore(): Int = board.countPieces(ReversiColor.BLACK)

    fun getWhiteScore(): Int = board.countPieces(ReversiColor.WHITE)

    fun getMyScore(): Int =
        if (myColor == ReversiColor.BLACK) getBlackScore() else getWhiteScore()

    fun getOpponentScore(): Int =
        if (myColor == ReversiColor.BLACK) getWhiteScore() else getBlackScore()

    companion object {
        const val TURN_TIMEOUT_MS = 30_000L
        const val DEFAULT_INITIAL_TIME_MS = 10 * 60 * 1000L
        const val DEFAULT_INCREMENT_MS = 0L

        fun createNew(
            gameMode: GameMode,
            myColor: ReversiColor,
            opponent: User?
        ): ReversiGameState {
            val board = ReversiBoard.createInitial()
            val validMoves = ReversiRules.getValidMoves(board, ReversiColor.BLACK)
                .map { it.position }

            return ReversiGameState(
                gameId = UUID.randomUUID().toString(),
                board = board,
                currentTurn = ReversiColor.BLACK,
                phase = ReversiGamePhase.PLAYING,
                gameMode = gameMode,
                myColor = myColor,
                opponent = opponent,
                validMoves = validMoves
            )
        }

        fun createForChallenge(
            gameId: String,
            gameMode: GameMode,
            myColor: ReversiColor,
            opponent: User?
        ): ReversiGameState {
            val board = ReversiBoard.createInitial()
            val validMoves = ReversiRules.getValidMoves(board, ReversiColor.BLACK)
                .map { it.position }

            return ReversiGameState(
                gameId = gameId,
                board = board,
                currentTurn = ReversiColor.BLACK,
                phase = ReversiGamePhase.PLAYING,
                gameMode = gameMode,
                myColor = myColor,
                opponent = opponent,
                validMoves = validMoves
            )
        }
    }
}
