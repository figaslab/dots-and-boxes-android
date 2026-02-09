package com.devfigas.reversi

import android.app.ActivityManager
import android.app.Application
import android.os.Process
import com.devfigas.mockpvp.PvpGameFactoryRegistry
import com.devfigas.mockpvp.analytics.AnalyticsManager
import lib.devfigas.P2PKit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!isMainProcess()) return
        PvpGameFactoryRegistry.register(ReversiGameFactory())
        P2PKit.init(this)
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
