package com.wisso.wizefiles.settings

import android.content.Context
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import com.wisso.wizefiles.core.entitlement.DebugEntitlementController

/**
 * Installs developer-only controls into App Settings for debug builds.
 */
object DebugEntitlementSettings {
    fun install(context: Context, preferenceScreen: PreferenceScreen) {
        val category = PreferenceCategory(context).apply {
            key = "debug_entitlement_category"
            title = "Developer"
            isIconSpaceReserved = false
        }
        val simulatePro = SwitchPreferenceCompat(context).apply {
            key = "debug_simulate_pro"
            title = "Simulate Pro access"
            summary = "Unlock Pro features in this debug build only"
            isIconSpaceReserved = false
            isPersistent = false
            isChecked = DebugEntitlementController.isProEnabled()
            setOnPreferenceChangeListener { _, value ->
                DebugEntitlementController.setProEnabled(context, value as Boolean)
                true
            }
        }
        preferenceScreen.addPreference(category)
        category.addPreference(simulatePro)
    }
}
