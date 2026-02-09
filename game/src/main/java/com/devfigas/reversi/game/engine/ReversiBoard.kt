package com.devfigas.reversi.game.engine

import com.devfigas.gridgame.model.GridBoard
import com.devfigas.gridgame.model.GridPosition
import com.devfigas.gridgame.model.PlayerSide

class ReversiBoard private constructor(
    private val cells: Array<Array<ReversiPiece?>>
) : GridBoard<ReversiPiece> {

    override val rows: Int = 8
    override val cols: Int = 8

    override fun getPiece(row: Int, col: Int): ReversiPiece? {
        if (row !in 0 until rows || col !in 0 until cols) return null
        return cells[row][col]
    }

    override fun getAllPieces(): List<Pair<GridPosition, ReversiPiece>> {
        val result = mutableListOf<Pair<GridPosition, ReversiPiece>>()
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val piece = cells[row][col]
                if (piece != null) {
                    result.add(GridPosition(row, col) to piece)
                }
            }
        }
        return result
    }

    override fun getAllPieces(side: PlayerSide): List<Pair<GridPosition, ReversiPiece>> {
        return getAllPieces().filter { it.second.owner == side }
    }

    override fun copy(): ReversiBoard {
        val newCells = Array(rows) { row ->
            Array(cols) { col ->
                cells[row][col]?.copy()
            }
        }
        return ReversiBoard(newCells)
    }

    fun setPiece(row: Int, col: Int, piece: ReversiPiece?) {
        if (row in 0 until rows && col in 0 until cols) {
            cells[row][col] = piece
        }
    }

    fun removePiece(row: Int, col: Int) {
        if (row in 0 until rows && col in 0 until cols) {
            cells[row][col] = null
        }
    }

    fun countPieces(color: ReversiColor): Int {
        var count = 0
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (cells[row][col]?.color == color) {
                    count++
                }
            }
        }
        return count
    }

    fun countTotal(): Int {
        var count = 0
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (cells[row][col] != null) {
                    count++
                }
            }
        }
        return count
    }

    fun isFull(): Boolean = countTotal() == rows * cols

    companion object {
        fun createInitial(): ReversiBoard {
            val cells = Array(8) { arrayOfNulls<ReversiPiece>(8) }
            cells[3][3] = ReversiPiece(ReversiColor.WHITE)
            cells[3][4] = ReversiPiece(ReversiColor.BLACK)
            cells[4][3] = ReversiPiece(ReversiColor.BLACK)
            cells[4][4] = ReversiPiece(ReversiColor.WHITE)
            return ReversiBoard(cells)
        }

        fun createEmpty(): ReversiBoard {
            val cells = Array(8) { arrayOfNulls<ReversiPiece>(8) }
            return ReversiBoard(cells)
        }
    }
}
