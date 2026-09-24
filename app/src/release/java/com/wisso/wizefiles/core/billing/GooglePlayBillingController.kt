package com.wisso.wizefiles.core.billing

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.wisso.wizefiles.core.entitlement.license.LicenseEntitlementController
import com.wisso.wizefiles.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import kotlin.coroutines.resume

internal class GooglePlayBillingController(
    private val licenseController: LicenseEntitlementController,
    private val backendBaseUrl: String,
    private val appVersionCode: Long,
) : BillingController, PurchasesUpdatedListener, DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val currentState = MutableStateFlow<BillingState>(BillingState.Unavailable)
    private val currentEvents = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 16)
    private val offerRecords = LinkedHashMap<String, PlayOfferRecord>()

    @Volatile
    private var billingClient: BillingClient? = null
    private lateinit var purchaseProcessor: PurchaseProcessor
    private lateinit var packageName: String
    private var lastRefreshElapsedMillis = Long.MIN_VALUE

    override val state: StateFlow<BillingState> = currentState.asStateFlow()
    override val events: SharedFlow<BillingEvent> = currentEvents.asSharedFlow()

    @Synchronized
    override fun initialize(context: Context) {
        if (billingClient != null) return
        val appContext = context.applicationContext
        packageName = appContext.packageName
        purchaseProcessor = PurchaseProcessor(
            packageName = packageName,
            appVersionCode = appVersionCode,
            licenseController = licenseController,
            backendClient = HttpsLicenseBackendClient(backendBaseUrl),
        )
        billingClient = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .enableAutoServiceReconnection()
            .build()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        connect()
    }

    override fun onResume(owner: LifecycleOwner) {
        refresh()
    }

    override fun refresh() {
        val client = billingClient ?: return
        if (client.isReady) {
            val now = SystemClock.elapsedRealtime()
            if (lastRefreshElapsedMillis != Long.MIN_VALUE &&
                now - lastRefreshElapsedMillis < MIN_REFRESH_INTERVAL_MILLIS
            ) {
                return
            }
            queryProducts(client)
            queryPurchases(client)
        } else if (currentState.value != BillingState.Connecting) {
            connect()
        }
    }

    override fun restorePurchases(): Boolean {
        val client = billingClient ?: return false
        if (client.isReady) {
            queryPurchases(client)
        } else {
            connect()
        }
        return true
    }

    @Synchronized
    private fun connect() {
        val client = billingClient ?: return
        if (client.isReady || currentState.value == BillingState.Connecting) return
        currentState.value = BillingState.Connecting
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts(client)
                    queryPurchases(client)
                } else {
                    setError(result)
                }
            }

            override fun onBillingServiceDisconnected() {
                currentState.value = BillingState.Connecting
            }
        })
    }

    private fun queryProducts(client: BillingClient) {
        scope.launch {
            val records = mutableListOf<PlayOfferRecord>()
            for ((type, productIds) in BillingProducts.groupedByType()) {
                val response = queryProductDetails(client, type, productIds)
                if (response.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    setError(response.billingResult)
                    return@launch
                }
                records += response.productDetails.flatMap(::toOfferRecords)
            }
            synchronized(offerRecords) {
                offerRecords.clear()
                records.forEach { offerRecords[it.offer.id] = it }
            }
            currentState.value = BillingState.Ready(records.map(PlayOfferRecord::offer))
        }
    }

    private suspend fun queryProductDetails(
        client: BillingClient,
        type: BillingProductType,
        productIds: List<String>,
    ): ProductDetailsQueryResult = suspendCancellableCoroutine { continuation ->
        val products = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(type.playType())
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        client.queryProductDetailsAsync(params) { result, detailsResult ->
            if (continuation.isActive) {
                continuation.resume(
                    ProductDetailsQueryResult(
                        billingResult = result,
                        productDetails = detailsResult.productDetailsList,
                    ),
                )
            }
        }
    }

    private fun queryPurchases(client: BillingClient) {
        lastRefreshElapsedMillis = SystemClock.elapsedRealtime()
        BillingProducts.supported.values.distinct().forEach { type ->
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(type.playType())
                .build()
            client.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    processPurchases(purchases)
                } else {
                    emitError(result.toBillingError())
                }
            }
        }
    }

    override fun launchPurchase(activity: Activity, offerId: String): BillingLaunchResult {
        val client = billingClient?.takeIf { it.isReady }
            ?: return BillingLaunchResult.Unavailable
        val record = synchronized(offerRecords) { offerRecords[offerId] }
            ?: return BillingLaunchResult.ProductUnavailable
        val productBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(record.productDetails)
        record.offerToken?.takeIf { it.isNotBlank() }?.let(productBuilder::setOfferToken)
        val flowBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productBuilder.build()))
        licenseController.installationId()?.let(::obfuscatedInstallationId)
            ?.let(flowBuilder::setObfuscatedProfileId)
        val result = client.launchBillingFlow(activity, flowBuilder.build())
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            BillingLaunchResult.Launched
        } else {
            BillingLaunchResult.Failed(result.toBillingError())
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when {
            result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null ->
                processPurchases(purchases)
            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED ->
                currentEvents.tryEmit(BillingEvent.UserCanceled)
            else -> emitError(result.toBillingError())
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val client = billingClient ?: return
        purchases.forEach { purchase ->
            scope.launch {
                val snapshot = PlayPurchaseSnapshot(
                    productIds = purchase.products.toSet(),
                    purchaseToken = purchase.purchaseToken,
                    state = purchase.purchaseState.toPurchaseState(),
                    acknowledged = purchase.isAcknowledged,
                )
                when (val result = purchaseProcessor.process(snapshot) { token ->
                    acknowledge(client, token)
                }) {
                    PurchaseProcessingResult.Ignored -> Unit
                    is PurchaseProcessingResult.Pending ->
                        currentEvents.emit(BillingEvent.PurchasePending(result.productId))
                    is PurchaseProcessingResult.Activated -> {
                        currentEvents.emit(
                            BillingEvent.ProActivated(
                                result.productId,
                                result.acknowledgementPending,
                            ),
                        )
                        if (result.acknowledgementPending) {
                            currentEvents.emit(BillingEvent.Error(BillingError.ACKNOWLEDGEMENT_FAILED))
                        }
                    }
                    is PurchaseProcessingResult.Failed ->
                        currentEvents.emit(BillingEvent.Error(result.error))
                }
            }
        }
    }

    private suspend fun acknowledge(client: BillingClient, token: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(token)
                .build()
            client.acknowledgePurchase(params) { result ->
                if (continuation.isActive) {
                    continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
            }
        }

    private fun toOfferRecords(details: ProductDetails): List<PlayOfferRecord> = when (details.productType) {
        BillingClient.ProductType.INAPP -> {
            val offers = details.oneTimePurchaseOfferDetailsList
                ?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(details.oneTimePurchaseOfferDetails)
            offers.map { offerDetails ->
                val id = listOf(
                    details.productId,
                    offerDetails.purchaseOptionId ?: "default",
                    offerDetails.offerId ?: "base",
                ).joinToString(":")
                PlayOfferRecord(
                    offer = BillingOffer(
                        id = id,
                        productId = details.productId,
                        productType = BillingProductType.ONE_TIME,
                        title = details.name,
                        description = details.description,
                        formattedPrice = offerDetails.formattedPrice,
                        priceAmountMicros = offerDetails.priceAmountMicros,
                        priceCurrencyCode = offerDetails.priceCurrencyCode,
                        offerId = offerDetails.offerId,
                    ),
                    productDetails = details,
                    offerToken = offerDetails.offerToken,
                )
            }
        }
        BillingClient.ProductType.SUBS -> details.subscriptionOfferDetails.orEmpty().map { offerDetails ->
            val phases = offerDetails.pricingPhases.pricingPhaseList
            val recurring = phases.last()
            val id = listOf(
                details.productId,
                offerDetails.basePlanId,
                offerDetails.offerId ?: "base",
            ).joinToString(":")
            PlayOfferRecord(
                offer = BillingOffer(
                    id = id,
                    productId = details.productId,
                    productType = BillingProductType.SUBSCRIPTION,
                    title = details.name,
                    description = details.description,
                    formattedPrice = recurring.formattedPrice,
                    priceAmountMicros = recurring.priceAmountMicros,
                    priceCurrencyCode = recurring.priceCurrencyCode,
                    billingPeriodIso8601 = recurring.billingPeriod,
                    basePlanId = offerDetails.basePlanId,
                    offerId = offerDetails.offerId,
                    hasFreeTrial = phases.any { it.priceAmountMicros == 0L },
                ),
                productDetails = details,
                offerToken = offerDetails.offerToken,
            )
        }
        else -> emptyList()
    }

    private fun BillingProductType.playType(): String = when (this) {
        BillingProductType.ONE_TIME -> BillingClient.ProductType.INAPP
        BillingProductType.SUBSCRIPTION -> BillingClient.ProductType.SUBS
    }

    private fun Int.toPurchaseState(): PlayPurchaseState = when (this) {
        Purchase.PurchaseState.PENDING -> PlayPurchaseState.PENDING
        Purchase.PurchaseState.PURCHASED -> PlayPurchaseState.PURCHASED
        else -> PlayPurchaseState.UNSPECIFIED
    }

    private fun BillingResult.toBillingError(): BillingError = when (responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> BillingError.BILLING_UNAVAILABLE
        BillingClient.BillingResponseCode.NETWORK_ERROR,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> BillingError.NETWORK
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> BillingError.PRODUCT_UNAVAILABLE
        else -> BillingError.INTERNAL
    }

    private fun setError(result: BillingResult) {
        val error = result.toBillingError()
        currentState.value = BillingState.Error(error)
        emitError(error)
    }

    private fun emitError(error: BillingError) {
        currentEvents.tryEmit(BillingEvent.Error(error))
        AppLog.w(TAG, "Billing operation failed: $error")
    }

    private fun obfuscatedInstallationId(installationId: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(installationId.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private data class ProductDetailsQueryResult(
        val billingResult: BillingResult,
        val productDetails: List<ProductDetails>,
    )

    private data class PlayOfferRecord(
        val offer: BillingOffer,
        val productDetails: ProductDetails,
        val offerToken: String?,
    )

    companion object {
        private const val TAG = "PlayBilling"
        private const val MIN_REFRESH_INTERVAL_MILLIS = 60_000L
    }
}
