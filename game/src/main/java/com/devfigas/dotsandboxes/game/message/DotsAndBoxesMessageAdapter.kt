package com.devfigas.dotsandboxes.game.message

import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesLine
import com.devfigas.dotsandboxes.game.engine.PlayerColor

object DotsAndBoxesMessageAdapter {

    fun encodeMoveData(line: DotsAndBoxesLine): String = line.toNotation()

    fun decodeMoveData(moveData: String): DotsAndBoxesLine? = DotsAndBoxesLine.fromNotation(moveData)

    fun PlayerColor.toSideName(): String = name

    fun String.toPlayerColor(): PlayerColor = PlayerColor.valueOf(this)
}
