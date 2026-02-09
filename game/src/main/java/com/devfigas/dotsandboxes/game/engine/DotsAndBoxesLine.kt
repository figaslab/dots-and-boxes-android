package com.devfigas.dotsandboxes.game.engine

enum class LineOrientation {
    HORIZONTAL,
    VERTICAL
}

data class DotsAndBoxesLine(
    val orientation: LineOrientation,
    val row: Int,
    val col: Int
) {
    fun toNotation(): String {
        val prefix = if (orientation == LineOrientation.HORIZONTAL) "H" else "V"
        return "$prefix:$row:$col"
    }

    companion object {
        fun fromNotation(notation: String): DotsAndBoxesLine? {
            val parts = notation.split(":")
            if (parts.size != 3) return null
            val orientation = when (parts[0]) {
                "H" -> LineOrientation.HORIZONTAL
                "V" -> LineOrientation.VERTICAL
                else -> return null
            }
            val row = parts[1].toIntOrNull() ?: return null
            val col = parts[2].toIntOrNull() ?: return null
            return DotsAndBoxesLine(orientation, row, col)
        }
    }
}
