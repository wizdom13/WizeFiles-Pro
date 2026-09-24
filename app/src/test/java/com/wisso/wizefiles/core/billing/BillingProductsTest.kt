package com.wisso.wizefiles.core.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingProductsTest {
    @Test
    fun `product detail query groups are homogeneous and complete`() {
        val groups = BillingProducts.groupedByType()

        assertEquals(
            setOf(BillingProductType.ONE_TIME, BillingProductType.SUBSCRIPTION),
            groups.keys,
        )
        assertEquals(BillingProducts.supported.keys, groups.values.flatten().toSet())
        groups.forEach { (type, productIds) ->
            assertTrue(productIds.isNotEmpty())
            productIds.forEach { productId ->
                assertEquals(type, BillingProducts.typeOf(productId))
            }
        }
    }
}
