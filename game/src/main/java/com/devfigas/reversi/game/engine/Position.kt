package com.devfigas.reversi.game.engine

data class Position(val row: Int, val col: Int) {
    fun isValid(): Boolean = row in 0..7 && col in 0..7

    fun toNotation(): String = "${('a' + col)}${8 - row}"

    companion object {
        fun fromNotation(notation: String): Position? {
            if (notation.length != 2) return null
            val col = notation[0] - 'a'
            val row = 8 - (notation[1] - '0')
            return if (row in 0..7 && col in 0..7) Position(row, col) else null
        }
    }
}
