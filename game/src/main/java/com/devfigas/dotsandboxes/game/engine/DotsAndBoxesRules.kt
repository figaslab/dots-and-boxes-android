package com.devfigas.dotsandboxes.game.engine

object DotsAndBoxesRules {

    fun getValidMoves(board: DotsAndBoxesBoard, player: PlayerColor): List<DotsAndBoxesMove> {
        val moves = mutableListOf<DotsAndBoxesMove>()

        // Horizontal lines
        for (row in 0 until board.dotRows) {
            for (col in 0 until board.dotCols - 1) {
                if (!board.isHorizontalLineDrawn(row, col)) {
                    moves.add(DotsAndBoxesMove(
                        DotsAndBoxesLine(LineOrientation.HORIZONTAL, row, col),
                        player
                    ))
                }
            }
        }

        // Vertical lines
        for (row in 0 until board.dotRows - 1) {
            for (col in 0 until board.dotCols) {
                if (!board.isVerticalLineDrawn(row, col)) {
                    moves.add(DotsAndBoxesMove(
                        DotsAndBoxesLine(LineOrientation.VERTICAL, row, col),
                        player
                    ))
                }
            }
        }

        return moves
    }

    fun applyMove(board: DotsAndBoxesBoard, move: DotsAndBoxesMove): DotsAndBoxesMoveResult {
        val oldRedScore = board.countBoxes(PlayerColor.RED)
        val oldBlueScore = board.countBoxes(PlayerColor.BLUE)

        val newBoard = board.drawLine(move.line, move.player)

        val newRedScore = newBoard.countBoxes(PlayerColor.RED)
        val newBlueScore = newBoard.countBoxes(PlayerColor.BLUE)

        val boxesCompleted = (newRedScore + newBlueScore) - (oldRedScore + oldBlueScore)
        val extraTurn = boxesCompleted > 0

        return DotsAndBoxesMoveResult(
            board = newBoard,
            move = move,
            boxesCompleted = boxesCompleted,
            extraTurn = extraTurn
        )
    }

    fun isGameOver(board: DotsAndBoxesBoard): Boolean = board.allLinesDrawn()

    fun getScore(board: DotsAndBoxesBoard): Pair<Int, Int> {
        val red = board.countBoxes(PlayerColor.RED)
        val blue = board.countBoxes(PlayerColor.BLUE)
        return red to blue
    }

    fun getWinner(board: DotsAndBoxesBoard): PlayerColor? {
        val (red, blue) = getScore(board)
        return when {
            red > blue -> PlayerColor.RED
            blue > red -> PlayerColor.BLUE
            else -> null
        }
    }
}
