package com.wisso.wizefiles.settings

import android.content.Context
import androidx.preference.PreferenceScreen

/**
 * Debug entitlement controls are deliberately absent from this build variant.
 */
object DebugEntitlementSettings {
    fun install(
        @Suppress("UNUSED_PARAMETER") context: Context,
        @Suppress("UNUSED_PARAMETER") preferenceScreen: PreferenceScreen,
    ) = Unit
}
