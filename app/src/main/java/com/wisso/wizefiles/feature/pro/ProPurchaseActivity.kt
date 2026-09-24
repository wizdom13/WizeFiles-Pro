package com.wisso.wizefiles.feature.pro

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.core.billing.AppBilling
import com.wisso.wizefiles.core.billing.BillingError
import com.wisso.wizefiles.core.billing.BillingEvent
import com.wisso.wizefiles.core.billing.BillingLaunchResult
import com.wisso.wizefiles.core.billing.BillingOffer
import com.wisso.wizefiles.core.billing.BillingProducts
import com.wisso.wizefiles.core.billing.BillingState
import com.wisso.wizefiles.core.entitlement.AppEntitlements
import com.wisso.wizefiles.core.entitlement.EntitlementState
import com.wisso.wizefiles.databinding.ActivityProPurchaseBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ProPurchaseActivity : BaseThemedActivity() {
    private lateinit var binding: ActivityProPurchaseBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProPurchaseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        applyInsets()

        binding.retryButton.setOnClickListener {
            showStatus(R.string.pro_purchase_connecting)
            AppBilling.controller.refresh()
        }
        binding.restoreButton.setOnClickListener {
            if (AppBilling.controller.restorePurchases()) {
                showStatus(R.string.pro_purchase_restore_started)
            } else {
                showStatus(R.string.pro_purchase_unavailable)
            }
        }
        binding.manageButton.setOnClickListener { openSubscriptionManagement() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        AppBilling.controller.state,
                        AppEntitlements.repository.state,
                    ) { billing, entitlement -> billing to entitlement }
                        .collect { (billing, entitlement) ->
                            render(billing, entitlement)
                        }
                }
                launch {
                    AppBilling.controller.events.collect(::handleEvent)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppBilling.controller.refresh()
    }

    private fun render(
        billingState: BillingState,
        entitlementState: EntitlementState,
    ) {
        val isPro = entitlementState.isPro
        binding.activeCard.isVisible = isPro
        binding.purchaseContent.isVisible = !isPro
        binding.restoreButton.isVisible = !isPro && billingState !is BillingState.Unavailable
        binding.manageButton.isVisible = isPro && billingState !is BillingState.Unavailable

        if (isPro) {
            binding.statusMessage.setText(R.string.pro_purchase_active_status)
            return
        }

        when (billingState) {
            BillingState.Unavailable -> {
                setLoading(false)
                hidePlans()
                binding.retryButton.isVisible = false
                showStatus(R.string.pro_purchase_unavailable)
            }

            BillingState.Connecting -> {
                setLoading(true)
                hidePlans()
                binding.retryButton.isVisible = false
                showStatus(R.string.pro_purchase_connecting)
            }

            is BillingState.Error -> {
                setLoading(false)
                hidePlans()
                binding.retryButton.isVisible = true
                showStatus(billingState.error.messageResource())
            }

            is BillingState.Ready -> {
                setLoading(false)
                binding.retryButton.isVisible = false
                renderPlans(billingState.offers.toPurchasePlans())
            }
        }
    }

    private fun renderPlans(plans: PurchasePlans) {
        binding.noPlansText.isVisible = plans.isEmpty
        renderPlan(
            binding.lifetimeCard,
            binding.lifetimePrice,
            binding.lifetimeDescription,
            binding.lifetimeTrial,
            binding.lifetimeButton,
            plans.lifetime,
            PurchasePlanKind.LIFETIME,
        )
        renderPlan(
            binding.yearlyCard,
            binding.yearlyPrice,
            binding.yearlyDescription,
            binding.yearlyTrial,
            binding.yearlyButton,
            plans.yearly,
            PurchasePlanKind.YEARLY,
        )
        renderPlan(
            binding.monthlyCard,
            binding.monthlyPrice,
            binding.monthlyDescription,
            binding.monthlyTrial,
            binding.monthlyButton,
            plans.monthly,
            PurchasePlanKind.MONTHLY,
        )
        if (plans.isEmpty) {
            showStatus(R.string.pro_purchase_products_unavailable)
        } else {
            showStatus(R.string.pro_purchase_choose_plan)
        }
    }

    private fun renderPlan(
        card: View,
        price: TextView,
        description: TextView,
        trial: View,
        button: MaterialButton,
        offer: BillingOffer?,
        kind: PurchasePlanKind,
    ) {
        card.isVisible = offer != null
        if (offer == null) return

        price.text = when (kind) {
            PurchasePlanKind.LIFETIME -> offer.formattedPrice
            PurchasePlanKind.YEARLY ->
                getString(R.string.pro_purchase_price_yearly, offer.formattedPrice)
            PurchasePlanKind.MONTHLY ->
                getString(R.string.pro_purchase_price_monthly, offer.formattedPrice)
        }
        description.text = offer.description.ifBlank {
            getString(
                when (kind) {
                    PurchasePlanKind.LIFETIME -> R.string.pro_purchase_lifetime_summary
                    PurchasePlanKind.YEARLY -> R.string.pro_purchase_yearly_summary
                    PurchasePlanKind.MONTHLY -> R.string.pro_purchase_monthly_summary
                },
            )
        }
        trial.isVisible = offer.hasFreeTrial
        button.text = getString(
            when (kind) {
                PurchasePlanKind.LIFETIME -> R.string.pro_purchase_buy_lifetime
                PurchasePlanKind.YEARLY -> R.string.pro_purchase_subscribe_yearly
                PurchasePlanKind.MONTHLY -> R.string.pro_purchase_subscribe_monthly
            },
            offer.formattedPrice,
        )
        button.setOnClickListener { launchPurchase(offer) }
    }

    private fun launchPurchase(offer: BillingOffer) {
        when (val result = AppBilling.controller.launchPurchase(this, offer.id)) {
            BillingLaunchResult.Launched ->
                showStatus(R.string.pro_purchase_complete_in_play)
            BillingLaunchResult.Unavailable ->
                showStatus(R.string.pro_purchase_unavailable)
            BillingLaunchResult.ProductUnavailable ->
                showStatus(R.string.pro_purchase_products_unavailable)
            is BillingLaunchResult.Failed ->
                showStatus(result.error.messageResource())
        }
    }

    private fun handleEvent(event: BillingEvent) {
        when (event) {
            is BillingEvent.PurchasePending ->
                showStatus(R.string.pro_purchase_pending)
            is BillingEvent.ProActivated -> {
                showStatus(
                    if (event.acknowledgementPending) {
                        R.string.pro_purchase_active_ack_pending
                    } else {
                        R.string.pro_purchase_activated
                    },
                )
            }
            BillingEvent.UserCanceled ->
                showStatus(R.string.pro_purchase_canceled)
            is BillingEvent.Error ->
                showStatus(event.error.messageResource())
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.isVisible = loading
    }

    private fun hidePlans() {
        binding.noPlansText.isVisible = false
        binding.lifetimeCard.isVisible = false
        binding.yearlyCard.isVisible = false
        binding.monthlyCard.isVisible = false
    }

    private fun showStatus(messageResource: Int) {
        binding.statusMessage.setText(messageResource)
    }

    private fun BillingError.messageResource(): Int = when (this) {
        BillingError.BILLING_UNAVAILABLE -> R.string.pro_purchase_unavailable
        BillingError.NETWORK -> R.string.pro_purchase_network_error
        BillingError.PRODUCT_UNAVAILABLE -> R.string.pro_purchase_products_unavailable
        BillingError.BACKEND_UNAVAILABLE -> R.string.pro_purchase_backend_error
        BillingError.VERIFICATION_FAILED -> R.string.pro_purchase_verification_error
        BillingError.ACKNOWLEDGEMENT_FAILED -> R.string.pro_purchase_acknowledgement_error
        BillingError.INTERNAL -> R.string.pro_purchase_internal_error
    }

    private fun openSubscriptionManagement() {
        val uri = Uri.parse(
            "https://play.google.com/store/account/subscriptions" +
                "?sku=${BillingProducts.PRO_SUBSCRIPTION}" +
                "&package=$packageName",
        )
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }.onFailure {
            showStatus(R.string.pro_purchase_manage_unavailable)
        }
    }

    private fun applyInsets() {
        val initialToolbarTop = binding.toolbar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val status = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = initialToolbarTop + status.top)
            insets
        }
        val initialBottom = binding.scrollView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { view, insets ->
            val navigation =
                insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = initialBottom + navigation.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, ProPurchaseActivity::class.java)
    }
}
