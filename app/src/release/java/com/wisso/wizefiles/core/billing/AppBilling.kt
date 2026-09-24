package com.wisso.wizefiles.core.billing

import android.content.Context
import com.wisso.wizefiles.BuildConfig
import com.wisso.wizefiles.core.entitlement.AppEntitlements

object AppBilling {
    val controller: BillingController by lazy {
        if (BuildConfig.LICENSE_API_BASE_URL.isBlank() || BuildConfig.LICENSE_KEY_ID.isBlank()) {
            UnsupportedBillingController
        } else {
            GooglePlayBillingController(
                licenseController = AppEntitlements.licenseController,
                backendBaseUrl = BuildConfig.LICENSE_API_BASE_URL,
                appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            )
        }
    }

    fun initialize(context: Context) = controller.initialize(context)
}
