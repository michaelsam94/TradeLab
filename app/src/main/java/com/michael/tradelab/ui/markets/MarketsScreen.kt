package com.michael.tradelab.ui.markets

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michael.tradelab.data.repo.MarketRepository
import com.michael.tradelab.domain.model.Ticker
import com.michael.tradelab.domain.model.TradingPair
import com.michael.tradelab.ui.components.ChangeText
import com.michael.tradelab.ui.components.EmptyState
import com.michael.tradelab.ui.components.SkeletonRow
import com.michael.tradelab.ui.components.StatusChip
import com.michael.tradelab.ui.components.formatPrice
import com.michael.tradelab.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class MarketRow(val pair: TradingPair, val ticker: Ticker?)

data class MarketsUi(
    val loading: Boolean = true,
    val rows: List<MarketRow> = emptyList(),
    val movers: List<MarketRow> = emptyList(),
    val filter: String = "USDT",
    val query: String = "",
    val lastUpdated: Long? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class MarketsViewModel @Inject constructor(
    private val market: MarketRepository,
) : ViewModel() {

    val filter = MutableStateFlow("USDT")
    val query = MutableStateFlow("")

    val ui = combine(
        market.pairs,
        market.tickers,
        market.liveTicks.sample(500),
        filter,
        query,
    ) { pairs, tickers, live, filter, query ->
        val tickerMap = tickers.associateBy { it.symbol } + live
        val rows = pairs
            .filter {
                when (filter) {
                    "Favorites" -> it.isFavorite
                    "FIAT" -> it.quote in setOf("EUR", "TRY")
                    else -> it.quote == filter
                }
            }
            .filter { query.isBlank() || it.symbol.contains(query.trim(), ignoreCase = true) }
            .map { MarketRow(it, tickerMap[it.symbol]) }
            .sortedByDescending { it.ticker?.volume ?: 0.0 }
            .take(150)
        val movers = pairs.filter { it.quote == "USDT" }
            .map { MarketRow(it, tickerMap[it.symbol]) }
            .filter { it.ticker != null }
            .sortedByDescending { kotlin.math.abs(it.ticker!!.changePct) }
            .take(10)
        MarketsUi(
            loading = pairs.isEmpty(),
            rows = rows,
            movers = movers,
            filter = filter,
            query = query,
            lastUpdated = tickerMap.values.maxOfOrNull { it.updatedAt },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarketsUi())

    fun toggleFavorite(symbol: String) = viewModelScope.launch { market.toggleFavorite(symbol) }
}

private val QUOTE_FILTERS = listOf("USDT", "BTC", "ETH", "FIAT", "Favorites")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketsScreen(
    onOpenPair: (String) -> Unit,
    viewModel: MarketsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "search") {
            OutlinedTextField(
                value = ui.query,
                onValueChange = { viewModel.query.value = it },
                placeholder = { Text("Search pairs") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                supportingText = {
                    ui.lastUpdated?.let {
                        Text("Last updated " + SimpleDateFormat("HH:mm", Locale.US).format(Date(it)))
                    } ?: Text("Reconnecting…")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }
        item(key = "filters") {
            // Mandatory LazyRow: 5+ dynamic-width chips never fit compact screens.
            val chipState = rememberLazyListState()
            LazyRow(
                state = chipState,
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(items = QUOTE_FILTERS, key = { it }) { f ->
                    FilterChip(
                        selected = ui.filter == f,
                        onClick = { viewModel.filter.value = f },
                        label = { Text(f) },
                    )
                }
            }
        }
        if (ui.movers.isNotEmpty()) {
            item(key = "movers_header") {
                Text(
                    "Top movers",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                )
            }
            item(key = "movers") {
                // Mandatory LazyRow: fixed 180dp cards, 3+ never fit.
                val moverState = rememberLazyListState()
                LazyRow(
                    state = moverState,
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(items = ui.movers, key = { it.pair.symbol }) { mover ->
                        Card(
                            onClick = { onOpenPair(mover.pair.symbol) },
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.width(180.dp),
                        ) {
                            Column(Modifier.padding(Spacing.lg)) {
                                Text(mover.pair.symbol, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    formatPrice(mover.ticker?.last ?: 0.0),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                ChangeText(mover.ticker?.changePct ?: 0.0)
                            }
                        }
                    }
                }
            }
        }
        item(key = "all_header") {
            Text(
                "All pairs",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            )
        }
        if (ui.loading) {
            items(items = (0..7).toList(), key = { "skeleton_$it" }) { SkeletonRow() }
        } else if (ui.rows.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    headline = if (ui.filter == "Favorites") "Add your first pair" else "No pairs found",
                    body = if (ui.filter == "Favorites") "Star a pair to keep it here." else "Try a different filter or search term.",
                )
            }
        } else {
            items(items = ui.rows, key = { it.pair.symbol }) { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPair(row.pair.symbol) }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                ) {
                    IconButton(onClick = { viewModel.toggleFavorite(row.pair.symbol) }) {
                        if (row.pair.isFavorite) {
                            Icon(Icons.Filled.Star, contentDescription = "Remove ${row.pair.symbol} from favorites", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Outlined.StarBorder, contentDescription = "Add ${row.pair.symbol} to favorites")
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = Spacing.sm)) {
                        Text(row.pair.symbol, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${row.pair.base}/${row.pair.quote}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            row.ticker?.let { formatPrice(it.last) } ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        row.ticker?.let { ChangeText(it.changePct) }
                    }
                }
            }
        }
        item(key = "footer") {
            StatusChip(
                "Market data: Binance public API — display only, no real trading.",
                modifier = Modifier.padding(Spacing.lg),
            )
        }
    }
}
