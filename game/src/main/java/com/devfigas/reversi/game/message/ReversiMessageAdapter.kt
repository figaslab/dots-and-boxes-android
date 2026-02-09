package com.devfigas.reversi.game.message

import com.devfigas.reversi.game.engine.ReversiColor

object ReversiMessageAdapter {
    // Encode move as "row,col" or "PASS"
    fun encodeMoveData(row: Int, col: Int): String = "$row,$col"

    fun encodePass(): String = "PASS"

    // Returns Pair(row, col) or null for PASS
    fun decodeMoveData(moveData: String): Pair<Int, Int>? {
        if (moveData == "PASS") return null
        val parts = moveData.split(",")
        return Pair(parts[0].toInt(), parts[1].toInt())
    }

    fun ReversiColor.toSideName(): String = name

    fun String.toReversiColor(): ReversiColor = ReversiColor.valueOf(this)
}
