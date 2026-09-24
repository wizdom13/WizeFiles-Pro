// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.theme.night

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegateCompat
import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.AppLog
import com.wisso.wizefiles.util.SimpleActivityLifecycleCallbacks
import com.wisso.wizefiles.util.valueCompat

// We take over the activity creation when setting the default night mode from AppCompat so that:
// 1. We can recreate all activities upon change, instead of only started activities.
// 2. We can have custom handling of the change, instead of being forced to either recreate or
//    update resources configuration which is shared among activities.
object NightModeHelper {
    private const val TAG = "NightMode"
    private val activities = mutableSetOf<AppCompatActivity>()
    private val warnedActivityClasses = mutableSetOf<String>()

    fun initialize(application: Application) {
        application.registerActivityLifecycleCallbacks(object : SimpleActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    if (activity !in activities
                        && shouldWarnForUntrackedActivity(activity.javaClass.name)) {
                        AppLog.w(
                            TAG,
                            "Skipping BaseThemedActivity-only night mode setup for " +
                                "non-BaseThemedActivity ${activity.javaClass.name}"
                        )
                    }
                }

                override fun onActivityDestroyed(activity: Activity) {
                    if (activity is AppCompatActivity) {
                        activities -= activity
                    }
                }
            })
    }

    internal fun shouldWarnForUntrackedActivity(activityClassName: String): Boolean =
        warnedActivityClasses.add(activityClassName)

    fun apply(activity: AppCompatActivity) {
        activities += activity
        activity.delegate.localNightMode = nightMode
    }

    fun sync() {
        for (activity in activities) {
            val nightMode = nightMode
            if (activity is OnNightModeChangedListener) {
                if (getUiModeNight(activity.delegate.localNightMode, activity)
                    != getUiModeNight(nightMode, activity)) {
                    activity.onNightModeChangedFromHelper(nightMode)
                }
            } else {
                activity.delegate.localNightMode = nightMode
            }
        }
    }

    private val nightMode: Int
        get() = Settings.NIGHT_MODE.valueCompat.value

    /*
     * @see androidx.appcompat.app.AppCompatDelegateImpl#updateForNightMode(int, boolean)
     */
    private fun getUiModeNight(nightMode: Int, activity: AppCompatActivity): Int =
        when (AppCompatDelegateCompat.mapNightMode(activity.delegate, application, nightMode)) {
            AppCompatDelegate.MODE_NIGHT_YES -> Configuration.UI_MODE_NIGHT_YES
            AppCompatDelegate.MODE_NIGHT_NO -> Configuration.UI_MODE_NIGHT_NO
            else ->
                (activity.applicationContext.resources.configuration.uiMode
                    and Configuration.UI_MODE_NIGHT_MASK)
        }

    fun isInNightMode(activity: AppCompatActivity): Boolean =
        (getUiModeNight(activity.delegate.localNightMode, activity)
            == Configuration.UI_MODE_NIGHT_YES)

    interface OnNightModeChangedListener {
        fun onNightModeChangedFromHelper(nightMode: Int)
    }
}
