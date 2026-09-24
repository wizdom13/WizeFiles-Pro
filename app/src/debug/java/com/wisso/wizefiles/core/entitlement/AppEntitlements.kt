package com.wisso.wizefiles.core.entitlement

import android.content.Context
import com.wisso.wizefiles.core.entitlement.license.LicenseEntitlementController
import com.wisso.wizefiles.core.entitlement.license.UnsupportedBuildLicenseController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Debug builds start Free and expose a persistent override for exercising both product states.
 */
object AppEntitlements {
    private val source = DebugEntitlementSource()

    val repository: EntitlementRepository = DefaultEntitlementRepository(source)
    val licenseController: LicenseEntitlementController = UnsupportedBuildLicenseController

    fun initialize(context: Context) {
        source.setProEnabled(debugPreferences(context).getBoolean(KEY_SIMULATE_PRO, false))
    }

    internal fun setDebugOverride(isPro: Boolean?) {
        source.setProEnabled(isPro == true)
    }

    internal fun setDebugOverride(context: Context, isPro: Boolean) {
        debugPreferences(context).edit().putBoolean(KEY_SIMULATE_PRO, isPro).apply()
        source.setProEnabled(isPro)
    }

    internal fun clearDebugOverride(context: Context) {
        debugPreferences(context).edit().remove(KEY_SIMULATE_PRO).apply()
        source.setProEnabled(false)
    }

    internal fun isDebugOverrideEnabled(): Boolean = source.state.value.isPro

    private fun debugPreferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "debug_entitlements"
    private const val KEY_SIMULATE_PRO = "simulate_pro"
}

/**
 * Debug-source-set-only control. It is absent from release and beta artifacts.
 */
object DebugEntitlementController {
    fun setProEnabled(isPro: Boolean?) {
        AppEntitlements.setDebugOverride(isPro)
    }

    fun setProEnabled(context: Context, isPro: Boolean) {
        AppEntitlements.setDebugOverride(context, isPro)
    }

    fun isProEnabled(): Boolean = AppEntitlements.isDebugOverrideEnabled()

    fun reset() {
        AppEntitlements.setDebugOverride(null)
    }

    fun reset(context: Context) {
        AppEntitlements.clearDebugOverride(context)
    }
}

private class DebugEntitlementSource : EntitlementSource {
    private val currentState = MutableStateFlow(EntitlementState.free())

    override val state: StateFlow<EntitlementState> = currentState.asStateFlow()

    fun setProEnabled(isPro: Boolean) {
        currentState.value =
            if (isPro) {
                EntitlementState.pro()
            } else {
                EntitlementState.free()
            }
    }
}
