package com.michael.tradelab.ui.paywall

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.michael.tradelab.R
import com.michael.tradelab.data.repo.BillingRepository
import com.michael.tradelab.ui.components.DisclaimerBanner
import com.michael.tradelab.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billing: BillingRepository,
) : ViewModel() {
    val products = billing.products
    val available = billing.billingAvailable
    val proUnlocked = billing.proUnlocked.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun buy(activity: Activity, details: ProductDetails) = billing.launchPurchase(activity, details)
    fun restore() = billing.restorePurchases()
}

@Composable
fun ProPaywallScreen(viewModel: PaywallViewModel = hiltViewModel()) {
    val products by viewModel.products.collectAsState()
    val available by viewModel.available.collectAsState()
    val pro by viewModel.proUnlocked.collectAsState()
    val activity = LocalContext.current as? Activity

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        DisclaimerBanner(stringResource(R.string.paywall_note))
        Text(
            "TradeLab Pro",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
        Text(
            "Pro unlocks extra chart overlays and longer educational backtests. The virtual top-up pack adds simulated funds with no real-world value.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
        if (pro) {
            Text(
                "Pro is active on this device.",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
        if (!available) {
            Text(
                "Google Play Billing is unavailable right now. Purchases will appear here when Play services are reachable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
        products.forEach { details ->
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(details.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        details.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { activity?.let { viewModel.buy(it, details) } },
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(details.oneTimePurchaseOfferDetails?.formattedPrice ?: "Buy")
                    }
                }
            }
        }
        TextButton(
            onClick = viewModel::restore,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        ) { Text("Restore purchases") }
    }
}
