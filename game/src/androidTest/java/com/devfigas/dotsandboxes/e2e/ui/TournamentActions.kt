package com.devfigas.dotsandboxes.e2e.ui

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice

/**
 * UI Automation actions for the tournament system.
 * Handles tournament selection, balance verification, and debug operations.
 */
object TournamentActions {
    private const val TAG = "TournamentActions"
    private const val TIMEOUT_MS = 5000L
    private const val PACKAGE_NAME = "com.devfigas.dotsandboxes"

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    /**
     * Clicks the "VS Internet" button to navigate to tournament selection.
     */
    fun navigateToTournaments(): Boolean {
        Log.d(TAG, "navigateToTournaments: clicking VS Internet button")

        try {
            if (!LoginActions.isOnGameModeScreen()) {
                Log.e(TAG, "Not on game mode screen")
                return false
            }

            val internetButton = device.findObject(By.res("$PACKAGE_NAME:id/btn_vs_internet"))
            if (internetButton == null) {
                Log.e(TAG, "VS Internet button not found")
                return false
            }

            internetButton.click()
            Log.d(TAG, "VS Internet button clicked")

            Thread.sleep(2000)

            val success = isOnTournamentSelectionScreen()
            Log.d(TAG, "Navigate to tournaments result: $success")
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to tournaments: ${e.message}", e)
            return false
        }
    }

    /**
     * Checks if we're on the tournament selection screen.
     */
    fun isOnTournamentSelectionScreen(): Boolean {
        val recycler = device.findObject(By.res("$PACKAGE_NAME:id/recycler_tournaments"))
        return recycler != null
    }

    /**
     * Selects a tournament by name.
     */
    fun selectTournament(tournamentName: String): Boolean {
        Log.d(TAG, "selectTournament: selecting $tournamentName")

        try {
            var tournamentTitle = device.findObject(By.textContains(tournamentName.uppercase()))
            if (tournamentTitle == null) {
                tournamentTitle = device.findObject(By.textContains(tournamentName.lowercase()))
            }
            if (tournamentTitle == null) {
                tournamentTitle = device.findObject(By.textContains(tournamentName))
            }
            if (tournamentTitle == null) {
                Log.e(TAG, "Tournament '$tournamentName' not found")
                return false
            }

            tournamentTitle.click()
            Log.d(TAG, "Tournament '$tournamentName' clicked")

            Thread.sleep(1500)

            val lobbyScreen = device.findObject(By.res("$PACKAGE_NAME:id/recycler_view"))
            if (lobbyScreen != null) {
                Log.d(TAG, "Successfully entered lobby for $tournamentName")
                return true
            }

            val insufficientFundsDialog = device.findObject(By.textContains("insufficient"))
                ?: device.findObject(By.textContains("Insufficient"))
            if (insufficientFundsDialog != null) {
                Log.d(TAG, "Insufficient funds dialog appeared")
                return false
            }

            Log.d(TAG, "Tournament selected, waiting for lobby...")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting tournament: ${e.message}", e)
            return false
        }
    }

    /**
     * Verifies that a tournament is locked.
     */
    fun verifyTournamentLocked(tournamentName: String): Boolean {
        Log.d(TAG, "verifyTournamentLocked: checking $tournamentName")

        try {
            val tournamentTitle = device.findObject(By.textContains(tournamentName.uppercase()))
            if (tournamentTitle == null) {
                Log.e(TAG, "Tournament '$tournamentName' not found")
                return false
            }

            val lockedButton = device.findObject(By.text("LOCKED"))
            val isLocked = lockedButton != null

            Log.d(TAG, "Tournament '$tournamentName' locked: $isLocked")
            return isLocked
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying tournament locked: ${e.message}", e)
            return false
        }
    }

    /**
     * Verifies that a tournament is unlocked.
     */
    fun verifyTournamentUnlocked(tournamentName: String): Boolean {
        Log.d(TAG, "verifyTournamentUnlocked: checking $tournamentName")

        try {
            val tournamentTitle = device.findObject(By.textContains(tournamentName.uppercase()))
            if (tournamentTitle == null) {
                Log.e(TAG, "Tournament '$tournamentName' not found")
                return false
            }

            val playButton = device.findObject(By.text("PLAY"))
            val isUnlocked = playButton != null

            Log.d(TAG, "Tournament '$tournamentName' unlocked: $isUnlocked")
            return isUnlocked
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying tournament unlocked: ${e.message}", e)
            return false
        }
    }

    /**
     * Verifies the balance displayed in the UI.
     */
    fun verifyBalance(expected: Int): Boolean {
        Log.d(TAG, "verifyBalance: expecting $expected")

        try {
            val balanceView = device.findObject(By.res("$PACKAGE_NAME:id/tv_balance"))
            if (balanceView == null) {
                Log.e(TAG, "Balance view not found")
                return false
            }

            val balanceText = balanceView.text
            val actualBalance = balanceText?.toIntOrNull()

            val matches = actualBalance == expected
            Log.d(TAG, "Balance: expected=$expected, actual=$actualBalance, matches=$matches")
            return matches
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying balance: ${e.message}", e)
            return false
        }
    }

    /**
     * Verifies the no funds warning visibility.
     */
    fun verifyNoFundsWarning(shouldBeVisible: Boolean): Boolean {
        Log.d(TAG, "verifyNoFundsWarning: expecting visible=$shouldBeVisible")

        try {
            val warningView = device.findObject(By.res("$PACKAGE_NAME:id/layout_no_funds"))
                ?: device.findObject(By.textContains("No coins available"))

            val isVisible = warningView != null
            val matches = isVisible == shouldBeVisible

            Log.d(TAG, "No funds warning: expected=$shouldBeVisible, actual=$isVisible, matches=$matches")
            return matches
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying no funds warning: ${e.message}", e)
            return false
        }
    }

    /**
     * Verifies the progress displayed for a tournament.
     */
    fun verifyProgress(tournamentName: String, wins: Int, goal: Int): Boolean {
        Log.d(TAG, "verifyProgress: expecting $wins/$goal for $tournamentName")

        try {
            val expectedText = "$wins/$goal"
            val progressView = device.findObject(By.textContains(expectedText))

            val matches = progressView != null
            Log.d(TAG, "Progress for $tournamentName: expected=$expectedText, found=$matches")
            return matches
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying progress: ${e.message}", e)
            return false
        }
    }

    /**
     * Opens the debug menu from game mode screen.
     */
    fun openDebugMenu(): Boolean {
        Log.d(TAG, "openDebugMenu: opening debug menu")

        try {
            if (!LoginActions.isOnGameModeScreen()) {
                Log.e(TAG, "Not on game mode screen")
                return false
            }

            val debugButton = device.findObject(By.desc("Debug Settings"))
            if (debugButton == null) {
                Log.e(TAG, "Debug button not found (are you running debug build?)")
                return false
            }

            debugButton.click()
            Log.d(TAG, "Debug button clicked")
            Thread.sleep(1000)

            val debugTitle = device.findObject(By.textContains("Debug Menu"))
            val success = debugTitle != null
            Log.d(TAG, "Open debug menu result: $success")
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error opening debug menu: ${e.message}", e)
            return false
        }
    }

    /**
     * Sets victories value in debug menu.
     */
    fun setDebugVictories(value: Int): Boolean {
        Log.d(TAG, "setDebugVictories: setting to $value")

        try {
            val victoriesInput = device.findObject(By.res("$PACKAGE_NAME:id/et_victories"))
            if (victoriesInput == null) {
                Log.e(TAG, "Victories input not found")
                return false
            }

            victoriesInput.click()
            victoriesInput.clear()
            victoriesInput.text = value.toString()
            Log.d(TAG, "Set victories input to $value")

            val setButton = device.findObject(By.res("$PACKAGE_NAME:id/btn_set_victories"))
                ?: device.findObject(By.textContains("Set Victories"))
            if (setButton == null) {
                Log.e(TAG, "Set Victories button not found")
                return false
            }

            setButton.click()
            Log.d(TAG, "Set Victories button clicked")
            Thread.sleep(500)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting victories: ${e.message}", e)
            return false
        }
    }

    /**
     * Sets balance value in debug menu.
     */
    fun setDebugBalance(value: Int): Boolean {
        Log.d(TAG, "setDebugBalance: setting to $value")

        try {
            val balanceInput = device.findObject(By.res("$PACKAGE_NAME:id/et_refill_amount"))
            if (balanceInput == null) {
                Log.e(TAG, "Refill amount input not found")
                return false
            }

            balanceInput.click()
            balanceInput.clear()
            balanceInput.text = value.toString()
            Log.d(TAG, "Set refill amount input to $value")

            val refillButton = device.findObject(By.res("$PACKAGE_NAME:id/btn_refill_money"))
                ?: device.findObject(By.textContains("Refill Coins"))
            if (refillButton == null) {
                Log.e(TAG, "Refill Coins button not found")
                return false
            }

            refillButton.click()
            Log.d(TAG, "Refill Coins button clicked")
            Thread.sleep(500)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting balance: ${e.message}", e)
            return false
        }
    }

    /**
     * Resets all progress in debug menu.
     */
    fun resetDebugProgress(): Boolean {
        Log.d(TAG, "resetDebugProgress: resetting progress")

        try {
            val resetButton = device.findObject(By.res("$PACKAGE_NAME:id/btn_reset_progress"))
                ?: device.findObject(By.textContains("Reset All Progress"))
            if (resetButton == null) {
                Log.e(TAG, "Reset Progress button not found")
                return false
            }

            resetButton.click()
            Log.d(TAG, "Reset Progress button clicked")
            Thread.sleep(500)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting progress: ${e.message}", e)
            return false
        }
    }

    /**
     * Clicks Complete All Tournaments in debug menu.
     */
    fun completeAllTournaments(): Boolean {
        Log.d(TAG, "completeAllTournaments: completing all")

        try {
            val completeButton = device.findObject(By.res("$PACKAGE_NAME:id/btn_complete_all"))
                ?: device.findObject(By.textContains("Complete All Tournaments"))
            if (completeButton == null) {
                Log.e(TAG, "Complete All button not found")
                return false
            }

            completeButton.click()
            Log.d(TAG, "Complete All button clicked")
            Thread.sleep(500)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error completing all tournaments: ${e.message}", e)
            return false
        }
    }

    /**
     * Navigates back from current screen.
     */
    fun pressBack(): Boolean {
        Log.d(TAG, "pressBack: pressing back")
        try {
            device.pressBack()
            Thread.sleep(500)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error pressing back: ${e.message}", e)
            return false
        }
    }

    /**
     * Waits for insufficient funds dialog and dismisses it.
     */
    fun dismissInsufficientFundsDialog(): Boolean {
        Log.d(TAG, "dismissInsufficientFundsDialog: looking for dialog")

        try {
            Thread.sleep(500)

            val dialog = device.findObject(By.textContains("Insufficient"))
                ?: device.findObject(By.textContains("insufficient"))

            if (dialog == null) {
                Log.d(TAG, "Insufficient funds dialog not found")
                return false
            }

            val okButton = device.findObject(By.text("OK"))
                ?: device.findObject(By.res("android:id/button1"))

            if (okButton != null) {
                okButton.click()
                Log.d(TAG, "Insufficient funds dialog dismissed")
                Thread.sleep(500)
                return true
            }

            Log.e(TAG, "OK button not found in dialog")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing dialog: ${e.message}", e)
            return false
        }
    }

    /**
     * Waits for game over dialog after a game ends.
     */
    fun waitForGameOver(timeoutMs: Long = 30000L): Boolean {
        Log.d(TAG, "waitForGameOver: waiting...")

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val gameOverIndicator = device.findObject(By.textContains("wins"))
                ?: device.findObject(By.textContains("You won"))
                ?: device.findObject(By.textContains("You lost"))
                ?: device.findObject(By.textContains("coins"))

            if (gameOverIndicator != null) {
                Log.d(TAG, "Game over dialog found")
                return true
            }

            Thread.sleep(500)
        }

        Log.e(TAG, "Timeout waiting for game over")
        return false
    }

    /**
     * Clicks Accept/OK button on game over dialog.
     */
    fun dismissGameOverDialog(): Boolean {
        Log.d(TAG, "dismissGameOverDialog: dismissing")

        try {
            val okButton = device.findObject(By.text("OK"))
                ?: device.findObject(By.text("Accept"))
                ?: device.findObject(By.res("android:id/button1"))

            if (okButton != null) {
                okButton.click()
                Log.d(TAG, "Game over dialog dismissed")
                Thread.sleep(500)
                return true
            }

            Log.e(TAG, "Dismiss button not found")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing game over: ${e.message}", e)
            return false
        }
    }
}
