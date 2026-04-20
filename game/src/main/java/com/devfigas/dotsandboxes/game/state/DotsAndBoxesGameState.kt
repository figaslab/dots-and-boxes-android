package com.devfigas.dotsandboxes.game.state

import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesBoard
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesMove
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesRules
import com.devfigas.dotsandboxes.game.engine.PlayerColor
import com.devfigas.mockpvp.model.GameMode
import com.devfigas.mockpvp.model.User
import java.util.UUID

enum class SyncStatus {
    SYNCED,
    WAITING_CONFIRMATION,
    SYNCING
}

data class DotsAndBoxesGameState(
    val gameId: String,
    val board: DotsAndBoxesBoard,
    val currentTurn: PlayerColor,
    val phase: DotsAndBoxesGamePhase,
    val gameMode: GameMode,
    val myColor: PlayerColor,
    val opponent: User?,
    val moveHistory: List<DotsAndBoxesMove> = emptyList(),
    val result: DotsAndBoxesGameResult? = null,
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
    val redTimeRemainingMs: Long = DEFAULT_INITIAL_TIME_MS,
    val blueTimeRemainingMs: Long = DEFAULT_INITIAL_TIME_MS,
    val incrementMs: Long = DEFAULT_INCREMENT_MS,
    val isUnlimitedTime: Boolean = false,
    val timerActive: Boolean = false
) {

    fun isMyTurn(): Boolean = currentTurn == myColor

    fun myTimeRemainingMs(): Long =
        if (myColor == PlayerColor.RED) redTimeRemainingMs else blueTimeRemainingMs

    fun opponentTimeRemainingMs(): Long =
        if (myColor == PlayerColor.RED) blueTimeRemainingMs else redTimeRemainingMs

    fun currentPlayerTimeRemainingMs(): Long =
        if (currentTurn == PlayerColor.RED) redTimeRemainingMs else blueTimeRemainingMs

    fun isTimeExpired(): Boolean {
        if (isUnlimitedTime) return false
        return currentPlayerTimeRemainingMs() <= 0
    }

    fun remainingTurnTime(): Long {
        if (!timerActive) return turnTimeoutMs
        val elapsed = System.currentTimeMillis() - turnStartTime
        return maxOf(0, turnTimeoutMs - elapsed)
    }

    fun isTurnExpired(): Boolean = timerActive && remainingTurnTime() <= 0

    fun getRedScore(): Int = board.countBoxes(PlayerColor.RED)

    fun getBlueScore(): Int = board.countBoxes(PlayerColor.BLUE)

    fun getMyScore(): Int =
        if (myColor == PlayerColor.RED) getRedScore() else getBlueScore()

    fun getOpponentScore(): Int =
        if (myColor == PlayerColor.RED) getBlueScore() else getRedScore()

    companion object {
        const val TURN_TIMEOUT_MS = 15_000L
        const val DEFAULT_INITIAL_TIME_MS = 10 * 60 * 1000L
        const val DEFAULT_INCREMENT_MS = 0L

        fun createNew(
            gameMode: GameMode,
            myColor: PlayerColor,
            opponent: User?
        ): DotsAndBoxesGameState {
            val board = DotsAndBoxesBoard.createEmpty()
            return DotsAndBoxesGameState(
                gameId = UUID.randomUUID().toString(),
                board = board,
                currentTurn = PlayerColor.RED,
                phase = DotsAndBoxesGamePhase.PLAYING,
                gameMode = gameMode,
                myColor = myColor,
                opponent = opponent
            )
        }

        fun createForChallenge(
            gameId: String,
            gameMode: GameMode,
            myColor: PlayerColor,
            opponent: User?
        ): DotsAndBoxesGameState {
            val board = DotsAndBoxesBoard.createEmpty()
            return DotsAndBoxesGameState(
                gameId = gameId,
                board = board,
                currentTurn = PlayerColor.RED,
                phase = DotsAndBoxesGamePhase.PLAYING,
                gameMode = gameMode,
                myColor = myColor,
                opponent = opponent
            )
        }
    }
}
