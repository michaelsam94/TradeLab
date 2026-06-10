package com.michael.tradelab.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michael.tradelab.R
import com.michael.tradelab.data.repo.MarketRepository
import com.michael.tradelab.data.repo.PortfolioRepository
import com.michael.tradelab.domain.model.OrderSide
import com.michael.tradelab.domain.model.VirtualOrder
import com.michael.tradelab.domain.usecase.ComputePerformanceStatsUseCase
import com.michael.tradelab.ui.components.EmptyState
import com.michael.tradelab.ui.components.formatPrice
import com.michael.tradelab.ui.components.formatUsd
import com.michael.tradelab.ui.theme.Spacing
import com.michael.tradelab.ui.theme.gainColor
import com.michael.tradelab.ui.theme.lossColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HistoryUi(
    val orders: List<VirtualOrder> = emptyList(),
    val cash: Double = 0.0,
    val stats: ComputePerformanceStatsUseCase.Stats? = null,
    /** live unrealized P/L over all open positions; null when no live prices yet */
    val unrealized: Double? = null,
    /** live equity = cash + locked margin + unrealized P/L */
    val equity: Double? = null,
    val openPositions: Int = 0,
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    portfolio: PortfolioRepository,
    market: MarketRepository,
    computeStats: ComputePerformanceStatsUseCase,
) : ViewModel() {
    val ui = combine(
        portfolio.filledOrders,
        portfolio.wallet,
        portfolio.positions,
        market.liveTicks.sample(1_000),
    ) { orders, wallet, positions, ticks ->
        val priced = positions.mapNotNull { pos -> ticks[pos.symbol]?.last?.let { pos to it } }
        val unrealized = if (priced.isEmpty() && positions.isNotEmpty()) null
        else priced.sumOf { (pos, price) -> pos.unrealizedPnl(price) }
        val lockedMargin = positions.sumOf { it.margin }
        HistoryUi(
            orders = orders,
            cash = wallet.cashBalance,
            stats = computeStats(orders, PortfolioRepository.STARTING_BALANCE),
            unrealized = unrealized,
            equity = unrealized?.let { wallet.cashBalance + lockedMargin + it },
            openPositions = positions.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUi())
}

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    val stats = ui.stats

    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header") {
            Column(Modifier.padding(Spacing.lg)) {
                Text("Performance", style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.simulated_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (stats != null) {
            item(key = "stats") {
                // Mandatory LazyRow: metric cards clip on small phones.
                val statState = rememberLazyListState()
                val cards = listOf(
                    "Equity" to (ui.equity?.let { formatUsd(it) } ?: "—"),
                    "Unrealized P/L" to (ui.unrealized?.let { formatUsd(it) } ?: "—"),
                    "Open positions" to ui.openPositions.toString(),
                    "Virtual cash" to formatUsd(ui.cash),
                    "Realized P/L" to formatUsd(stats.realizedPnl),
                    "Win rate" to "${"%.0f".format(stats.winRate * 100)}%",
                    "Closed trades" to stats.closedTrades.toString(),
                    "Max drawdown" to "${"%.1f".format(stats.maxDrawdownPct)}%",
                )
                LazyRow(
                    state = statState,
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(items = cards, key = { it.first }) { (label, value) ->
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.width(150.dp),
                        ) {
                            Column(Modifier.padding(Spacing.lg)) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    stringResource(R.string.simulated_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "orders_header") {
            Text(
                "Filled virtual orders",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            )
        }
        if (ui.orders.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    headline = "No virtual trades yet",
                    body = "Place a virtual order from the Trade tab and it will appear here.",
                )
            }
        } else {
            items(items = ui.orders, key = { it.id }) { order ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                ) {
                    Column {
                        Text(
                            "${order.side.name} ${order.symbol}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (order.side == OrderSide.BUY) gainColor() else lossColor(),
                        )
                        Text(
                            "${order.qty} @ ${order.fillPrice?.let { formatPrice(it) } ?: "—"} · " +
                                SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(order.filledAt ?: order.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (order.reducedQty > 0) {
                        Text(
                            formatUsd(order.realizedPnl),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (order.realizedPnl >= 0) gainColor() else lossColor(),
                        )
                    }
                }
            }
        }
    }
}
