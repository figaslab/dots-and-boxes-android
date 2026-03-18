package com.devfigas.dotsandboxes.e2e.ui

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice

/**
 * UI Automation actions for the Dots and Boxes game board.
 * Handles line drawing between dots and board state verification.
 *
 * Board layout: 5x5 dots (4x4 boxes)
 * Players tap between two adjacent dots to draw a line (horizontal or vertical).
 * When a player completes a box (all 4 sides drawn), they score a point and get an extra turn.
 * The player with the most boxes at the end wins.
 */
object GameActions {
    private const val TAG = "GameActions"
    private const val TIMEOUT_MS = 5000L
    private const val PACKAGE_NAME = "com.devfigas.dotsandboxes"

    /** Number of dot rows */
    private const val DOT_ROWS = 5

    /** Number of dot columns */
    private const val DOT_COLS = 5

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    /**
     * Taps between two adjacent dots to draw a line.
     *
     * The dots are arranged in a grid:
     *   (0,0) (0,1) (0,2) (0,3) (0,4)
     *   (1,0) (1,1) (1,2) (1,3) (1,4)
     *   (2,0) (2,1) (2,2) (2,3) (2,4)
     *   (3,0) (3,1) (3,2) (3,3) (3,4)
     *   (4,0) (4,1) (4,2) (4,3) (4,4)
     *
     * To draw a horizontal line between (row, col) and (row, col+1), pass those coordinates.
     * To draw a vertical line between (row, col) and (row+1, col), pass those coordinates.
     *
     * @param row1 Row of the first dot (0-based)
     * @param col1 Column of the first dot (0-based)
     * @param row2 Row of the second dot (0-based)
     * @param col2 Column of the second dot (0-based)
     * @return true if the tap was performed successfully
     */
    fun tapLine(row1: Int, col1: Int, row2: Int, col2: Int): Boolean {
        Log.d(TAG, "tapLine: drawing line from ($row1,$col1) to ($row2,$col2)")

        try {
            val boardView = device.findObject(By.res("$PACKAGE_NAME:id/dots_and_boxes_board_view"))
            if (boardView == null) {
                Log.e(TAG, "Dots and Boxes board view not found")
                return false
            }

            val bounds = boardView.visibleBounds
            val boardWidth = bounds.width()
            val boardHeight = bounds.height()

            // Calculate dot spacing
            val dotSpacingX = boardWidth.toFloat() / (DOT_COLS - 1)
            val dotSpacingY = boardHeight.toFloat() / (DOT_ROWS - 1)

            // Calculate dot positions
            val dot1x = bounds.left + col1 * dotSpacingX
            val dot1y = bounds.top + row1 * dotSpacingY
            val dot2x = bounds.left + col2 * dotSpacingX
            val dot2y = bounds.top + row2 * dotSpacingY

            // Tap at the midpoint between the two dots
            val tapX = ((dot1x + dot2x) / 2).toInt()
            val tapY = ((dot1y + dot2y) / 2).toInt()

            Log.d(TAG, "Dot1: ($dot1x, $dot1y), Dot2: ($dot2x, $dot2y), Tap: ($tapX, $tapY)")
            device.click(tapX, tapY)
            Thread.sleep(500)

            Log.d(TAG, "Line drawn successfully from ($row1,$col1) to ($row2,$col2)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing line: ${e.message}", e)
            return false
        }
    }

    /**
     * Draws a horizontal line between (row, col) and (row, col+1).
     * Convenience method for horizontal lines.
     *
     * @param row The row of the dots
     * @param col The left column (line goes from col to col+1)
     * @return true if the tap was performed successfully
     */
    fun tapHorizontalLine(row: Int, col: Int): Boolean {
        return tapLine(row, col, row, col + 1)
    }

    /**
     * Draws a vertical line between (row, col) and (row+1, col).
     * Convenience method for vertical lines.
     *
     * @param row The top row (line goes from row to row+1)
     * @param col The column of the dots
     * @return true if the tap was performed successfully
     */
    fun tapVerticalLine(row: Int, col: Int): Boolean {
        return tapLine(row, col, row + 1, col)
    }

    /**
     * Verifies that the game screen is visible and ready.
     */
    fun isGameScreenReady(): Boolean {
        Log.d(TAG, "isGameScreenReady: checking...")

        try {
            val boardView = device.findObject(By.res("$PACKAGE_NAME:id/dots_and_boxes_board_view"))
            val statusView = device.findObject(By.res("$PACKAGE_NAME:id/tv_game_status"))

            val ready = boardView != null && statusView != null
            Log.d(TAG, "Game screen ready: $ready")
            return ready
        } catch (e: Exception) {
            Log.e(TAG, "Error checking game screen: ${e.message}", e)
            return false
        }
    }

    /**
     * Waits for the game screen to be ready.
     */
    fun waitForGameScreen(timeoutMs: Long = 10000L): Boolean {
        Log.d(TAG, "waitForGameScreen: waiting...")

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (isGameScreenReady()) {
                return true
            }
            Thread.sleep(500)
        }

        Log.e(TAG, "Timeout waiting for game screen")
        return false
    }

    /**
     * Verifies that it's the player's turn by checking the game status text.
     */
    fun verifyMyTurn(): Boolean {
        Log.d(TAG, "verifyMyTurn: checking...")

        try {
            val statusText = device.findObject(By.res("$PACKAGE_NAME:id/tv_game_status"))
            if (statusText == null) {
                Log.e(TAG, "Game status text not found")
                return false
            }

            val text = statusText.text?.lowercase() ?: ""
            Log.d(TAG, "Current status text: $text")

            val isMyTurn = text.contains("your") && text.contains("turn")
            Log.d(TAG, "Is my turn: $isMyTurn")
            return isMyTurn
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying turn: ${e.message}", e)
            return false
        }
    }

    /**
     * Waits until it's the player's turn.
     */
    fun waitForMyTurn(timeoutMs: Long = 30000L): Boolean {
        Log.d(TAG, "waitForMyTurn: waiting...")

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (verifyMyTurn()) {
                Log.d(TAG, "It's now my turn")
                return true
            }
            Thread.sleep(500)
        }

        Log.e(TAG, "Timeout waiting for my turn")
        return false
    }

    /**
     * Waits for game over dialog to appear.
     */
    fun waitForGameOver(timeoutMs: Long = 60000L): Boolean {
        Log.d(TAG, "waitForGameOver: waiting... (timeout=${timeoutMs}ms)")

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                val youWin = device.findObject(By.text("You Win!"))
                val youLose = device.findObject(By.text("You Lose!"))
                val gameOver = device.findObject(By.textContains("Game Over"))

                if (youWin != null || youLose != null || gameOver != null) {
                    Log.d(TAG, "Game over detected: win=${youWin != null}, lose=${youLose != null}, gameOver=${gameOver != null}")
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking game over: ${e.message}")
            }

            Thread.sleep(500)
        }

        Log.e(TAG, "Timeout waiting for game over dialog")
        return false
    }

    /**
     * Clicks the rematch button in the game result dialog.
     */
    fun clickRematchButton(timeoutMs: Long = 15000L): Boolean {
        Log.d(TAG, "clickRematchButton: looking for rematch button")

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                val rematchBtn = device.findObject(By.res("$PACKAGE_NAME:id/btn_rematch"))
                    ?: device.findObject(By.text("Rematch"))
                    ?: device.findObject(By.textContains("REMATCH"))

                if (rematchBtn != null) {
                    Log.d(TAG, "Rematch button found, clicking...")
                    rematchBtn.click()
                    Thread.sleep(500)
                    Log.d(TAG, "Rematch button clicked")
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error looking for rematch button: ${e.message}")
            }
            Thread.sleep(500)
        }

        Log.e(TAG, "Rematch button not found within timeout")
        return false
    }

    /**
     * Waits for the game to restart after rematch.
     */
    fun waitForGameRestart(timeoutMs: Long = 35000L): Boolean {
        Log.d(TAG, "waitForGameRestart: waiting for new game to start")

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                val youWin = device.findObject(By.text("You Win!"))
                val youLose = device.findObject(By.text("You Lose!"))
                val rematchBtn = device.findObject(By.res("$PACKAGE_NAME:id/btn_rematch"))

                val boardView = device.findObject(By.res("$PACKAGE_NAME:id/dots_and_boxes_board_view"))
                val statusView = device.findObject(By.res("$PACKAGE_NAME:id/tv_game_status"))

                val dialogsGone = youWin == null && youLose == null && rematchBtn == null
                val gameReady = boardView != null && statusView != null

                if (dialogsGone && gameReady) {
                    val statusText = statusView.text?.lowercase() ?: ""
                    if (statusText.contains("turn")) {
                        Log.d(TAG, "New game started successfully! Status: ${statusView.text}")
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for game restart: ${e.message}")
            }
            Thread.sleep(500)
        }

        Log.e(TAG, "Timeout waiting for game to restart")
        return false
    }

    /**
     * Clicks the Leave Game button to exit the current game.
     */
    fun clickLeaveGame(timeoutMs: Long = 5000L): Boolean {
        Log.d(TAG, "clickLeaveGame: pressing back to open leave game dialog")

        try {
            device.pressBack()
            Thread.sleep(1000)

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                var leaveBtn = device.findObject(By.res("$PACKAGE_NAME:id/btn_leave"))
                if (leaveBtn == null) {
                    leaveBtn = device.findObject(By.text("Leave"))
                }

                if (leaveBtn != null) {
                    Log.d(TAG, "Leave button found in dialog, clicking...")
                    leaveBtn.click()
                    Thread.sleep(1000)
                    Log.d(TAG, "Leave button clicked successfully")
                    return true
                }

                Thread.sleep(300)
            }

            Log.e(TAG, "Leave button not found in dialog within timeout")
            return false

        } catch (e: Exception) {
            Log.e(TAG, "Error during leave game: ${e.message}", e)
            return false
        }
    }

    /**
     * Presses the device back button.
     */
    fun pressBack(): Boolean {
        Log.d(TAG, "pressBack: pressing back button from game")
        try {
            device.pressBack()
            Thread.sleep(500)
            Log.d(TAG, "pressBack: SUCCESS")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error pressing back: ${e.message}", e)
            return false
        }
    }

    /**
     * Logs the current board state for debugging.
     */
    fun logBoardState() {
        Log.d(TAG, "=== Board State ===")
        try {
            val statusText = device.findObject(By.res("$PACKAGE_NAME:id/tv_game_status"))
            Log.d(TAG, "Status: ${statusText?.text ?: "not found"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging board state: ${e.message}")
        }
        Log.d(TAG, "===================")
    }
}
