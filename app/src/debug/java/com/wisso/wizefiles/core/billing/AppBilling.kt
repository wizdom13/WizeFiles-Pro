package com.wisso.wizefiles.core.billing

import android.content.Context

object AppBilling {
    val controller: BillingController = UnsupportedBillingController

    fun initialize(context: Context) = controller.initialize(context)
}

