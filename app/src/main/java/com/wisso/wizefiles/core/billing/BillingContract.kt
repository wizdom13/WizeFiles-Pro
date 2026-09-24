package com.wisso.wizefiles.core.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class BillingProductType {
    ONE_TIME,
    SUBSCRIPTION,
}

data class BillingOffer(
    val id: String,
    val productId: String,
    val productType: BillingProductType,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
    val billingPeriodIso8601: String? = null,
    val basePlanId: String? = null,
    val offerId: String? = null,
    val hasFreeTrial: Boolean = false,
)

sealed interface BillingState {
    data object Unavailable : BillingState

    data object Connecting : BillingState

    data class Ready(val offers: List<BillingOffer>) : BillingState

    data class Error(val error: BillingError) : BillingState
}

enum class BillingError {
    BILLING_UNAVAILABLE,
    NETWORK,
    PRODUCT_UNAVAILABLE,
    BACKEND_UNAVAILABLE,
    VERIFICATION_FAILED,
    ACKNOWLEDGEMENT_FAILED,
    INTERNAL,
}

sealed interface BillingEvent {
    data class PurchasePending(val productId: String) : BillingEvent

    data class ProActivated(
        val productId: String,
        val acknowledgementPending: Boolean,
    ) : BillingEvent

    data object UserCanceled : BillingEvent

    data class Error(val error: BillingError) : BillingEvent
}

sealed interface BillingLaunchResult {
    data object Launched : BillingLaunchResult

    data object Unavailable : BillingLaunchResult

    data object ProductUnavailable : BillingLaunchResult

    data class Failed(val error: BillingError) : BillingLaunchResult
}

interface BillingController {
    val state: StateFlow<BillingState>
    val events: SharedFlow<BillingEvent>

    fun initialize(context: Context)

    fun refresh()

    fun restorePurchases(): Boolean

    fun launchPurchase(activity: Activity, offerId: String): BillingLaunchResult
}

