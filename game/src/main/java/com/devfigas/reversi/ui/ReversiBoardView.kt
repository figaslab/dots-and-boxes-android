package com.devfigas.reversi.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.devfigas.reversi.game.engine.ReversiBoard
import com.devfigas.reversi.game.engine.ReversiColor
import com.devfigas.reversi.game.engine.ReversiPiece
import com.devfigas.reversi.game.engine.Position
import com.devfigas.reversi.game.state.ReversiGameState
import com.devfigas.gridgame.model.GridBoard
import com.devfigas.gridgame.model.GridPiece
import com.devfigas.gridgame.model.GridPosition
import com.devfigas.gridgame.model.PlayerSide
import com.devfigas.gridgame.ui.GridBoardView
import android.graphics.Canvas

class ReversiBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : GridBoardView(context, attrs, defStyleAttr) {

    companion object {
        private const val FLIP_DURATION_MS = 350L
        private const val FLIP_START_DELAY_MS = 100L
        private const val PLACE_DURATION_MS = 200L
    }

    private var lastMoveCount: Int = 0
    private var previousBoard: ReversiBoard? = null

    // Flip animation state
    private var flippingPositions: Set<GridPosition> = emptySet()
    private var flipProgress: Float = 1f
    private var flipAnimator: ValueAnimator? = null
    private var flipOldPieces: Map<GridPosition, GridPiece> = emptyMap()
    private var flipNewPieces: Map<GridPosition, GridPiece> = emptyMap()

    // Place animation state (newly placed disc appears with scale)
    private var placingPosition: GridPosition? = null
    private var placeProgress: Float = 1f
    private var placeAnimator: ValueAnimator? = null
    private var placingPiece: GridPiece? = null

    init {
        setPieceRenderer(ReversiPieceRenderer())
    }

    fun updateFromState(state: ReversiGameState) {
        val newMoveCount = state.moveHistory.size
        val lastMove = state.moveHistory.lastOrNull()

        // Check for game restart
        if (state.moveHistory.isEmpty() && (lastMoveCount > 0 || isAnimating() || isFlipAnimating())) {
            cancelAllAnimations()
            setBoard(BoardAdapter(state.board))
            setSelection(null, state.validMoves.map { GridPosition(it.row, it.col) })
            setAlertPosition(null)
            lastMoveCount = 0
            previousBoard = state.board.copy() as ReversiBoard
            return
        }

        // Check if there's a new move to animate
        if (lastMove != null && newMoveCount > lastMoveCount && hasBoard()) {
            val pos = lastMove.position
            val gridPos = GridPosition(pos.row, pos.col)
            val piece = state.board.getPiece(pos.row, pos.col)

            if (piece != null) {
                val flippedGridPositions = lastMove.flippedPositions.map {
                    GridPosition(it.row, it.col)
                }

                // Collect old and new pieces for flip animation
                val oldPieces = mutableMapOf<GridPosition, GridPiece>()
                val newPieces = mutableMapOf<GridPosition, GridPiece>()
                val prevBoard = previousBoard
                if (prevBoard != null && flippedGridPositions.isNotEmpty()) {
                    for (fp in flippedGridPositions) {
                        prevBoard.getPiece(fp.row, fp.col)?.let { oldPieces[fp] = it }
                        state.board.getPiece(fp.row, fp.col)?.let { newPieces[fp] = it }
                    }
                }

                // Set the new board immediately
                setBoard(BoardAdapter(state.board))

                // Start place animation (disc pops in)
                startPlaceAnimation(gridPos, piece)

                // Start flip animation for flipped pieces
                if (oldPieces.isNotEmpty() && newPieces.isNotEmpty()) {
                    startFlipAnimation(flippedGridPositions.toSet(), oldPieces, newPieces)
                }

                setSelection(null, state.validMoves.map { GridPosition(it.row, it.col) })
                lastMoveCount = newMoveCount
                setLastMove(gridPos, gridPos)
                setAlertPosition(null)
                previousBoard = state.board.copy() as ReversiBoard
                return
            }
        }

        // No animation needed, update immediately
        cancelAllAnimations()
        setBoard(BoardAdapter(state.board))
        setSelection(null, state.validMoves.map { GridPosition(it.row, it.col) })
        lastMoveCount = newMoveCount
        if (lastMove != null) {
            val gridPos = GridPosition(lastMove.position.row, lastMove.position.col)
            setLastMove(gridPos, gridPos)
        } else {
            setLastMove(null, null)
        }
        setAlertPosition(null)
        previousBoard = state.board.copy() as ReversiBoard
    }

    // --- Animation control ---

    private fun startPlaceAnimation(position: GridPosition, piece: GridPiece) {
        placeAnimator?.cancel()
        placingPosition = position
        placingPiece = piece
        placeProgress = 0f

        placeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PLACE_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                placeProgress = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finishPlaceAnimation()
                }
                override fun onAnimationCancel(animation: Animator) {
                    finishPlaceAnimation()
                }
            })
            start()
        }
    }

    private fun finishPlaceAnimation() {
        placingPosition = null
        placingPiece = null
        placeProgress = 1f
        invalidate()
    }

    private fun startFlipAnimation(
        positions: Set<GridPosition>,
        oldPieces: Map<GridPosition, GridPiece>,
        newPieces: Map<GridPosition, GridPiece>
    ) {
        flipAnimator?.cancel()
        flippingPositions = positions
        flipOldPieces = oldPieces
        flipNewPieces = newPieces
        flipProgress = 0f

        flipAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FLIP_DURATION_MS
            startDelay = FLIP_START_DELAY_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                flipProgress = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finishFlipAnimation()
                }
                override fun onAnimationCancel(animation: Animator) {
                    finishFlipAnimation()
                }
            })
            start()
        }
    }

    private fun finishFlipAnimation() {
        flippingPositions = emptySet()
        flipOldPieces = emptyMap()
        flipNewPieces = emptyMap()
        flipProgress = 1f
        flipAnimator = null
        invalidate()
    }

    private fun cancelAllAnimations() {
        placeAnimator?.cancel()
        finishPlaceAnimation()
        flipAnimator?.cancel()
        finishFlipAnimation()
        clearAnimationState()
    }

    private fun isFlipAnimating(): Boolean = flippingPositions.isNotEmpty()

    // --- Drawing hooks ---

    override fun shouldSkipPosition(position: GridPosition): Boolean {
        // Skip positions that are being animated (place or flip)
        if (flippingPositions.contains(position)) return true
        if (position == placingPosition && placeProgress < 1f) return true
        return false
    }

    override fun onPostDrawPieces(canvas: Canvas) {
        drawPlacingPiece(canvas)
        drawFlippingPieces(canvas)
    }

    private fun drawPlacingPiece(canvas: Canvas) {
        val pos = placingPosition ?: return
        val piece = placingPiece ?: return
        if (placeProgress >= 1f) return

        val sqSize = getSquareSize()
        val flipped = isBoardFlipped()
        val rows = getBoardRowCount()
        val cols = getBoardColCount()
        val displayRow = if (flipped) rows - 1 - pos.row else pos.row
        val displayCol = if (flipped) cols - 1 - pos.col else pos.col
        val left = displayCol * sqSize
        val top = displayRow * sqSize
        val centerX = left + sqSize / 2f
        val centerY = top + sqSize / 2f

        // Scale from 0 to 1 (pop-in effect)
        val scale = placeProgress

        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.scale(scale, scale)
        canvas.translate(-centerX, -centerY)
        drawPiece(canvas, piece, left, top)
        canvas.restore()
    }

    private fun drawFlippingPieces(canvas: Canvas) {
        if (flippingPositions.isEmpty()) return

        val sqSize = getSquareSize()
        val flipped = isBoardFlipped()
        val rows = getBoardRowCount()

        for (pos in flippingPositions) {
            val displayRow = if (flipped) rows - 1 - pos.row else pos.row
            val displayCol = if (flipped) getBoardColCount() - 1 - pos.col else pos.col
            val left = displayCol * sqSize
            val top = displayRow * sqSize
            val centerX = left + sqSize / 2f
            val centerY = top + sqSize / 2f

            val piece: GridPiece?
            val scaleX: Float

            if (flipProgress < 0.5f) {
                // First half: compress old piece horizontally
                piece = flipOldPieces[pos]
                scaleX = 1f - flipProgress * 2f  // 1.0 → 0.0
            } else {
                // Second half: expand new piece horizontally
                piece = flipNewPieces[pos]
                scaleX = (flipProgress - 0.5f) * 2f  // 0.0 → 1.0
            }

            if (piece != null && scaleX > 0.01f) {
                canvas.save()
                canvas.translate(centerX, centerY)
                canvas.scale(scaleX, 1f)
                canvas.translate(-centerX, -centerY)
                drawPiece(canvas, piece, left, top)
                canvas.restore()
            }
        }
    }

    // --- Board adapter ---

    private class BoardAdapter(private val board: ReversiBoard) : GridBoard<ReversiPiece> {
        override val rows: Int = 8
        override val cols: Int = 8

        override fun getPiece(row: Int, col: Int): ReversiPiece? {
            if (row !in 0..7 || col !in 0..7) return null
            return board.getPiece(row, col)
        }

        override fun getAllPieces(): List<Pair<GridPosition, ReversiPiece>> {
            val pieces = mutableListOf<Pair<GridPosition, ReversiPiece>>()
            for (r in 0 until 8) {
                for (c in 0 until 8) {
                    board.getPiece(r, c)?.let { pieces.add(GridPosition(r, c) to it) }
                }
            }
            return pieces
        }

        override fun getAllPieces(side: PlayerSide): List<Pair<GridPosition, ReversiPiece>> {
            val color = if (side == PlayerSide.FIRST) ReversiColor.BLACK else ReversiColor.WHITE
            val pieces = mutableListOf<Pair<GridPosition, ReversiPiece>>()
            for (r in 0 until 8) {
                for (c in 0 until 8) {
                    val piece = board.getPiece(r, c)
                    if (piece != null && piece.color == color) {
                        pieces.add(GridPosition(r, c) to piece)
                    }
                }
            }
            return pieces
        }

        override fun copy(): GridBoard<ReversiPiece> = BoardAdapter(board.copy() as ReversiBoard)
    }
}
