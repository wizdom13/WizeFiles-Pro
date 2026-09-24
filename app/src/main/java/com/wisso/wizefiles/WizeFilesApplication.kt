package com.wisso.wizefiles

import android.app.Application
import android.content.Context
import com.wisso.wizefiles.feature.crashreport.CrashReportNotification
import com.wisso.wizefiles.feature.crashreport.CrashReportStore
import org.acra.ACRA.init
import org.acra.ACRA.isACRASenderServiceProcess
import org.acra.config.CoreConfigurationBuilder

class WizeFilesApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (isACRASenderServiceProcess()) return

        val acraConfiguration = CoreConfigurationBuilder()
            .withBuildConfigClass(BuildConfig::class.java)
        init(this, acraConfiguration)
    }

    override fun onCreate() {
        super.onCreate()
        CrashReportNotification.createChannel(this)
        CrashReportStore.pruneExpired(this)
    }
}
