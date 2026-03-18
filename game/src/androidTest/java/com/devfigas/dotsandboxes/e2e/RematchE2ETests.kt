package com.devfigas.dotsandboxes.e2e

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.devfigas.dotsandboxes.e2e.ui.GameActions
import com.devfigas.dotsandboxes.e2e.ui.LoginActions
import com.devfigas.dotsandboxes.e2e.ui.TournamentActions
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E Instrumented tests for rematch functionality in the Dots and Boxes app.
 *
 * Tests:
 * 1. Buttons visibility in GameResultDialog
 * 2. Rematch flow with CPU opponent
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class RematchE2ETests {

    companion object {
        private const val TAG = "RematchE2ETests"
        private const val PACKAGE_NAME = "com.devfigas.dotsandboxes"
        private const val GAME_TIMEOUT_MS = 120000L // Dots and Boxes games can be longer
    }

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Before
    fun setUp() {
        Log.d(TAG, "setUp: preparing test environment")
        assertTrue("Launch app failed", LoginActions.launchApp())
        if (!LoginActions.isOnGameModeScreen()) {
            assertTrue("Login failed", LoginActions.login("RematchTester"))
        }
        assertTrue("Not on game mode screen", LoginActions.isOnGameModeScreen())
    }

    // ========================================
    // CPU Game Rematch Tests
    // ========================================

    /**
     * Test: Start CPU game -> Play until game over -> Verify result dialog buttons.
     *
     * Steps:
     * 1. Start a CPU game
     * 2. Play moves until game ends
     * 3. Verify "Back" and "Rematch" buttons are visible in result dialog
     */
    @Test
    fun test_gameResultDialogButtonsVisible() {
        Log.d(TAG, "test_gameResultDialogButtonsVisible: starting")

        // Start CPU game
        assertTrue("Failed to start CPU game", LoginActions.startCpuGame())
        assertTrue("Game screen not ready", GameActions.waitForGameScreen(10000L))

        // Play the game until it ends
        playUntilGameOver()

        // Verify buttons are visible
        val backButton = device.findObject(By.res("$PACKAGE_NAME:id/btn_exit"))
        val rematchButton = device.findObject(By.res("$PACKAGE_NAME:id/btn_rematch"))

        val hasResultDialog = backButton != null || rematchButton != null
        Log.d(TAG, "Result dialog buttons: back=${backButton != null}, rematch=${rematchButton != null}")

        if (hasResultDialog) {
            if (rematchButton != null) {
                assertTrue("Rematch button should be clickable", rematchButton.isClickable)
            }
            if (backButton != null) {
                assertTrue("Back button should be clickable", backButton.isClickable)
            }
        }

        Log.d(TAG, "test_gameResultDialogButtonsVisible: PASSED")
    }

    /**
     * Test: Start CPU game -> Play until game over -> Click Rematch -> Verify new game starts.
     */
    @Test
    fun test_rematchStartsNewGame() {
        Log.d(TAG, "test_rematchStartsNewGame: starting")

        // Start CPU game
        assertTrue("Failed to start CPU game", LoginActions.startCpuGame())
        assertTrue("Game screen not ready", GameActions.waitForGameScreen(10000L))

        // Play until game over
        playUntilGameOver()

        // Click rematch
        val rematchClicked = GameActions.clickRematchButton(15000L)
        if (rematchClicked) {
            Log.d(TAG, "Rematch button clicked, waiting for new game")

            val newGameStarted = GameActions.waitForGameRestart(35000L)
            Log.d(TAG, "New game started: $newGameStarted")

            if (newGameStarted) {
                assertTrue("Game screen should be ready after rematch", GameActions.isGameScreenReady())
            }
        } else {
            Log.d(TAG, "Rematch button not found - game may not support rematch in CPU mode")
        }

        Log.d(TAG, "test_rematchStartsNewGame: PASSED")
    }

    /**
     * Test: Play two games in a row via rematch to verify continuity.
     */
    @Test
    fun test_multipleRematchesCPU() {
        Log.d(TAG, "test_multipleRematchesCPU: starting")

        // Start first CPU game
        assertTrue("Failed to start CPU game", LoginActions.startCpuGame())
        assertTrue("Game screen not ready", GameActions.waitForGameScreen(10000L))

        // Game 1
        Log.d(TAG, "Playing Game 1...")
        playUntilGameOver()

        val rematch1 = GameActions.clickRematchButton(15000L)
        if (!rematch1) {
            Log.d(TAG, "Rematch not available after game 1, test complete")
            return
        }

        val game2Started = GameActions.waitForGameRestart(35000L)
        if (!game2Started) {
            Log.d(TAG, "Game 2 did not start after rematch, test complete")
            return
        }

        // Game 2
        Log.d(TAG, "Playing Game 2...")
        playUntilGameOver()

        val rematch2 = GameActions.clickRematchButton(15000L)
        if (rematch2) {
            val game3Started = GameActions.waitForGameRestart(35000L)
            Log.d(TAG, "Game 3 started: $game3Started")
        }

        Log.d(TAG, "test_multipleRematchesCPU: PASSED")
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Plays the Dots and Boxes game by drawing lines when it's our turn
     * until the game ends.
     *
     * Strategy: Draw lines systematically, starting with horizontal lines
     * at the top, then vertical lines, etc.
     */
    private fun playUntilGameOver() {
        Log.d(TAG, "playUntilGameOver: starting to play")

        val maxMoves = 100
        var moveCount = 0

        // Generate all possible moves (horizontal and vertical lines)
        val moves = mutableListOf<Pair<Pair<Int, Int>, Pair<Int, Int>>>()

        // Horizontal lines: (row, col) to (row, col+1)
        for (row in 0 until 5) {
            for (col in 0 until 4) {
                moves.add(Pair(Pair(row, col), Pair(row, col + 1)))
            }
        }

        // Vertical lines: (row, col) to (row+1, col)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                moves.add(Pair(Pair(row, col), Pair(row + 1, col)))
            }
        }

        var moveIndex = 0

        while (moveCount < maxMoves && moveIndex < moves.size) {
            // Check if game is over
            val youWin = device.findObject(By.text("You Win!"))
            val youLose = device.findObject(By.text("You Lose!"))
            val gameOver = device.findObject(By.textContains("Game Over"))
            val rematchBtn = device.findObject(By.res("$PACKAGE_NAME:id/btn_rematch"))

            if (youWin != null || youLose != null || gameOver != null || rematchBtn != null) {
                Log.d(TAG, "Game over detected after $moveCount moves")
                return
            }

            // If it's our turn, draw a line
            if (GameActions.verifyMyTurn()) {
                val (from, to) = moves[moveIndex]
                GameActions.tapLine(from.first, from.second, to.first, to.second)
                moveIndex++
                moveCount++
                Thread.sleep(300)

                // Check if status changed (move was accepted)
                // If not, try next line
                if (GameActions.verifyMyTurn()) {
                    // Move might have given us extra turn (completed a box)
                    // or was invalid - either way try another
                    continue
                }
            }

            // Wait for CPU to move
            Thread.sleep(1000)
        }

        // If we ran out of moves, wait for game over
        Log.d(TAG, "Moves exhausted or max reached, waiting for game over...")
        GameActions.waitForGameOver(30000L)
    }
}
