package com.wisso.wizefiles.core.android.compat

import android.content.Context

object PreferenceManagerCompat {
    fun getDefaultSharedPreferencesName(context: Context): String =
        "${context.packageName}_preferences"

    val defaultSharedPreferencesMode: Int
        get() = Context.MODE_PRIVATE
}
