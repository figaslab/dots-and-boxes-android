package com.devfigas.reversi.game.ai

import com.devfigas.reversi.game.engine.ReversiBoard
import com.devfigas.reversi.game.engine.ReversiColor
import com.devfigas.reversi.game.engine.ReversiMove
import com.devfigas.reversi.game.engine.ReversiRules
import com.devfigas.reversi.game.state.ReversiGameState
import android.util.Log
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class ReversiAIEngine(private val level: Int = 5) : ReversiAI {

    /**
     * Evaluation mode determines which factors the AI considers.
     * Lower levels use simpler evaluations, mimicking how beginners think.
     */
    private enum class EvalMode {
        /** Only considers capture count and corners. Like a beginner who just wants more pieces. */
        GREEDY,
        /** Adds X-square avoidance and edge awareness. */
        BASIC,
        /** Adds mobility and positional weight table. */
        STANDARD,
        /** Full evaluation: parity, corners, mobility, stability, positional weights. */
        FULL
    }

    private data class LevelConfig(
        val depth: Int,            // Minimax depth (0 = no lookahead, just evaluate resulting board)
        val evalMode: EvalMode,    // What the AI "sees" when evaluating
        val errorRate: Double,     // Probability of picking a suboptimal move
        val mistakeQuality: Double // When making a mistake: 0.0 = terrible, 1.0 = near-best
    )

    private data class TimingConfig(
        val baseTimeMs: Long,      // Base thinking time
        val perOptionMs: Long,     // Additional ms per valid move option
        val criticalBonusMs: Long, // Extra ms for critical positions (corners available)
        val varianceMs: Long,      // Random variance range
        val maxTimeMs: Long        // Hard cap
    )

    companion object {
        private const val TAG = "ReversiAI"

        private val LEVEL_CONFIGS = listOf(
            // Level 0 (Practice): Greedy - captures most pieces, grabs corners
            LevelConfig(depth = 0, evalMode = EvalMode.GREEDY, errorRate = 0.15, mistakeQuality = 0.0),
            // Level 1 (Bronze 1): Greedy with slight depth
            LevelConfig(depth = 1, evalMode = EvalMode.GREEDY, errorRate = 0.25, mistakeQuality = 0.1),
            // Level 2 (Bronze 2): Starts noticing X-squares and edges
            LevelConfig(depth = 1, evalMode = EvalMode.BASIC, errorRate = 0.20, mistakeQuality = 0.2),
            // Level 3 (Silver 1): Basic positional play with some lookahead
            LevelConfig(depth = 2, evalMode = EvalMode.BASIC, errorRate = 0.15, mistakeQuality = 0.3),
            // Level 4 (Silver 2): Understands mobility
            LevelConfig(depth = 3, evalMode = EvalMode.STANDARD, errorRate = 0.12, mistakeQuality = 0.5),
            // Level 5 (Gold 1): Full evaluation, decent depth
            LevelConfig(depth = 3, evalMode = EvalMode.FULL, errorRate = 0.08, mistakeQuality = 0.6),
            // Level 6 (Gold 2): Stronger play
            LevelConfig(depth = 4, evalMode = EvalMode.FULL, errorRate = 0.05, mistakeQuality = 0.7),
            // Level 7 (Platinum): Deep analysis
            LevelConfig(depth = 5, evalMode = EvalMode.FULL, errorRate = 0.03, mistakeQuality = 0.8),
            // Level 8 (Diamond): Very strong
            LevelConfig(depth = 6, evalMode = EvalMode.FULL, errorRate = 0.01, mistakeQuality = 0.9),
            // Level 9 (Master): Perfect play
            LevelConfig(depth = 7, evalMode = EvalMode.FULL, errorRate = 0.0, mistakeQuality = 1.0)
        )

        /**
         * Timing configs for human-like response times in tournament mode.
         * - Beginners: impulsive, relatively fast
         * - Intermediate: moderate, deliberate
         * - Advanced: variable - fast on obvious, slow on critical
         * - Expert: highly variable based on position complexity
         */
        private val TIMING_CONFIGS = listOf(
            // Level 0: Practice bot - fast but not instant
            TimingConfig(baseTimeMs = 400, perOptionMs = 15, criticalBonusMs = 100, varianceMs = 150, maxTimeMs = 1200),
            // Level 1: Impulsive beginner
            TimingConfig(baseTimeMs = 800, perOptionMs = 40, criticalBonusMs = 200, varianceMs = 350, maxTimeMs = 2500),
            // Level 2: Casual player
            TimingConfig(baseTimeMs = 900, perOptionMs = 55, criticalBonusMs = 300, varianceMs = 400, maxTimeMs = 3000),
            // Level 3: Learning player - starts thinking more
            TimingConfig(baseTimeMs = 1000, perOptionMs = 70, criticalBonusMs = 400, varianceMs = 500, maxTimeMs = 3500),
            // Level 4: Intermediate - deliberate
            TimingConfig(baseTimeMs = 1100, perOptionMs = 85, criticalBonusMs = 500, varianceMs = 600, maxTimeMs = 4000),
            // Level 5: Solid player
            TimingConfig(baseTimeMs = 1200, perOptionMs = 95, criticalBonusMs = 600, varianceMs = 650, maxTimeMs = 4500),
            // Level 6: Advanced - more variable
            TimingConfig(baseTimeMs = 1000, perOptionMs = 110, criticalBonusMs = 800, varianceMs = 750, maxTimeMs = 5000),
            // Level 7: Strong - fast on obvious, slow on complex
            TimingConfig(baseTimeMs = 800, perOptionMs = 130, criticalBonusMs = 1000, varianceMs = 850, maxTimeMs = 5500),
            // Level 8: Expert - highly variable
            TimingConfig(baseTimeMs = 700, perOptionMs = 145, criticalBonusMs = 1200, varianceMs = 950, maxTimeMs = 6000),
            // Level 9: Master - instant on forced, very long on critical
            TimingConfig(baseTimeMs = 500, perOptionMs = 160, criticalBonusMs = 1500, varianceMs = 1100, maxTimeMs = 6500)
        )

        private val POSITIONAL_WEIGHTS = arrayOf(
            intArrayOf(100, -20,  10,   5,   5,  10, -20, 100),
            intArrayOf(-20, -50,  -2,  -2,  -2,  -2, -50, -20),
            intArrayOf( 10,  -2,   1,   1,   1,   1,  -2,  10),
            intArrayOf(  5,  -2,   1,   0,   0,   1,  -2,   5),
            intArrayOf(  5,  -2,   1,   0,   0,   1,  -2,   5),
            intArrayOf( 10,  -2,   1,   1,   1,   1,  -2,  10),
            intArrayOf(-20, -50,  -2,  -2,  -2,  -2, -50, -20),
            intArrayOf(100, -20,  10,   5,   5,  10, -20, 100)
        )

        private val CORNERS = listOf(
            0 to 0, 0 to 7, 7 to 0, 7 to 7
        )

        private val X_SQUARES = mapOf(
            (1 to 1) to (0 to 0),
            (1 to 6) to (0 to 7),
            (6 to 1) to (7 to 0),
            (6 to 6) to (7 to 7)
        )
    }

    private val config: LevelConfig
        get() = LEVEL_CONFIGS[level.coerceIn(0, 9)]

    private val timingConfig: TimingConfig
        get() = TIMING_CONFIGS[level.coerceIn(0, 9)]

    // ==================== MOVE SELECTION ====================

    override fun selectMove(state: ReversiGameState): ReversiMove? {
        val validMoves = ReversiRules.getValidMoves(state.board, state.currentTurn)
        if (validMoves.isEmpty()) return null
        if (validMoves.size == 1) return validMoves.first()

        val cfg = config
        val shuffledMoves = validMoves.shuffled(Random)

        // Score each move
        val scoredMoves = if (cfg.depth == 0) {
            // No lookahead: just evaluate the resulting board
            shuffledMoves.map { move ->
                val newBoard = ReversiRules.applyMove(state.board, move)
                val score = evaluate(newBoard, state.currentTurn, cfg.evalMode)
                move to score
            }
        } else {
            // Minimax with alpha-beta pruning
            shuffledMoves.map { move ->
                val newBoard = ReversiRules.applyMove(state.board, move)
                val score = minimax(
                    board = newBoard,
                    depth = cfg.depth - 1,
                    alpha = Double.NEGATIVE_INFINITY,
                    beta = Double.POSITIVE_INFINITY,
                    isMaximizing = false,
                    currentColor = state.currentTurn.opposite(),
                    myColor = state.currentTurn,
                    evalMode = cfg.evalMode
                )
                move to score
            }
        }

        val sortedMoves = scoredMoves.sortedByDescending { it.second }

        val bestScore = sortedMoves.first().second
        val worstScore = sortedMoves.last().second

        // Apply error rate: sometimes pick a suboptimal move
        if (cfg.errorRate > 0.0 && Random.nextDouble() < cfg.errorRate) {
            val chosen = pickSuboptimalMove(sortedMoves, cfg.mistakeQuality)
            val chosenScore = scoredMoves.first { it.first == chosen }.second
            val rank = sortedMoves.indexOfFirst { it.first == chosen } + 1
            Log.d(TAG, "AI L$level | MISTAKE | eval=${cfg.evalMode} depth=${cfg.depth} | " +
                "move=${chosen.position.row},${chosen.position.col} rank=$rank/${sortedMoves.size} " +
                "score=${"%.1f".format(chosenScore)} (best=${"%.1f".format(bestScore)} worst=${"%.1f".format(worstScore)})")
            return chosen
        }

        val best = sortedMoves.first().first
        Log.d(TAG, "AI L$level | BEST | eval=${cfg.evalMode} depth=${cfg.depth} | " +
            "move=${best.position.row},${best.position.col} rank=1/${sortedMoves.size} " +
            "score=${"%.1f".format(bestScore)} (worst=${"%.1f".format(worstScore)})")
        return best
    }

    // ==================== HUMAN-LIKE TIMING ====================

    /**
     * Calculate human-like thinking time based on position complexity and AI level.
     * Used in tournament mode to simulate realistic opponent behavior.
     *
     * Factors considered:
     * - Number of valid moves (more options = more thinking)
     * - Critical positions (corners available = extra deliberation)
     * - Game phase (opening = faster, midgame = slower, endgame = medium)
     * - Level personality (beginners are impulsive, experts are strategic)
     * - Natural variance (no human thinks the exact same time twice)
     */
    fun calculateThinkingTimeMs(state: ReversiGameState): Long {
        val timing = timingConfig
        val validMoves = ReversiRules.getValidMoves(state.board, state.currentTurn)
        val numMoves = validMoves.size

        // Forced move: minimal thinking (but never instant)
        if (numMoves <= 1) {
            return (timing.baseTimeMs * 0.5 + triangularNoise(timing.varianceMs / 3))
                .toLong().coerceIn(300, timing.maxTimeMs)
        }

        var time = timing.baseTimeMs.toDouble()

        // More options = more thinking time
        time += timing.perOptionMs * numMoves

        // Critical position bonus: corners available demand careful thought
        val hasCornerMove = validMoves.any { move ->
            val p = move.position
            (p.row == 0 || p.row == 7) && (p.col == 0 || p.col == 7)
        }
        if (hasCornerMove) {
            time += timing.criticalBonusMs
        }

        // Game phase adjustment
        val totalDiscs = state.board.countTotal()
        val phaseMultiplier = when {
            totalDiscs < 15 -> 0.8   // Opening: faster, familiar patterns
            totalDiscs > 50 -> 0.85  // Endgame: fewer options, more counting
            else -> 1.1              // Midgame: complex, more thinking
        }
        time *= phaseMultiplier

        // Triangular noise for natural feel (bell-shaped distribution)
        time += triangularNoise(timing.varianceMs)

        val phase = when {
            totalDiscs < 15 -> "opening"
            totalDiscs > 50 -> "endgame"
            else -> "midgame"
        }
        val result = time.toLong().coerceIn(300, timing.maxTimeMs)
        Log.d(TAG, "AI L$level | TIMING | ${result}ms | options=$numMoves phase=$phase corner=$hasCornerMove discs=$totalDiscs")
        return result
    }

    /**
     * Triangular noise: average of two uniform randoms.
     * Produces a bell-shaped distribution centered at 0, more natural than uniform.
     */
    private fun triangularNoise(range: Long): Double {
        val a = Random.nextDouble(-range.toDouble(), range.toDouble())
        val b = Random.nextDouble(-range.toDouble(), range.toDouble())
        return (a + b) / 2.0
    }

    // ==================== SUBOPTIMAL MOVE PICKING ====================

    private fun pickSuboptimalMove(
        sortedMoves: List<Pair<ReversiMove, Double>>,
        mistakeQuality: Double
    ): ReversiMove {
        if (sortedMoves.size <= 1) return sortedMoves.first().first

        val size = sortedMoves.size
        val weights = DoubleArray(size) { index ->
            val normalizedPosition = index.toDouble() / (size - 1).toDouble()

            if (index == 0) {
                // Never pick the best move when making a mistake
                0.0
            } else {
                // mistakeQuality 0.0 -> target near end of list (worst moves)
                // mistakeQuality 1.0 -> target near start of list (almost-best moves)
                val targetPosition = 1.0 - mistakeQuality
                val distance = abs(normalizedPosition - targetPosition)
                exp(-distance * distance * 4.0)
            }
        }

        val totalWeight = weights.sum()
        if (totalWeight <= 0.0) {
            return sortedMoves[Random.nextInt(1, size)].first
        }

        var roll = Random.nextDouble() * totalWeight
        for (i in weights.indices) {
            roll -= weights[i]
            if (roll <= 0.0) {
                return sortedMoves[i].first
            }
        }

        return sortedMoves.last().first
    }

    // ==================== MINIMAX ====================

    private fun minimax(
        board: ReversiBoard,
        depth: Int,
        alpha: Double,
        beta: Double,
        isMaximizing: Boolean,
        currentColor: ReversiColor,
        myColor: ReversiColor,
        evalMode: EvalMode
    ): Double {
        if (depth == 0 || ReversiRules.isGameOver(board)) {
            return evaluate(board, myColor, evalMode)
        }

        val validMoves = ReversiRules.getValidMoves(board, currentColor)

        if (validMoves.isEmpty()) {
            val opponentColor = currentColor.opposite()
            return if (ReversiRules.hasValidMoves(board, opponentColor)) {
                minimax(board, depth - 1, alpha, beta, !isMaximizing, opponentColor, myColor, evalMode)
            } else {
                evaluate(board, myColor, evalMode)
            }
        }

        var currentAlpha = alpha
        var currentBeta = beta

        if (isMaximizing) {
            var maxEval = Double.NEGATIVE_INFINITY
            for (move in validMoves) {
                val newBoard = ReversiRules.applyMove(board, move)
                val eval = minimax(newBoard, depth - 1, currentAlpha, currentBeta, false, currentColor.opposite(), myColor, evalMode)
                maxEval = max(maxEval, eval)
                currentAlpha = max(currentAlpha, eval)
                if (currentBeta <= currentAlpha) break
            }
            return maxEval
        } else {
            var minEval = Double.POSITIVE_INFINITY
            for (move in validMoves) {
                val newBoard = ReversiRules.applyMove(board, move)
                val eval = minimax(newBoard, depth - 1, currentAlpha, currentBeta, true, currentColor.opposite(), myColor, evalMode)
                minEval = min(minEval, eval)
                currentBeta = min(currentBeta, eval)
                if (currentBeta <= currentAlpha) break
            }
            return minEval
        }
    }

    // ==================== EVALUATION FUNCTIONS ====================

    private fun evaluate(board: ReversiBoard, myColor: ReversiColor, mode: EvalMode): Double {
        return when (mode) {
            EvalMode.GREEDY -> evaluateGreedy(board, myColor)
            EvalMode.BASIC -> evaluateBasic(board, myColor)
            EvalMode.STANDARD -> evaluateStandard(board, myColor)
            EvalMode.FULL -> evaluateFull(board, myColor)
        }
    }

    /**
     * GREEDY: Like a beginner - just wants more pieces and grabs corners.
     * This is the "capture as many as possible" mentality that new players have.
     */
    private fun evaluateGreedy(board: ReversiBoard, myColor: ReversiColor): Double {
        val oppColor = myColor.opposite()
        val myDiscs = board.countPieces(myColor)
        val oppDiscs = board.countPieces(oppColor)

        // Disc count is the beginner's main metric
        var score = (myDiscs - oppDiscs).toDouble() * 10.0

        // Even beginners learn that corners are good
        for ((row, col) in CORNERS) {
            val piece = board.getPiece(row, col)
            when {
                piece?.color == myColor -> score += 50.0
                piece?.color == oppColor -> score -= 50.0
            }
        }

        return score
    }

    /**
     * BASIC: Improving player - knows about corners, avoids X-squares,
     * has some edge awareness. Still values capture count highly.
     */
    private fun evaluateBasic(board: ReversiBoard, myColor: ReversiColor): Double {
        val oppColor = myColor.opposite()
        val myDiscs = board.countPieces(myColor)
        val oppDiscs = board.countPieces(oppColor)

        var score = (myDiscs - oppDiscs).toDouble() * 5.0

        // Corner control
        for ((row, col) in CORNERS) {
            val piece = board.getPiece(row, col)
            when {
                piece?.color == myColor -> score += 40.0
                piece?.color == oppColor -> score -= 40.0
            }
        }

        // X-square penalty (avoids giving away corners)
        for ((xSquare, corner) in X_SQUARES) {
            val cornerPiece = board.getPiece(corner.first, corner.second)
            if (cornerPiece == null) {
                val xPiece = board.getPiece(xSquare.first, xSquare.second)
                when {
                    xPiece?.color == myColor -> score -= 15.0
                    xPiece?.color == oppColor -> score += 15.0
                }
            }
        }

        // Simple edge awareness (edges are generally good)
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                if (row == 0 || row == 7 || col == 0 || col == 7) {
                    val piece = board.getPiece(row, col)
                    when {
                        piece?.color == myColor -> score += 3.0
                        piece?.color == oppColor -> score -= 3.0
                    }
                }
            }
        }

        return score
    }

    /**
     * STANDARD: Solid player - understands mobility, uses positional weight table,
     * and adjusts strategy by game phase. Doesn't yet consider stability.
     */
    private fun evaluateStandard(board: ReversiBoard, myColor: ReversiColor): Double {
        val oppColor = myColor.opposite()
        val myDiscs = board.countPieces(myColor)
        val oppDiscs = board.countPieces(oppColor)
        val totalDiscs = myDiscs + oppDiscs

        val discParity = if (myDiscs + oppDiscs > 0) {
            100.0 * (myDiscs - oppDiscs) / (myDiscs + oppDiscs)
        } else 0.0

        var cornerScore = 0.0
        for ((row, col) in CORNERS) {
            val piece = board.getPiece(row, col)
            when {
                piece?.color == myColor -> cornerScore += 25.0
                piece?.color == oppColor -> cornerScore -= 25.0
            }
        }

        for ((xSquare, corner) in X_SQUARES) {
            val cornerPiece = board.getPiece(corner.first, corner.second)
            if (cornerPiece == null) {
                val xPiece = board.getPiece(xSquare.first, xSquare.second)
                when {
                    xPiece?.color == myColor -> cornerScore -= 12.0
                    xPiece?.color == oppColor -> cornerScore += 12.0
                }
            }
        }

        val myMoves = ReversiRules.getValidMoves(board, myColor).size
        val oppMoves = ReversiRules.getValidMoves(board, oppColor).size
        val mobility = if (myMoves + oppMoves > 0) {
            100.0 * (myMoves - oppMoves) / (myMoves + oppMoves)
        } else 0.0

        var positional = 0.0
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val piece = board.getPiece(row, col)
                when {
                    piece?.color == myColor -> positional += POSITIONAL_WEIGHTS[row][col]
                    piece?.color == oppColor -> positional -= POSITIONAL_WEIGHTS[row][col]
                }
            }
        }

        return when {
            totalDiscs < 20 -> discParity * 0.5 + cornerScore * 10.0 + mobility * 5.0 + positional * 2.0
            totalDiscs <= 50 -> discParity * 1.0 + cornerScore * 8.0 + mobility * 3.0 + positional * 1.0
            else -> discParity * 5.0 + cornerScore * 8.0
        }
    }

    /**
     * FULL: Expert evaluation with all factors including disc stability.
     * Used by the strongest levels.
     */
    private fun evaluateFull(board: ReversiBoard, myColor: ReversiColor): Double {
        val oppColor = myColor.opposite()
        val myDiscs = board.countPieces(myColor)
        val oppDiscs = board.countPieces(oppColor)
        val totalDiscs = myDiscs + oppDiscs

        val discParity = if (myDiscs + oppDiscs > 0) {
            100.0 * (myDiscs - oppDiscs) / (myDiscs + oppDiscs)
        } else 0.0

        var cornerScore = 0.0
        for ((row, col) in CORNERS) {
            val piece = board.getPiece(row, col)
            when {
                piece?.color == myColor -> cornerScore += 25.0
                piece?.color == oppColor -> cornerScore -= 25.0
            }
        }

        for ((xSquare, corner) in X_SQUARES) {
            val cornerPiece = board.getPiece(corner.first, corner.second)
            if (cornerPiece == null) {
                val xPiece = board.getPiece(xSquare.first, xSquare.second)
                when {
                    xPiece?.color == myColor -> cornerScore -= 12.0
                    xPiece?.color == oppColor -> cornerScore += 12.0
                }
            }
        }

        val myMoves = ReversiRules.getValidMoves(board, myColor).size
        val oppMoves = ReversiRules.getValidMoves(board, oppColor).size
        val mobility = if (myMoves + oppMoves > 0) {
            100.0 * (myMoves - oppMoves) / (myMoves + oppMoves)
        } else 0.0

        val stability = calculateStability(board, myColor, oppColor)

        var positional = 0.0
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val piece = board.getPiece(row, col)
                when {
                    piece?.color == myColor -> positional += POSITIONAL_WEIGHTS[row][col]
                    piece?.color == oppColor -> positional -= POSITIONAL_WEIGHTS[row][col]
                }
            }
        }

        return when {
            totalDiscs < 20 -> discParity * 0.5 + cornerScore * 10.0 + mobility * 5.0 + positional * 2.0
            totalDiscs <= 50 -> discParity * 1.0 + cornerScore * 8.0 + mobility * 3.0 + stability * 3.0 + positional * 1.0
            else -> discParity * 5.0 + cornerScore * 8.0 + stability * 3.0
        }
    }

    private fun calculateStability(
        board: ReversiBoard,
        myColor: ReversiColor,
        oppColor: ReversiColor
    ): Double {
        var myStable = 0
        var oppStable = 0

        for ((row, col) in CORNERS) {
            val piece = board.getPiece(row, col)
            when {
                piece?.color == myColor -> myStable++
                piece?.color == oppColor -> oppStable++
            }
        }

        val cornerEdges = listOf(
            Triple(0, 0, listOf(0 to 1, 1 to 0)),
            Triple(0, 7, listOf(0 to -1, 1 to 0)),
            Triple(7, 0, listOf(0 to 1, -1 to 0)),
            Triple(7, 7, listOf(0 to -1, -1 to 0))
        )

        for ((cornerRow, cornerCol, directions) in cornerEdges) {
            val cornerPiece = board.getPiece(cornerRow, cornerCol) ?: continue
            val cornerColor = cornerPiece.color

            for ((dr, dc) in directions) {
                var r = cornerRow + dr
                var c = cornerCol + dc
                while (r in 0..7 && c in 0..7) {
                    val piece = board.getPiece(r, c)
                    if (piece?.color != cornerColor) break
                    when (cornerColor) {
                        myColor -> myStable++
                        else -> oppStable++
                    }
                    r += dr
                    c += dc
                }
            }
        }

        return if (myStable + oppStable > 0) {
            100.0 * (myStable - oppStable) / (myStable + oppStable)
        } else 0.0
    }
}
