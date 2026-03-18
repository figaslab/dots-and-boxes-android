package com.devfigas.dotsandboxes.e2e

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BannerAdE2ETest {

    companion object {
        private const val TAG = "BannerAdE2ETest"
        private const val TIMEOUT_MS = 15000L
    }

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    private val pkg: String by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.packageName
    }

    private fun presetAgeCategory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("chess_age", Context.MODE_PRIVATE).edit()
            .putString("age_category", "ADULT")
            .putLong("verified_timestamp", System.currentTimeMillis())
            .putInt("verification_version", 2)
            .commit()
    }

    @Test
    fun testBannerInPracticeMode() {
        Log.d(TAG, "Starting banner test - package: $pkg")

        presetAgeCategory()
        device.pressHome()
        Thread.sleep(1000)

        device.executeShellCommand("logcat -c")
        Thread.sleep(500)

        device.executeShellCommand("am start -n $pkg/com.devfigas.mockpvp.activity.MainActivity")
        device.wait(Until.hasObject(By.pkg(pkg).depth(0)), TIMEOUT_MS)
        Thread.sleep(5000)

        // Handle consent dialog
        for (i in 1..3) {
            val consentBtn = device.findObject(By.desc("Consent"))
            if (consentBtn != null) { consentBtn.click(); Thread.sleep(3000); break }
            Thread.sleep(1000)
        }

        // Handle login
        val nameField = device.findObject(By.res("$pkg:id/landing_page_et_name"))
        if (nameField != null) {
            nameField.click(); Thread.sleep(300); nameField.clear(); nameField.text = "TestPlayer"
            device.pressBack(); Thread.sleep(500)
            device.findObject(By.res("$pkg:id/landing_page_bt_enter"))?.click()
            Thread.sleep(3000)
        }

        // Click CPU button
        val cpuBtn = device.wait(Until.findObject(By.res("$pkg:id/btn_vs_cpu")), 10000)
        assertNotNull("CPU button not found", cpuBtn)
        cpuBtn.click()
        Thread.sleep(2000)

        // Handle side selection
        val sideOption = device.findObject(By.textContains("Red"))
            ?: device.findObject(By.textContains("Blue"))
            ?: device.findObject(By.textContains("First"))
        sideOption?.click()
        Thread.sleep(1000)

        // Wait for versus dialog + ad load
        Thread.sleep(15000)

        // Verify game screen
        val boardView = device.findObject(By.res("$pkg:id/dots_and_boxes_board_view"))
        assertNotNull("Board view not found - not in game screen", boardView)

        // Check Appodeal init + banner via logcat
        val logcat = device.executeShellCommand("logcat -d -s AppodealAdProvider:D AdManager:D")
        Log.d(TAG, "Appodeal logs: $logcat")

        val initFailed = logcat.contains("Appodeal initialized: false") || logcat.contains("init error")
        assertFalse("Appodeal init FAILED - wrong key? Logs: ${logcat.take(500)}", initFailed)

        val initSuccess = logcat.contains("Appodeal initialized: true")
        assertTrue("Appodeal did not initialize. Logs: ${logcat.take(500)}", initSuccess)

        val bannerLoaded = logcat.contains("Banner loaded")
        assertTrue("Banner did NOT load. Logs: ${logcat.take(500)}", bannerLoaded)

        Log.d(TAG, "SUCCESS: Appodeal initialized + banner loaded correctly")
    }
}
