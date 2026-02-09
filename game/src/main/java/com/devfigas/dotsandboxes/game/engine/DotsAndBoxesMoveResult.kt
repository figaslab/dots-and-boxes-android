package com.devfigas.dotsandboxes.game.engine

data class DotsAndBoxesMoveResult(
    val board: DotsAndBoxesBoard,
    val move: DotsAndBoxesMove,
    val boxesCompleted: Int,
    val extraTurn: Boolean
)
