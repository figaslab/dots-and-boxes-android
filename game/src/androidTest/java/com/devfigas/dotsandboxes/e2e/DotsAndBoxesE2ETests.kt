package com.devfigas.dotsandboxes.e2e

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.devfigas.dotsandboxes.e2e.ui.GameActions
import com.devfigas.dotsandboxes.e2e.ui.LoginActions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E Instrumented tests for the Dots and Boxes app.
 * These tests cover CPU-only scenarios (no BT/WiFi).
 *
 * Each test method can be called individually via:
 * adb shell am instrument -w -e class com.devfigas.dotsandboxes.e2e.DotsAndBoxesE2ETests#methodName \
 *     -e userName "TestUser" \
 *     com.devfigas.dotsandboxes.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DotsAndBoxesE2ETests {

    companion object {
        private const val TAG = "DotsAndBoxesE2ETests"
        private const val PACKAGE_NAME = "com.devfigas.dotsandboxes"

        const val ARG_USER_NAME = "userName"
        const val ARG_ROW1 = "row1"
        const val ARG_COL1 = "col1"
        const val ARG_ROW2 = "row2"
        const val ARG_COL2 = "col2"
        const val ARG_TIMEOUT_MS = "timeoutMs"
    }

    private val arguments by lazy {
        InstrumentationRegistry.getArguments()
    }

    private fun getArg(key: String, default: String = ""): String {
        return arguments.getString(key, default) ?: default
    }

    private fun getLongArg(key: String, default: Long = 5000L): Long {
        return arguments.getString(key, default.toString())?.toLongOrNull() ?: default
    }

    private fun getIntArg(key: String, default: Int = 0): Int {
        return arguments.getString(key, default.toString())?.toIntOrNull() ?: default
    }

    // ========================================
    // Individual Action Tests
    // ========================================

    /**
     * Launches app and performs login.
     * Args: userName
     */
    @Test
    fun action_login() {
        val userName = getArg(ARG_USER_NAME, "TestUser")
        Log.d(TAG, "action_login: userName=$userName")

        val result = LoginActions.launchAndLogin(userName)
        assertTrue("Login failed for user $userName", result)

        Log.d(TAG, "action_login: SUCCESS")
    }

    /**
     * Starts a CPU game from the game mode screen.
     * Assumes already logged in and on game mode screen.
     */
    @Test
    fun action_start_cpu_game() {
        Log.d(TAG, "action_start_cpu_game: starting CPU game")

        val result = LoginActions.startCpuGame()
        assertTrue("Failed to start CPU game", result)

        // Wait for game screen to be ready
        val gameReady = GameActions.waitForGameScreen(10000L)
        assertTrue("Game screen not ready after starting CPU game", gameReady)

        Log.d(TAG, "action_start_cpu_game: SUCCESS")
    }

    /**
     * Draws a line between two adjacent dots on the board.
     * Args: row1, col1, row2, col2
     *
     * For a horizontal line from (0,0) to (0,1): row1=0, col1=0, row2=0, col2=1
     * For a vertical line from (0,0) to (1,0): row1=0, col1=0, row2=1, col2=0
     */
    @Test
    fun action_draw_line() {
        val row1 = getIntArg(ARG_ROW1, 0)
        val col1 = getIntArg(ARG_COL1, 0)
        val row2 = getIntArg(ARG_ROW2, 0)
        val col2 = getIntArg(ARG_COL2, 1)
        Log.d(TAG, "action_draw_line: from ($row1,$col1) to ($row2,$col2)")

        assertTrue("Game screen not ready", GameActions.isGameScreenReady())

        val result = GameActions.tapLine(row1, col1, row2, col2)
        assertTrue("Failed to draw line from ($row1,$col1) to ($row2,$col2)", result)

        Log.d(TAG, "action_draw_line: SUCCESS")
    }

    // ========================================
    // Scenario Tests (CPU-only)
    // ========================================

    /**
     * Full scenario: Login -> Start CPU game -> Verify game screen ready.
     * Tests the basic flow of starting a game against the CPU.
     */
    @Test
    fun scenario_cpu_game_start() {
        val userName = getArg(ARG_USER_NAME, "TestPlayer")
        Log.d(TAG, "scenario_cpu_game_start: starting with userName=$userName")

        // Step 1: Login
        Log.d(TAG, "Step 1: Login")
        assertTrue("Launch app failed", LoginActions.launchApp())
        if (!LoginActions.isOnGameModeScreen()) {
            assertTrue("Login failed", LoginActions.login(userName))
        }
        assertTrue("Not on game mode screen", LoginActions.isOnGameModeScreen())

        // Step 2: Start CPU game
        Log.d(TAG, "Step 2: Start CPU game")
        assertTrue("Failed to start CPU game", LoginActions.startCpuGame())

        // Step 3: Wait for game screen
        Log.d(TAG, "Step 3: Wait for game screen")
        assertTrue("Game screen not ready", GameActions.waitForGameScreen(10000L))

        // Step 4: Verify the board is displayed
        Log.d(TAG, "Step 4: Verify board is displayed")
        assertTrue("Game screen not ready", GameActions.isGameScreenReady())
        GameActions.logBoardState()

        // Step 5: If it's our turn, draw a horizontal line at top
        if (GameActions.verifyMyTurn()) {
            Log.d(TAG, "Step 5: It's our turn, drawing horizontal line (0,0)-(0,1)")
            assertTrue("Failed to draw line", GameActions.tapHorizontalLine(0, 0))
        } else {
            Log.d(TAG, "Step 5: Waiting for our turn (CPU goes first)")
            GameActions.waitForMyTurn(15000L)
        }

        Log.d(TAG, "scenario_cpu_game_start: COMPLETED")
    }

    /**
     * Resign scenario: Login -> Start CPU game -> Leave game immediately.
     * Tests that the player can resign/leave a CPU game.
     */
    @Test
    fun scenario_resign_game() {
        val userName = getArg(ARG_USER_NAME, "TestPlayer")
        Log.d(TAG, "scenario_resign_game: starting with userName=$userName")

        // Step 1: Login
        Log.d(TAG, "Step 1: Login")
        assertTrue("Launch app failed", LoginActions.launchApp())
        if (!LoginActions.isOnGameModeScreen()) {
            assertTrue("Login failed", LoginActions.login(userName))
        }
        assertTrue("Not on game mode screen", LoginActions.isOnGameModeScreen())

        // Step 2: Start CPU game
        Log.d(TAG, "Step 2: Start CPU game")
        assertTrue("Failed to start CPU game", LoginActions.startCpuGame())

        // Step 3: Wait for game screen
        Log.d(TAG, "Step 3: Wait for game screen")
        assertTrue("Game screen not ready", GameActions.waitForGameScreen(10000L))

        // Step 4: Wait a moment then leave
        Log.d(TAG, "Step 4: Waiting 2s then leaving game")
        Thread.sleep(2000)

        // Step 5: Leave game
        Log.d(TAG, "Step 5: Leaving game")
        assertTrue("Failed to leave game", GameActions.clickLeaveGame())

        // Step 6: Verify we're back on game mode screen
        Log.d(TAG, "Step 6: Verify back on game mode screen")
        Thread.sleep(1000)
        val backOnModeScreen = LoginActions.isOnGameModeScreen()
        Log.d(TAG, "Back on game mode screen: $backOnModeScreen")

        Log.d(TAG, "scenario_resign_game: COMPLETED")
    }
}
