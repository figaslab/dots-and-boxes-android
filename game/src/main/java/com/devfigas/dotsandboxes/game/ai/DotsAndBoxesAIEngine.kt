package com.devfigas.dotsandboxes.game.ai

import android.util.Log
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesBoard
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesMove
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesRules
import com.devfigas.dotsandboxes.game.engine.PlayerColor
import com.devfigas.dotsandboxes.game.state.DotsAndBoxesGameState
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class DotsAndBoxesAIEngine(private val level: Int = 5) : DotsAndBoxesAI {

    companion object {
        private const val TAG = "DotsAndBoxesAI"
    }

    override fun selectMove(state: DotsAndBoxesGameState): DotsAndBoxesMove? {
        val validMoves = DotsAndBoxesRules.getValidMoves(state.board, state.currentTurn)
        if (validMoves.isEmpty()) return null
        if (validMoves.size == 1) return validMoves.first()

        return when {
            level <= 2 -> selectMoveEasy(state.board, validMoves, state.currentTurn)
            level <= 5 -> selectMoveMedium(state.board, validMoves, state.currentTurn)
            else -> selectMoveHard(state.board, validMoves, state.currentTurn)
        }
    }

    /**
     * Easy AI: Random selection, but always completes a box if possible.
     */
    private fun selectMoveEasy(
        board: DotsAndBoxesBoard,
        moves: List<DotsAndBoxesMove>,
        player: PlayerColor
    ): DotsAndBoxesMove {
        // First, check for box-completing moves
        val completingMoves = moves.filter { move ->
            val result = DotsAndBoxesRules.applyMove(board, move)
            result.boxesCompleted > 0
        }
        if (completingMoves.isNotEmpty()) {
            return completingMoves.random()
        }

        // Otherwise, random move
        return moves.random()
    }

    /**
     * Medium AI: Complete boxes when possible, avoid giving boxes to opponent.
     * A move that makes a box have 3 sides is dangerous (opponent completes it next).
     */
    private fun selectMoveMedium(
        board: DotsAndBoxesBoard,
        moves: List<DotsAndBoxesMove>,
        player: PlayerColor
    ): DotsAndBoxesMove {
        // Priority 1: Complete boxes
        val completingMoves = moves.filter { move ->
            val result = DotsAndBoxesRules.applyMove(board, move)
            result.boxesCompleted > 0
        }
        if (completingMoves.isNotEmpty()) {
            // Pick the one that completes the most boxes
            return completingMoves.maxByOrNull { move ->
                DotsAndBoxesRules.applyMove(board, move).boxesCompleted
            }!!
        }

        // Priority 2: Avoid moves that give opponent a box (creating 3-sided boxes)
        val safeMoves = moves.filter { move ->
            val adjacentBoxes = board.getAdjacentBoxes(move.line)
            adjacentBoxes.none { (boxRow, boxCol) ->
                board.countBoxSides(boxRow, boxCol) == 2
            }
        }

        if (safeMoves.isNotEmpty()) {
            return safeMoves.random()
        }

        // All moves are dangerous - pick the one that gives away the fewest boxes
        return moves.minByOrNull { move ->
            val adjacentBoxes = board.getAdjacentBoxes(move.line)
            adjacentBoxes.count { (boxRow, boxCol) ->
                board.countBoxSides(boxRow, boxCol) == 2
            }
        }!!
    }

    /**
     * Hard AI: Minimax with alpha-beta pruning.
     * Evaluates chains and double-crosses for strategic play.
     */
    private fun selectMoveHard(
        board: DotsAndBoxesBoard,
        moves: List<DotsAndBoxesMove>,
        player: PlayerColor
    ): DotsAndBoxesMove {
        val depth = when {
            level >= 9 -> 6
            level >= 7 -> 4
            else -> 3
        }

        var bestMove = moves.first()
        var bestScore = Double.NEGATIVE_INFINITY

        for (move in moves.shuffled()) {
            val result = DotsAndBoxesRules.applyMove(board, move)
            val score = if (result.extraTurn) {
                // If we get an extra turn, we continue as maximizer
                minimax(result.board, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, true, player, player)
            } else {
                minimax(result.board, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, player.opposite(), player)
            } + result.boxesCompleted * 100.0

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }

        Log.d(TAG, "AI L$level selected ${bestMove.line.toNotation()} score=${"%.1f".format(bestScore)}")
        return bestMove
    }

    private fun minimax(
        board: DotsAndBoxesBoard,
        depth: Int,
        alpha: Double,
        beta: Double,
        isMaximizing: Boolean,
        currentPlayer: PlayerColor,
        myColor: PlayerColor
    ): Double {
        if (depth == 0 || DotsAndBoxesRules.isGameOver(board)) {
            return evaluate(board, myColor)
        }

        val moves = DotsAndBoxesRules.getValidMoves(board, currentPlayer)
        if (moves.isEmpty()) return evaluate(board, myColor)

        var currentAlpha = alpha
        var currentBeta = beta

        if (isMaximizing) {
            var maxEval = Double.NEGATIVE_INFINITY
            for (move in moves) {
                val result = DotsAndBoxesRules.applyMove(board, move)
                val eval = if (result.extraTurn) {
                    minimax(result.board, depth - 1, currentAlpha, currentBeta, true, currentPlayer, myColor)
                } else {
                    minimax(result.board, depth - 1, currentAlpha, currentBeta, false, currentPlayer.opposite(), myColor)
                } + result.boxesCompleted * 10.0

                maxEval = max(maxEval, eval)
                currentAlpha = max(currentAlpha, eval)
                if (currentBeta <= currentAlpha) break
            }
            return maxEval
        } else {
            var minEval = Double.POSITIVE_INFINITY
            for (move in moves) {
                val result = DotsAndBoxesRules.applyMove(board, move)
                val eval = if (result.extraTurn) {
                    minimax(result.board, depth - 1, currentAlpha, currentBeta, false, currentPlayer, myColor)
                } else {
                    minimax(result.board, depth - 1, currentAlpha, currentBeta, true, currentPlayer.opposite(), myColor)
                } - result.boxesCompleted * 10.0

                minEval = min(minEval, eval)
                currentBeta = min(currentBeta, eval)
                if (currentBeta <= currentAlpha) break
            }
            return minEval
        }
    }

    private fun evaluate(board: DotsAndBoxesBoard, myColor: PlayerColor): Double {
        val myScore = board.countBoxes(myColor)
        val oppScore = board.countBoxes(myColor.opposite())
        var eval = (myScore - oppScore) * 100.0

        // Penalize creating 3-sided boxes for the opponent
        for (row in 0 until board.boxRows) {
            for (col in 0 until board.boxCols) {
                if (board.getBoxOwner(row, col) == null) {
                    val sides = board.countBoxSides(row, col)
                    if (sides == 3) {
                        // This box will be taken next turn - good or bad depending on whose turn
                        eval -= 20.0
                    }
                }
            }
        }

        return eval
    }

    fun calculateThinkingTimeMs(state: DotsAndBoxesGameState): Long {
        val movesLeft = state.board.totalLines - state.board.countDrawnLines()
        val baseTime = when {
            level <= 2 -> 400L
            level <= 5 -> 800L
            else -> 1200L
        }
        val perMoveTime = when {
            level <= 2 -> 10L
            level <= 5 -> 20L
            else -> 30L
        }
        val time = baseTime + perMoveTime * movesLeft + Random.nextLong(0, 500)
        return time.coerceIn(300, 5000)
    }
}
