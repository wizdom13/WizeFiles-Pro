package com.wisso.wizefiles.core.billing

object BillingProducts {
    const val PRO_LIFETIME = "wizefiles_pro_lifetime"
    const val PRO_SUBSCRIPTION = "wizefiles_pro"

    val supported: Map<String, BillingProductType> = mapOf(
        PRO_LIFETIME to BillingProductType.ONE_TIME,
        PRO_SUBSCRIPTION to BillingProductType.SUBSCRIPTION,
    )

    fun typeOf(productId: String): BillingProductType? = supported[productId]

    fun groupedByType(): Map<BillingProductType, List<String>> =
        supported.entries.groupBy({ it.value }, { it.key })
}

