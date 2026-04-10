package com.devfigas.dotsandboxes

import android.app.ActivityManager
import android.app.Application
import android.os.Process
import com.devfigas.mockpvp.PvpGameFactoryRegistry
import com.devfigas.mockpvp.analytics.AnalyticsManager
import lib.devfigas.P2PKit
import lib.devfigas.model.domain.entity.Settings

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!isMainProcess()) return
        PvpGameFactoryRegistry.register(DotsAndBoxesGameFactory())
        P2PKit.init(this, Settings(cryptographyEnabled = false, gamePackage = BuildConfig.APPLICATION_ID))
        AnalyticsManager.initialize(this)
    }

    private fun isMainProcess(): Boolean {
        val pid = Process.myPid()
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (processInfo in manager.runningAppProcesses ?: emptyList()) {
            if (processInfo.pid == pid) {
                return processInfo.processName == packageName
            }
        }
        return false
    }
}
