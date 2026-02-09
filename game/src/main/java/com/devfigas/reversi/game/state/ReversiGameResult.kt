package com.devfigas.reversi.game.state

import com.devfigas.reversi.game.engine.ReversiColor

data class ReversiGameResult(
    val winner: ReversiColor?,
    val reason: Reason,
    val blackScore: Int,
    val whiteScore: Int
) {
    enum class Reason {
        GAME_COMPLETE,
        RESIGNATION,
        TIMEOUT,
        OPPONENT_LEFT,
        AGREEMENT
    }
}
