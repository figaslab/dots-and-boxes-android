package com.devfigas.dotsandboxes.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesBoard
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesLine
import com.devfigas.dotsandboxes.game.engine.LineOrientation
import com.devfigas.dotsandboxes.game.engine.PlayerColor
import com.devfigas.dotsandboxes.game.state.DotsAndBoxesGameState
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class DotsAndBoxesBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val DOT_RADIUS_RATIO = 0.08f
        private const val DOT_SELECTED_RADIUS_RATIO = 0.12f
        private const val DOT_TARGET_RADIUS_RATIO = 0.10f
        private const val LINE_WIDTH_RATIO = 0.06f
        private const val DOT_TOUCH_THRESHOLD_RATIO = 0.40f

        // Colors
        private val COLOR_RED = Color.rgb(244, 67, 54)
        private val COLOR_RED_FILL = Color.argb(80, 244, 67, 54)
        private val COLOR_BLUE = Color.rgb(33, 150, 243)
        private val COLOR_BLUE_FILL = Color.argb(80, 33, 150, 243)
        private val COLOR_DOT = Color.rgb(220, 220, 220)
        private val COLOR_DOT_SELECTED = Color.rgb(171, 71, 188)
        private val COLOR_DOT_TARGET = Color.rgb(144, 238, 144)
        private val COLOR_DOT_TARGET_RING = Color.argb(100, 144, 238, 144)
        private val COLOR_UNDRAWN_LINE = Color.argb(40, 255, 255, 255)
        private val COLOR_PREVIEW_LINE = Color.argb(140, 171, 71, 188)
        private val COLOR_BACKGROUND = Color.rgb(30, 30, 50)
        private val COLOR_LAST_MOVE = Color.rgb(171, 71, 188)
    }

    private var board: DotsAndBoxesBoard? = null
    private var lastMoveLine: DotsAndBoxesLine? = null
    private var isMyTurn: Boolean = false
    private var onLineSelected: ((DotsAndBoxesLine) -> Unit)? = null

    // Dot selection state
    private var selectedDot: Pair<Int, Int>? = null  // (row, col) of selected dot
    private var isDragging: Boolean = false
    private var dragCurrentX: Float = 0f
    private var dragCurrentY: Float = 0f

    // Calculated dimensions
    private var cellSize: Float = 0f
    private var boardOffsetX: Float = 0f
    private var boardOffsetY: Float = 0f

    // Paints
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_DOT }
    private val dotSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_DOT_SELECTED }
    private val dotTargetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_DOT_TARGET }
    private val dotTargetRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_DOT_TARGET_RING
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val undrawnLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_UNDRAWN_LINE
        strokeCap = Paint.Cap.ROUND
    }
    private val previewLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_PREVIEW_LINE
        strokeCap = Paint.Cap.ROUND
    }
    private val redLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_RED
        strokeCap = Paint.Cap.ROUND
    }
    private val blueLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_BLUE
        strokeCap = Paint.Cap.ROUND
    }
    private val lastMoveLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_LAST_MOVE
        strokeCap = Paint.Cap.ROUND
    }
    private val redFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_RED_FILL }
    private val blueFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_BLUE_FILL }
    private val backgroundPaint = Paint().apply { color = COLOR_BACKGROUND }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    fun setOnLineSelectedListener(listener: (DotsAndBoxesLine) -> Unit) {
        onLineSelected = listener
    }

    fun updateFromState(state: DotsAndBoxesGameState) {
        board = state.board
        isMyTurn = state.isMyTurn()
        lastMoveLine = state.moveHistory.lastOrNull()?.line
        // Clear selection when state changes (new move was made)
        selectedDot = null
        isDragging = false
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val b = board
        if (b != null) {
            val aspectRatio = b.dotCols.toFloat() / b.dotRows.toFloat()
            val height = (width / aspectRatio).toInt()
            setMeasuredDimension(width, height)
        } else {
            setMeasuredDimension(width, width)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = board ?: return

        // Calculate cell size and offsets
        val availableWidth = width.toFloat()
        val availableHeight = height.toFloat()

        val cellW = availableWidth / b.dotCols
        val cellH = availableHeight / b.dotRows
        cellSize = min(cellW, cellH)

        val totalBoardWidth = cellSize * (b.dotCols - 1)
        val totalBoardHeight = cellSize * (b.dotRows - 1)
        boardOffsetX = (availableWidth - totalBoardWidth) / 2f
        boardOffsetY = (availableHeight - totalBoardHeight) / 2f

        val dotRadius = cellSize * DOT_RADIUS_RATIO
        val lineWidth = cellSize * LINE_WIDTH_RATIO

        redLinePaint.strokeWidth = lineWidth
        blueLinePaint.strokeWidth = lineWidth
        undrawnLinePaint.strokeWidth = lineWidth * 0.4f
        lastMoveLinePaint.strokeWidth = lineWidth * 1.3f
        previewLinePaint.strokeWidth = lineWidth * 0.8f
        dotTargetRingPaint.strokeWidth = lineWidth * 0.5f

        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Draw completed boxes
        drawBoxes(canvas, b)

        // Draw lines
        drawLines(canvas, b)

        // Draw preview line while dragging
        drawPreviewLine(canvas, b)

        // Draw dots (with selection highlighting)
        drawDots(canvas, b, dotRadius)
    }

    private fun drawBoxes(canvas: Canvas, board: DotsAndBoxesBoard) {
        val padding = cellSize * 0.08f

        for (row in 0 until board.boxRows) {
            for (col in 0 until board.boxCols) {
                val owner = board.getBoxOwner(row, col) ?: continue
                val left = boardOffsetX + col * cellSize + padding
                val top = boardOffsetY + row * cellSize + padding
                val right = boardOffsetX + (col + 1) * cellSize - padding
                val bottom = boardOffsetY + (row + 1) * cellSize - padding

                val paint = if (owner == PlayerColor.RED) redFillPaint else blueFillPaint
                canvas.drawRoundRect(RectF(left, top, right, bottom), cellSize * 0.05f, cellSize * 0.05f, paint)
            }
        }
    }

    private fun drawLines(canvas: Canvas, board: DotsAndBoxesBoard) {
        // Draw horizontal lines
        for (row in 0 until board.dotRows) {
            for (col in 0 until board.dotCols - 1) {
                val startX = boardOffsetX + col * cellSize
                val startY = boardOffsetY + row * cellSize
                val endX = boardOffsetX + (col + 1) * cellSize
                val endY = startY

                val line = DotsAndBoxesLine(LineOrientation.HORIZONTAL, row, col)

                if (board.isHorizontalLineDrawn(row, col)) {
                    val isLastMove = lastMoveLine == line
                    if (isLastMove) {
                        canvas.drawLine(startX, startY, endX, endY, lastMoveLinePaint)
                    }
                    val owner = board.getHorizontalLineOwner(row, col)
                    val paint = if (owner == PlayerColor.RED) redLinePaint else blueLinePaint
                    canvas.drawLine(startX, startY, endX, endY, paint)
                } else {
                    canvas.drawLine(startX, startY, endX, endY, undrawnLinePaint)
                }
            }
        }

        // Draw vertical lines
        for (row in 0 until board.dotRows - 1) {
            for (col in 0 until board.dotCols) {
                val startX = boardOffsetX + col * cellSize
                val startY = boardOffsetY + row * cellSize
                val endX = startX
                val endY = boardOffsetY + (row + 1) * cellSize

                val line = DotsAndBoxesLine(LineOrientation.VERTICAL, row, col)

                if (board.isVerticalLineDrawn(row, col)) {
                    val isLastMove = lastMoveLine == line
                    if (isLastMove) {
                        canvas.drawLine(startX, startY, endX, endY, lastMoveLinePaint)
                    }
                    val owner = board.getVerticalLineOwner(row, col)
                    val paint = if (owner == PlayerColor.RED) redLinePaint else blueLinePaint
                    canvas.drawLine(startX, startY, endX, endY, paint)
                } else {
                    canvas.drawLine(startX, startY, endX, endY, undrawnLinePaint)
                }
            }
        }
    }

    private fun drawPreviewLine(canvas: Canvas, board: DotsAndBoxesBoard) {
        val sel = selectedDot ?: return
        if (!isDragging) return

        val startX = boardOffsetX + sel.second * cellSize
        val startY = boardOffsetY + sel.first * cellSize

        // Snap to nearest valid target if close enough
        val targetDot = findDotAt(dragCurrentX, dragCurrentY, cellSize * DOT_TOUCH_THRESHOLD_RATIO)
        if (targetDot != null && isValidTarget(board, sel, targetDot)) {
            val endX = boardOffsetX + targetDot.second * cellSize
            val endY = boardOffsetY + targetDot.first * cellSize
            canvas.drawLine(startX, startY, endX, endY, previewLinePaint)
        } else {
            // Draw line following finger
            canvas.drawLine(startX, startY, dragCurrentX, dragCurrentY, previewLinePaint)
        }
    }

    private fun drawDots(canvas: Canvas, board: DotsAndBoxesBoard, dotRadius: Float) {
        val selectedRadius = cellSize * DOT_SELECTED_RADIUS_RATIO
        val targetRadius = cellSize * DOT_TARGET_RADIUS_RATIO
        val targetRingRadius = cellSize * DOT_TARGET_RADIUS_RATIO * 1.6f
        val validTargets = getValidTargets(board)

        for (row in 0 until board.dotRows) {
            for (col in 0 until board.dotCols) {
                val cx = boardOffsetX + col * cellSize
                val cy = boardOffsetY + row * cellSize
                val dot = row to col

                when {
                    dot == selectedDot -> {
                        // Selected dot: larger, yellow
                        canvas.drawCircle(cx, cy, selectedRadius, dotSelectedPaint)
                    }
                    dot in validTargets -> {
                        // Valid target: green ring + green dot
                        canvas.drawCircle(cx, cy, targetRingRadius, dotTargetRingPaint)
                        canvas.drawCircle(cx, cy, targetRadius, dotTargetPaint)
                    }
                    else -> {
                        canvas.drawCircle(cx, cy, dotRadius, dotPaint)
                    }
                }
            }
        }
    }

    /**
     * Returns the set of dots that are valid targets from the currently selected dot.
     * A target is valid if it's an adjacent dot and the line between them is not yet drawn.
     */
    private fun getValidTargets(board: DotsAndBoxesBoard): Set<Pair<Int, Int>> {
        val sel = selectedDot ?: return emptySet()
        val targets = mutableSetOf<Pair<Int, Int>>()
        val (sRow, sCol) = sel

        // Check right neighbor
        if (sCol < board.dotCols - 1 && !board.isHorizontalLineDrawn(sRow, sCol)) {
            targets.add(sRow to sCol + 1)
        }
        // Check left neighbor
        if (sCol > 0 && !board.isHorizontalLineDrawn(sRow, sCol - 1)) {
            targets.add(sRow to sCol - 1)
        }
        // Check bottom neighbor
        if (sRow < board.dotRows - 1 && !board.isVerticalLineDrawn(sRow, sCol)) {
            targets.add(sRow + 1 to sCol)
        }
        // Check top neighbor
        if (sRow > 0 && !board.isVerticalLineDrawn(sRow - 1, sCol)) {
            targets.add(sRow - 1 to sCol)
        }

        return targets
    }

    /**
     * Checks if targetDot is a valid destination from sourceDot (adjacent + line not drawn).
     */
    private fun isValidTarget(board: DotsAndBoxesBoard, source: Pair<Int, Int>, target: Pair<Int, Int>): Boolean {
        val line = dotsToLine(source, target) ?: return false
        return !board.isLineDrawn(line)
    }

    /**
     * Converts two adjacent dots into a DotsAndBoxesLine, or null if not adjacent.
     */
    private fun dotsToLine(dot1: Pair<Int, Int>, dot2: Pair<Int, Int>): DotsAndBoxesLine? {
        val (r1, c1) = dot1
        val (r2, c2) = dot2

        // Horizontal line: same row, adjacent columns
        if (r1 == r2 && abs(c1 - c2) == 1) {
            return DotsAndBoxesLine(LineOrientation.HORIZONTAL, r1, min(c1, c2))
        }
        // Vertical line: same column, adjacent rows
        if (c1 == c2 && abs(r1 - r2) == 1) {
            return DotsAndBoxesLine(LineOrientation.VERTICAL, min(r1, r2), c1)
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val b = board ?: return true
        if (cellSize <= 0) return true

        val touchX = event.x
        val touchY = event.y
        val threshold = cellSize * DOT_TOUCH_THRESHOLD_RATIO

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val dot = findDotAt(touchX, touchY, threshold) ?: return true

                if (selectedDot != null) {
                    // Already have a selected dot - check if tapping a valid target
                    val line = dotsToLine(selectedDot!!, dot)
                    if (line != null && !b.isLineDrawn(line)) {
                        onLineSelected?.invoke(line)
                        selectedDot = null
                        isDragging = false
                        invalidate()
                        return true
                    }
                }

                // Select this dot (or reselect a new one)
                selectedDot = dot
                isDragging = true
                dragCurrentX = touchX
                dragCurrentY = touchY
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging && selectedDot != null) {
                    dragCurrentX = touchX
                    dragCurrentY = touchY
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isDragging && selectedDot != null) {
                    val targetDot = findDotAt(touchX, touchY, threshold)
                    if (targetDot != null && targetDot != selectedDot) {
                        val line = dotsToLine(selectedDot!!, targetDot)
                        if (line != null && !b.isLineDrawn(line)) {
                            onLineSelected?.invoke(line)
                            selectedDot = null
                            isDragging = false
                            invalidate()
                            return true
                        }
                    }
                    // Ended drag on invalid target - keep dot selected but stop dragging
                    isDragging = false
                    invalidate()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                invalidate()
            }
        }

        return true
    }

    /**
     * Finds the dot closest to (x, y) within the given threshold, or null.
     */
    private fun findDotAt(x: Float, y: Float, threshold: Float): Pair<Int, Int>? {
        val b = board ?: return null
        var bestDot: Pair<Int, Int>? = null
        var bestDist = Float.MAX_VALUE

        for (row in 0 until b.dotRows) {
            for (col in 0 until b.dotCols) {
                val cx = boardOffsetX + col * cellSize
                val cy = boardOffsetY + row * cellSize
                val dist = distance(x, y, cx, cy)
                if (dist < threshold && dist < bestDist) {
                    bestDist = dist
                    bestDot = row to col
                }
            }
        }
        return bestDot
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }
}
