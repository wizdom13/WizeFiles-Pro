package com.wisso.wizefiles.core.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object UnsupportedBillingController : BillingController {
    private val currentState = MutableStateFlow<BillingState>(BillingState.Unavailable)
    private val currentEvents = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 1)

    override val state: StateFlow<BillingState> = currentState.asStateFlow()
    override val events: SharedFlow<BillingEvent> = currentEvents.asSharedFlow()

    override fun initialize(context: Context) = Unit

    override fun refresh() = Unit

    override fun restorePurchases(): Boolean = false

    override fun launchPurchase(activity: Activity, offerId: String): BillingLaunchResult =
        BillingLaunchResult.Unavailable
}

