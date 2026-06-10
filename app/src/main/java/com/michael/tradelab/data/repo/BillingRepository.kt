package com.michael.tradelab.data.repo

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.michael.tradelab.data.local.EntitlementDao
import com.michael.tradelab.data.local.EntitlementEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play Billing is the only payment path. All products are digital goods:
 * Pro indicator set + virtual top-up packs with no real-world value.
 */
@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val entitlementDao: EntitlementDao,
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val billingAvailable = MutableStateFlow(false)

    val proUnlocked: Flow<Boolean> = entitlementDao.observeAll()
        .map { list -> list.any { it.productId == PRODUCT_PRO && it.owned } }

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    fun connect() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                billingAvailable.value = result.responseCode == BillingClient.BillingResponseCode.OK
                if (billingAvailable.value) {
                    queryProducts()
                    restorePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                billingAvailable.value = false
            }
        })
    }

    private fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(PRODUCT_PRO, PRODUCT_TOPUP).map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            ).build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) products.value = details
        }
    }

    fun launchPurchase(activity: Activity, details: ProductDetails) {
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            ).build()
        client.launchBillingFlow(activity, params)
    }

    fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handlePurchases(purchases)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val now = System.currentTimeMillis()
        scope.launch {
            val owned = purchases
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .flatMap { it.products }
                .toSet()
            entitlementDao.upsertAll(
                listOf(PRODUCT_PRO, PRODUCT_TOPUP).map { EntitlementEntity(it, it in owned, now) }
            )
        }
        // Acknowledge within Play's 3-day window.
        purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build()
                ) { }
            }
    }

    companion object {
        const val PRODUCT_PRO = "pro_indicator_set"
        const val PRODUCT_TOPUP = "virtual_topup_pack"
    }
}
