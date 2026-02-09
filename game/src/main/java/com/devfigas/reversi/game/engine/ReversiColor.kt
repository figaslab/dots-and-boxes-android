package com.devfigas.reversi.game.engine

enum class ReversiColor {
    BLACK,
    WHITE;

    fun opposite(): ReversiColor = if (this == BLACK) WHITE else BLACK
}
