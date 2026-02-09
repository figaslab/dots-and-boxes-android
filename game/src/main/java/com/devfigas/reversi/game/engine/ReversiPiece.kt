package com.devfigas.reversi.game.engine

import com.devfigas.gridgame.model.GridPiece
import com.devfigas.gridgame.model.PlayerSide

data class ReversiPiece(val color: ReversiColor) : GridPiece {
    override val owner: PlayerSide
        get() = if (color == ReversiColor.BLACK) PlayerSide.FIRST else PlayerSide.SECOND
    override val value: Int
        get() = 1
}
