package com.devfigas.dotsandboxes.game.engine

/**
 * Board for Dots and Boxes game.
 *
 * For a grid of [dotRows] x [dotCols] dots:
 * - Horizontal lines: [dotRows] rows x ([dotCols]-1) cols
 * - Vertical lines: ([dotRows]-1) rows x [dotCols] cols
 * - Boxes: ([dotRows]-1) x ([dotCols]-1)
 *
 * Default: 5x5 dots = 4x4 boxes = 16 possible boxes, 40 total lines
 */
class DotsAndBoxesBoard private constructor(
    val dotRows: Int,
    val dotCols: Int,
    private val horizontalLines: Array<BooleanArray>,
    private val verticalLines: Array<BooleanArray>,
    private val horizontalLineOwners: Array<Array<PlayerColor?>>,
    private val verticalLineOwners: Array<Array<PlayerColor?>>,
    private val boxes: Array<Array<PlayerColor?>>
) {
    val boxRows: Int get() = dotRows - 1
    val boxCols: Int get() = dotCols - 1
    val totalLines: Int get() = dotRows * (dotCols - 1) + (dotRows - 1) * dotCols

    fun isHorizontalLineDrawn(row: Int, col: Int): Boolean {
        if (row !in 0 until dotRows || col !in 0 until dotCols - 1) return false
        return horizontalLines[row][col]
    }

    fun isVerticalLineDrawn(row: Int, col: Int): Boolean {
        if (row !in 0 until dotRows - 1 || col !in 0 until dotCols) return false
        return verticalLines[row][col]
    }

    fun isLineDrawn(line: DotsAndBoxesLine): Boolean {
        return when (line.orientation) {
            LineOrientation.HORIZONTAL -> isHorizontalLineDrawn(line.row, line.col)
            LineOrientation.VERTICAL -> isVerticalLineDrawn(line.row, line.col)
        }
    }

    fun getHorizontalLineOwner(row: Int, col: Int): PlayerColor? {
        if (row !in 0 until dotRows || col !in 0 until dotCols - 1) return null
        return horizontalLineOwners[row][col]
    }

    fun getVerticalLineOwner(row: Int, col: Int): PlayerColor? {
        if (row !in 0 until dotRows - 1 || col !in 0 until dotCols) return null
        return verticalLineOwners[row][col]
    }

    fun getLineOwner(line: DotsAndBoxesLine): PlayerColor? {
        return when (line.orientation) {
            LineOrientation.HORIZONTAL -> getHorizontalLineOwner(line.row, line.col)
            LineOrientation.VERTICAL -> getVerticalLineOwner(line.row, line.col)
        }
    }

    fun getBoxOwner(row: Int, col: Int): PlayerColor? {
        if (row !in 0 until boxRows || col !in 0 until boxCols) return null
        return boxes[row][col]
    }

    fun countBoxes(color: PlayerColor): Int {
        var count = 0
        for (row in 0 until boxRows) {
            for (col in 0 until boxCols) {
                if (boxes[row][col] == color) count++
            }
        }
        return count
    }

    fun countDrawnLines(): Int {
        var count = 0
        for (row in 0 until dotRows) {
            for (col in 0 until dotCols - 1) {
                if (horizontalLines[row][col]) count++
            }
        }
        for (row in 0 until dotRows - 1) {
            for (col in 0 until dotCols) {
                if (verticalLines[row][col]) count++
            }
        }
        return count
    }

    fun allLinesDrawn(): Boolean = countDrawnLines() == totalLines

    /**
     * Count how many sides of a given box are drawn.
     * A box at (row, col) has 4 sides:
     * - Top: horizontal line (row, col)
     * - Bottom: horizontal line (row+1, col)
     * - Left: vertical line (row, col)
     * - Right: vertical line (row, col+1)
     */
    fun countBoxSides(row: Int, col: Int): Int {
        if (row !in 0 until boxRows || col !in 0 until boxCols) return 0
        var sides = 0
        if (horizontalLines[row][col]) sides++       // top
        if (horizontalLines[row + 1][col]) sides++   // bottom
        if (verticalLines[row][col]) sides++          // left
        if (verticalLines[row][col + 1]) sides++      // right
        return sides
    }

    fun drawLine(line: DotsAndBoxesLine, player: PlayerColor): DotsAndBoxesBoard {
        val newH = horizontalLines.map { it.copyOf() }.toTypedArray()
        val newV = verticalLines.map { it.copyOf() }.toTypedArray()
        val newHOwners = horizontalLineOwners.map { it.copyOf() }.toTypedArray()
        val newVOwners = verticalLineOwners.map { it.copyOf() }.toTypedArray()
        val newBoxes = boxes.map { it.copyOf() }.toTypedArray()

        when (line.orientation) {
            LineOrientation.HORIZONTAL -> {
                newH[line.row][line.col] = true
                newHOwners[line.row][line.col] = player
            }
            LineOrientation.VERTICAL -> {
                newV[line.row][line.col] = true
                newVOwners[line.row][line.col] = player
            }
        }

        // Check which boxes were completed by this line
        val adjacentBoxes = getAdjacentBoxes(line)
        for ((boxRow, boxCol) in adjacentBoxes) {
            if (countBoxSidesOnArrays(newH, newV, boxRow, boxCol) == 4) {
                newBoxes[boxRow][boxCol] = player
            }
        }

        return DotsAndBoxesBoard(dotRows, dotCols, newH, newV, newHOwners, newVOwners, newBoxes)
    }

    private fun countBoxSidesOnArrays(
        h: Array<BooleanArray>,
        v: Array<BooleanArray>,
        row: Int,
        col: Int
    ): Int {
        var sides = 0
        if (h[row][col]) sides++       // top
        if (h[row + 1][col]) sides++   // bottom
        if (v[row][col]) sides++       // left
        if (v[row][col + 1]) sides++   // right
        return sides
    }

    /**
     * Returns the box positions adjacent to this line.
     * A horizontal line at (row, col) is adjacent to:
     * - box (row-1, col) above (if row > 0)
     * - box (row, col) below (if row < boxRows)
     * A vertical line at (row, col) is adjacent to:
     * - box (row, col-1) left (if col > 0)
     * - box (row, col) right (if col < boxCols)
     */
    fun getAdjacentBoxes(line: DotsAndBoxesLine): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        when (line.orientation) {
            LineOrientation.HORIZONTAL -> {
                if (line.row > 0) result.add(line.row - 1 to line.col)
                if (line.row < boxRows) result.add(line.row to line.col)
            }
            LineOrientation.VERTICAL -> {
                if (line.col > 0) result.add(line.row to line.col - 1)
                if (line.col < boxCols) result.add(line.row to line.col)
            }
        }
        return result
    }

    fun copy(): DotsAndBoxesBoard {
        return DotsAndBoxesBoard(
            dotRows, dotCols,
            horizontalLines.map { it.copyOf() }.toTypedArray(),
            verticalLines.map { it.copyOf() }.toTypedArray(),
            horizontalLineOwners.map { it.copyOf() }.toTypedArray(),
            verticalLineOwners.map { it.copyOf() }.toTypedArray(),
            boxes.map { it.copyOf() }.toTypedArray()
        )
    }

    /**
     * Encode board state as a string for network sync.
     * Format: horizontal lines (0/1) + "|" + vertical lines (0/1) + "|" + box owners (R/B/.)
     */
    fun encode(): String {
        val sb = StringBuilder()
        // Horizontal lines
        for (row in 0 until dotRows) {
            for (col in 0 until dotCols - 1) {
                sb.append(if (horizontalLines[row][col]) '1' else '0')
            }
        }
        sb.append('|')
        // Vertical lines
        for (row in 0 until dotRows - 1) {
            for (col in 0 until dotCols) {
                sb.append(if (verticalLines[row][col]) '1' else '0')
            }
        }
        sb.append('|')
        // Box owners
        for (row in 0 until boxRows) {
            for (col in 0 until boxCols) {
                sb.append(when (boxes[row][col]) {
                    PlayerColor.RED -> 'R'
                    PlayerColor.BLUE -> 'B'
                    null -> '.'
                })
            }
        }
        return sb.toString()
    }

    companion object {
        fun createEmpty(dotRows: Int = 5, dotCols: Int = 5): DotsAndBoxesBoard {
            return DotsAndBoxesBoard(
                dotRows = dotRows,
                dotCols = dotCols,
                horizontalLines = Array(dotRows) { BooleanArray(dotCols - 1) },
                verticalLines = Array(dotRows - 1) { BooleanArray(dotCols) },
                horizontalLineOwners = Array(dotRows) { arrayOfNulls(dotCols - 1) },
                verticalLineOwners = Array(dotRows - 1) { arrayOfNulls(dotCols) },
                boxes = Array(dotRows - 1) { arrayOfNulls(dotCols - 1) }
            )
        }

        fun decode(data: String, dotRows: Int = 5, dotCols: Int = 5): DotsAndBoxesBoard? {
            val parts = data.split("|")
            if (parts.size != 3) return null

            val board = createEmpty(dotRows, dotCols)
            val hPart = parts[0]
            val vPart = parts[1]
            val bPart = parts[2]

            var idx = 0
            for (row in 0 until dotRows) {
                for (col in 0 until dotCols - 1) {
                    if (idx >= hPart.length) return null
                    if (hPart[idx] == '1') {
                        board.horizontalLines[row][col] = true
                    }
                    idx++
                }
            }

            idx = 0
            for (row in 0 until dotRows - 1) {
                for (col in 0 until dotCols) {
                    if (idx >= vPart.length) return null
                    if (vPart[idx] == '1') {
                        board.verticalLines[row][col] = true
                    }
                    idx++
                }
            }

            idx = 0
            for (row in 0 until dotRows - 1) {
                for (col in 0 until dotCols - 1) {
                    if (idx >= bPart.length) return null
                    board.boxes[row][col] = when (bPart[idx]) {
                        'R' -> PlayerColor.RED
                        'B' -> PlayerColor.BLUE
                        else -> null
                    }
                    idx++
                }
            }

            return board
        }
    }
}
