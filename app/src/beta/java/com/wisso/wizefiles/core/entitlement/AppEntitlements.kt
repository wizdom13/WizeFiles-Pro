package com.wisso.wizefiles.core.entitlement

import android.content.Context
import com.wisso.wizefiles.core.entitlement.license.LicenseEntitlementController
import com.wisso.wizefiles.core.entitlement.license.UnsupportedBuildLicenseController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Time-limited GitHub beta builds expose all Pro workflows for testing and never sell licenses.
 */
object AppEntitlements {
    val repository: EntitlementRepository =
        DefaultEntitlementRepository(BetaEntitlementSource)
    val licenseController: LicenseEntitlementController = UnsupportedBuildLicenseController

    fun initialize(@Suppress("UNUSED_PARAMETER") context: Context) = Unit
}

private object BetaEntitlementSource : EntitlementSource {
    private val currentState = MutableStateFlow(EntitlementState.pro())

    override val state: StateFlow<EntitlementState> = currentState.asStateFlow()
}
