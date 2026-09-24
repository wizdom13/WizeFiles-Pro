// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.theme.custom

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import com.wisso.wizefiles.core.android.compat.recreateCompat
import com.wisso.wizefiles.core.android.compat.setThemeCompat
import com.wisso.wizefiles.core.android.compat.themeResIdCompat
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.theme.night.NightModeHelper
import com.wisso.wizefiles.util.AppLog
import com.wisso.wizefiles.util.SimpleActivityLifecycleCallbacks
import com.wisso.wizefiles.util.valueCompat

object CustomThemeHelper {
    private const val TAG = "CustomTheme"
    private val activityBaseThemes = mutableMapOf<Activity, Int>()
    private val warnedActivityClasses = mutableSetOf<String>()

    fun initialize(application: Application) {
        application.registerActivityLifecycleCallbacks(object : SimpleActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (!activityBaseThemes.containsKey(activity)
                    && shouldWarnForUntrackedActivity(activity.javaClass.name)) {
                    AppLog.w(
                        TAG,
                        "Skipping BaseThemedActivity-only custom theme setup for " +
                            "non-BaseThemedActivity ${activity.javaClass.name}"
                    )
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                activityBaseThemes.remove(activity)
            }
        })
    }

    fun apply(activity: Activity) {
        val baseThemeRes = activity.themeResIdCompat
        activityBaseThemes[activity] = baseThemeRes
        activity.setThemeCompat(getCustomThemeRes(baseThemeRes, activity))
    }

    internal fun shouldWarnForUntrackedActivity(activityClassName: String): Boolean =
        warnedActivityClasses.add(activityClassName)

    fun sync() {
        for ((activity, baseThemeRes) in activityBaseThemes) {
            val currentThemeRes = activity.themeResIdCompat
            val customThemeRes = getCustomThemeRes(baseThemeRes, activity)
            if (currentThemeRes != customThemeRes) {
                if (!NightModeHelper.isInNightMode(activity as AppCompatActivity)
                    && isBlackThemeChange(currentThemeRes, customThemeRes, activity)) {
                    continue
                }
                if (activity is OnThemeChangedListener) {
                    activity.onThemeChanged(customThemeRes)
                } else {
                    activity.recreateCompat()
                }
            }
        }
    }

    private fun getCustomThemeRes(@StyleRes baseThemeRes: Int, context: Context): Int {
        val resources = context.resources
        val themeName = resources.getResourceName(baseThemeRes)
        val customThemeName = if (Settings.BLACK_NIGHT_MODE.valueCompat) "$themeName.Black" else themeName
        return resources.getIdentifier(customThemeName, null, null)
    }

    private fun isBlackThemeChange(
        @StyleRes themeRes1: Int,
        @StyleRes themeRes2: Int,
        context: Context
    ): Boolean {
        val resources = context.resources
        val themeName1 = resources.getResourceName(themeRes1)
        val themeName2 = resources.getResourceName(themeRes2)
        return themeName1 == "$themeName2.Black" || themeName2 == "$themeName1.Black"
    }

    interface OnThemeChangedListener {
        fun onThemeChanged(@StyleRes theme: Int)
    }
}
