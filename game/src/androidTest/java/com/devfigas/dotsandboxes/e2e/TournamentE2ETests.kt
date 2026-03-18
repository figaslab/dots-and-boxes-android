package com.devfigas.dotsandboxes.e2e

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.devfigas.dotsandboxes.e2e.ui.LoginActions
import com.devfigas.dotsandboxes.e2e.ui.TournamentActions
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E Instrumented tests for the Tournament system in the Dots and Boxes app.
 *
 * Tests tournament selection, wallet/balance integration, progress tracking,
 * and game flow scenarios. CPU-only (no BT/WiFi).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class TournamentE2ETests {

    companion object {
        private const val TAG = "TournamentE2ETests"
        private const val PACKAGE_NAME = "com.devfigas.dotsandboxes"
        private const val BRONZE_FEE = 10
        private const val BRONZE_PRIZE = 20
        private const val INITIAL_BALANCE = 100
    }

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Before
    fun setUp() {
        Log.d(TAG, "setUp: preparing test environment")
        assertTrue("Launch app failed", LoginActions.launchApp())
        if (!LoginActions.isOnGameModeScreen()) {
            assertTrue("Login failed", LoginActions.login("TournamentTester"))
        }
        assertTrue("Not on game mode screen", LoginActions.isOnGameModeScreen())

        // Reset progress via debug menu
        assertTrue("Failed to open debug menu", TournamentActions.openDebugMenu())
        assertTrue("Failed to reset progress", TournamentActions.resetDebugProgress())
        TournamentActions.pressBack()

        Thread.sleep(1000)
    }

    // ========================================
    // Balance Display Tests
    // ========================================

    /**
     * Verifies that balance is displayed correctly on tournament selection screen.
     */
    @Test
    fun test_balanceDisplayedOnTournamentSelection() {
        Log.d(TAG, "test_balanceDisplayedOnTournamentSelection: starting")

        assertTrue("Failed to navigate to tournaments", TournamentActions.navigateToTournaments())
        assertTrue("Balance not displayed correctly", TournamentActions.verifyBalance(INITIAL_BALANCE))

        Log.d(TAG, "test_balanceDisplayedOnTournamentSelection: PASSED")
    }

    /**
     * Verifies balance updates after debug menu changes.
     */
    @Test
    fun test_balanceUpdatesFromDebugMenu() {
        Log.d(TAG, "test_balanceUpdatesFromDebugMenu: starting")

        assertTrue("Failed to open debug menu", TournamentActions.openDebugMenu())
        assertTrue("Failed to set balance", TournamentActions.setDebugBalance(500))
        TournamentActions.pressBack()

        Thread.sleep(500)
        assertTrue("Failed to navigate to tournaments", TournamentActions.navigateToTournaments())
        assertTrue("Balance should be 500", TournamentActions.verifyBalance(500))

        Log.d(TAG, "test_balanceUpdatesFromDebugMenu: PASSED")
    }

    // ========================================
    // Tournament Unlock Tests
    // ========================================

    /**
     * Verifies Bronze tournament is always unlocked.
     */
    @Test
    fun test_bronzeTournamentAlwaysUnlocked() {
        Log.d(TAG, "test_bronzeTournamentAlwaysUnlocked: starting")

        assertTrue("Failed to navigate to tournaments", TournamentActions.navigateToTournaments())
        assertTrue("Bronze should be unlocked", TournamentActions.verifyTournamentUnlocked("Bronze"))

        Log.d(TAG, "test_bronzeTournamentAlwaysUnlocked: PASSED")
    }

    /**
     * Verifies Silver tournament is locked with 0 victories.
     */
    @Test
    fun test_silverTournamentLockedInitially() {
        Log.d(TAG, "test_silverTournamentLockedInitially: starting")

        assertTrue("Failed to navigate to tournaments", TournamentActions.navigateToTournaments())
        assertTrue("Silver should be locked", TournamentActions.verifyTournamentLocked("Silver"))

        Log.d(TAG, "test_silverTournamentLockedInitially: PASSED")
    }

    /**
     * Verifies Silver tournament unlocks after reaching Bronze goal.
     */
    @Test
    fun test_silverUnlocksAfterBronzeGoal() {
        Log.d(TAG, "test_silverUnlocksAfterBronzeGoal: starting")

        assertTrue("Failed to open debug menu", TournamentActions.openDebugMenu())
        assertTrue("Failed to set victories", TournamentActions.setDebugVictories(10))
        TournamentActions.pressBack()

        Thread.sleep(500)
        assertTrue("Failed to navigate to tournaments", TournamentActions.navigateToTournaments())
        assertTrue("Silver should be unlocked with 10 victories", TournamentActions.verifyTournamentUnlocked("Silver"))

        Log.d(TAG, "test_silverUnlocksAfterBronzeGoal: PASSED")
    }

    // ========================================
    // Progress Display Tests
    // ========================================

    /**
     * Verifies progress displays correctly for Bronze tournament.
     */
    @Test
    fun test_progressDisplaysCorrectly() {
        Log.d(TAG, "test_progressDisplaysCorrectly: starting")

        assertTrue("Failed to open debug menu", TournamentActions.openDebugMenu())
        assertTrue("Failed to set victories", TournamentActions.setDebugVictories(5))
        TournamentActions.pressBack()

        Thread.sleep(500)
        assertTrue("Failed to navigate to tournaments", TournamentActions.navigateToTournaments())
        assertTrue("Progress should show 5/10", TournamentActions.verifyProgress("Bronze", 5, 10))

        Log.d(TAG, "test_progressDisplaysCorrectly: PASSED")
    }

    // ========================================
    // Insufficient Funds Tests
    // ========================================

    /**
     * Verifies that insufficient funds warning appears when balance is 0.
     */
    @Test
    fun test_noFundsWarningWhenBalanceZero() {
        Log.d(TAG, "test_noFundsWarningWhenBalanceZero: starting")

        assertTrue("Failed to open debug menu", TournamentActions.openDebugMenu())
        assertTrue("Failed to set balance", TournamentActions.setDebugBalance(0))
        TournamentActions.pressBack()

        Thread.sleep(500)
        assertTrue("Failed to navigate to tournaments", TournamentActions.navigateToTournaments())
        assertTrue("No funds warning should be visible", TournamentActions.verifyNoFundsWarning(true))

        Log.d(TAG, "test_noFundsWarningWhenBalanceZero: PASSED")
    }

    /**
     * Verifies tournament selection fails when insufficient funds.
     */
    @Test
    fun test_insufficientFundsBlocksTournament() {
        Log.d(TAG, "test_insufficientFundsBlocksTournament: starting")

        assertTrue("Failed to open debug menu", TournamentActions.openDebugMenu())
        assertTrue("Failed to set balance", TournamentActions.setDebugBalance(5))
        TournamentActions.pressBack()

        Thread.sleep(500)
        assertTrue("Failed to navigate to tournaments", TournamentActions.navigateToTournaments())

        val result = TournamentActions.selectTournament("Bronze")
        assertFalse("Should not be able to enter tournament with insufficient funds", result)

        TournamentActions.dismissInsufficientFundsDialog()

        Log.d(TAG, "test_insufficientFundsBlocksTournament: PASSED")
    }

    // ========================================
    // Debug Menu Tests
    // ========================================

    /**
     * Verifies reset progress clears victories and balance.
     */
    @Test
    fun test_resetProgressClearsAll() {
        Log.d(TAG, "test_resetProgressClearsAll: starting")

        assertTrue("Failed to open debug menu", TournamentActions.openDebugMenu())
        assertTrue("Failed to set victories", TournamentActions.setDebugVictories(50))
        assertTrue("Failed to set balance", TournamentActions.setDebugBalance(500))
        TournamentActions.pressBack()

        Thread.sleep(500)
        assertTrue("Failed to open debug menu again", TournamentActions.openDebugMenu())
        assertTrue("Failed to reset progress", TournamentActions.resetDebugProgress())
        TournamentActions.pressBack()

        Thread.sleep(500)
        assertTrue("Failed to navigate to tournaments", TournamentActions.navigateToTournaments())
        assertTrue("Balance should be reset to initial", TournamentActions.verifyBalance(INITIAL_BALANCE))
        assertTrue("Progress should show 0/10", TournamentActions.verifyProgress("Bronze", 0, 10))

        Log.d(TAG, "test_resetProgressClearsAll: PASSED")
    }
}
