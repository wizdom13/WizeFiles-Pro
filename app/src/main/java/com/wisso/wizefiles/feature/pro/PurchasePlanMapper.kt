package com.wisso.wizefiles.feature.pro

import com.wisso.wizefiles.core.billing.BillingOffer
import com.wisso.wizefiles.core.billing.BillingProductType

internal enum class PurchasePlanKind {
    LIFETIME,
    YEARLY,
    MONTHLY,
}

internal data class PurchasePlans(
    val lifetime: BillingOffer?,
    val yearly: BillingOffer?,
    val monthly: BillingOffer?,
) {
    val isEmpty: Boolean
        get() = lifetime == null && yearly == null && monthly == null
}

internal fun List<BillingOffer>.toPurchasePlans(): PurchasePlans {
    val lifetimeOffers = filter { it.productType == BillingProductType.ONE_TIME }
    val subscriptionOffers = filter { it.productType == BillingProductType.SUBSCRIPTION }
    return PurchasePlans(
        lifetime = lifetimeOffers.preferredOffer(),
        yearly = subscriptionOffers
            .filter { it.billingPeriodIso8601 == PERIOD_YEARLY }
            .preferredOffer(),
        monthly = subscriptionOffers
            .filter { it.billingPeriodIso8601 == PERIOD_MONTHLY }
            .preferredOffer(),
    )
}

private fun List<BillingOffer>.preferredOffer(): BillingOffer? =
    sortedWith(
        compareByDescending<BillingOffer> { it.hasFreeTrial }
            .thenBy { it.priceAmountMicros },
    ).firstOrNull()

private const val PERIOD_MONTHLY = "P1M"
private const val PERIOD_YEARLY = "P1Y"
