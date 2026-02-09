package com.devfigas.dotsandboxes.game.state

import com.devfigas.dotsandboxes.game.engine.PlayerColor

data class DotsAndBoxesGameResult(
    val winner: PlayerColor?,
    val reason: Reason,
    val redScore: Int,
    val blueScore: Int
) {
    enum class Reason {
        GAME_COMPLETE,
        RESIGNATION,
        TIMEOUT,
        OPPONENT_LEFT,
        AGREEMENT
    }
}
