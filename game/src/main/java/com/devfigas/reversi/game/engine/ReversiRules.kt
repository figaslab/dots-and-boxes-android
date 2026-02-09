package com.devfigas.reversi.game.engine

object ReversiRules {

    private val DIRECTIONS = listOf(
        -1 to -1, -1 to 0, -1 to 1,
        0 to -1,           0 to 1,
        1 to -1,  1 to 0,  1 to 1
    )

    fun getValidMoves(board: ReversiBoard, color: ReversiColor): List<ReversiMove> {
        val moves = mutableListOf<ReversiMove>()
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                if (board.getPiece(row, col) != null) continue
                val position = Position(row, col)
                val flipped = getFlippedPositions(board, position, color)
                if (flipped.isNotEmpty()) {
                    moves.add(ReversiMove(position, color, flipped))
                }
            }
        }
        return moves
    }

    fun getFlippedPositions(
        board: ReversiBoard,
        position: Position,
        color: ReversiColor
    ): List<Position> {
        val allFlipped = mutableListOf<Position>()
        val oppositeColor = color.opposite()

        for ((dr, dc) in DIRECTIONS) {
            val candidates = mutableListOf<Position>()
            var r = position.row + dr
            var c = position.col + dc

            while (r in 0..7 && c in 0..7) {
                val piece = board.getPiece(r, c)
                if (piece == null) {
                    break
                } else if (piece.color == oppositeColor) {
                    candidates.add(Position(r, c))
                } else {
                    // Same color found -- all candidates in between are flanked
                    allFlipped.addAll(candidates)
                    break
                }
                r += dr
                c += dc
            }
        }

        return allFlipped
    }

    fun applyMove(board: ReversiBoard, move: ReversiMove): ReversiBoard {
        val newBoard = board.copy()
        newBoard.setPiece(move.position.row, move.position.col, ReversiPiece(move.color))
        for (pos in move.flippedPositions) {
            newBoard.setPiece(pos.row, pos.col, ReversiPiece(move.color))
        }
        return newBoard
    }

    fun hasValidMoves(board: ReversiBoard, color: ReversiColor): Boolean {
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                if (board.getPiece(row, col) != null) continue
                val position = Position(row, col)
                if (getFlippedPositions(board, position, color).isNotEmpty()) {
                    return true
                }
            }
        }
        return false
    }

    fun isGameOver(board: ReversiBoard): Boolean {
        if (board.isFull()) return true
        return !hasValidMoves(board, ReversiColor.BLACK) && !hasValidMoves(board, ReversiColor.WHITE)
    }

    fun getScore(board: ReversiBoard): Pair<Int, Int> {
        val black = board.countPieces(ReversiColor.BLACK)
        val white = board.countPieces(ReversiColor.WHITE)
        return black to white
    }

    fun getWinner(board: ReversiBoard): ReversiColor? {
        val (black, white) = getScore(board)
        return when {
            black > white -> ReversiColor.BLACK
            white > black -> ReversiColor.WHITE
            else -> null
        }
    }
}
