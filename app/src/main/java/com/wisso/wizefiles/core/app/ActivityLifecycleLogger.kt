package com.wisso.wizefiles.core.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.wisso.wizefiles.util.AppLog

internal object ActivityLifecycleLogger : Application.ActivityLifecycleCallbacks {
    private const val TAG = "ActivityLifecycle"

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        AppLog.i(TAG, "Registered global activity lifecycle callbacks")
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        AppLog.i(
            TAG,
            "created activity=${activity.javaClass.name}, hasSavedState=${savedInstanceState != null}, ${intentSummary(activity.intent)}"
        )
    }

    override fun onActivityStarted(activity: Activity) {
        AppLog.i(TAG, "started activity=${activity.javaClass.name}")
    }

    override fun onActivityResumed(activity: Activity) {
        AppLog.i(TAG, "resumed activity=${activity.javaClass.name}")
    }

    override fun onActivityPaused(activity: Activity) {
        AppLog.i(TAG, "paused activity=${activity.javaClass.name}")
    }

    override fun onActivityStopped(activity: Activity) {
        AppLog.i(TAG, "stopped activity=${activity.javaClass.name}")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        AppLog.i(TAG, "saveInstanceState activity=${activity.javaClass.name}")
    }

    override fun onActivityDestroyed(activity: Activity) {
        AppLog.i(TAG, "destroyed activity=${activity.javaClass.name}, isFinishing=${activity.isFinishing}")
    }

    private fun intentSummary(intent: Intent?): String = AppLog.summarizeIntent(intent)
}
