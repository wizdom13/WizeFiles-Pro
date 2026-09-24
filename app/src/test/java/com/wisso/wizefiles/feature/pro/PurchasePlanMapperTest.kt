package com.wisso.wizefiles.feature.pro

import com.wisso.wizefiles.core.billing.BillingOffer
import com.wisso.wizefiles.core.billing.BillingProductType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurchasePlanMapperTest {
    @Test
    fun classifiesLifetimeYearlyAndMonthlyOffers() {
        val lifetime = offer("lifetime", BillingProductType.ONE_TIME)
        val yearly = offer(
            "yearly",
            BillingProductType.SUBSCRIPTION,
            billingPeriod = "P1Y",
        )
        val monthly = offer(
            "monthly",
            BillingProductType.SUBSCRIPTION,
            billingPeriod = "P1M",
        )

        val plans = listOf(monthly, lifetime, yearly).toPurchasePlans()

        assertEquals(lifetime, plans.lifetime)
        assertEquals(yearly, plans.yearly)
        assertEquals(monthly, plans.monthly)
    }

    @Test
    fun prefersEligibleTrialForSameCadence() {
        val base = offer(
            "yearly-base",
            BillingProductType.SUBSCRIPTION,
            billingPeriod = "P1Y",
            priceMicros = 12_000_000,
        )
        val trial = offer(
            "yearly-trial",
            BillingProductType.SUBSCRIPTION,
            billingPeriod = "P1Y",
            priceMicros = 15_000_000,
            hasFreeTrial = true,
        )

        assertEquals(trial, listOf(base, trial).toPurchasePlans().yearly)
    }

    @Test
    fun ignoresUnsupportedSubscriptionCadence() {
        val weekly = offer(
            "weekly",
            BillingProductType.SUBSCRIPTION,
            billingPeriod = "P1W",
        )

        val plans = listOf(weekly).toPurchasePlans()

        assertNull(plans.yearly)
        assertNull(plans.monthly)
        assertEquals(true, plans.isEmpty)
    }

    private fun offer(
        id: String,
        type: BillingProductType,
        billingPeriod: String? = null,
        priceMicros: Long = 1_000_000,
        hasFreeTrial: Boolean = false,
    ) = BillingOffer(
        id = id,
        productId = id,
        productType = type,
        title = id,
        description = "",
        formattedPrice = "\$1.00",
        priceAmountMicros = priceMicros,
        priceCurrencyCode = "USD",
        billingPeriodIso8601 = billingPeriod,
        hasFreeTrial = hasFreeTrial,
    )
}
