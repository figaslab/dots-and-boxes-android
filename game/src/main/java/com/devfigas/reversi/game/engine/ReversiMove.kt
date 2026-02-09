package com.devfigas.reversi.game.engine

import com.devfigas.gridgame.model.GridMove
import com.devfigas.gridgame.model.GridPiece
import com.devfigas.gridgame.model.GridPosition

data class ReversiMove(
    val position: Position,
    val color: ReversiColor,
    val flippedPositions: List<Position>
) : GridMove {
    override val from: GridPosition
        get() = GridPosition(position.row, position.col)
    override val to: GridPosition
        get() = GridPosition(position.row, position.col)
    override val piece: GridPiece
        get() = ReversiPiece(color)
    override val capturedPiece: GridPiece?
        get() = null
}
