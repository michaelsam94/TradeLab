package com.michael.tradelab.ui.indicators

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michael.tradelab.R
import com.michael.tradelab.data.local.IndicatorDao
import com.michael.tradelab.data.repo.MarketRepository
import com.michael.tradelab.domain.model.Bias
import com.michael.tradelab.domain.model.IndicatorReadout
import com.michael.tradelab.domain.model.IndicatorType
import com.michael.tradelab.domain.usecase.ComputeIndicatorsUseCase
import com.michael.tradelab.domain.usecase.RunBacktestUseCase
import com.michael.tradelab.ui.components.BiasChip
import com.michael.tradelab.ui.components.ConfidenceMeter
import com.michael.tradelab.ui.components.DisclaimerBanner
import com.michael.tradelab.ui.components.EmptyState
import com.michael.tradelab.ui.components.SkeletonRow
import com.michael.tradelab.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IndicatorFeedUi(
    val loading: Boolean = true,
    val readouts: List<IndicatorReadout> = emptyList(),
    val filter: IndicatorType? = null,
    val backtest: RunBacktestUseCase.BacktestResult? = null,
    val backtestRunning: Boolean = false,
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class IndicatorFeedViewModel @Inject constructor(
    indicatorDao: IndicatorDao,
    private val compute: ComputeIndicatorsUseCase,
    private val runBacktest: RunBacktestUseCase,
    private val marketRepository: MarketRepository,
) : ViewModel() {

    val filter = MutableStateFlow<IndicatorType?>(null)
    private val backtest = MutableStateFlow<RunBacktestUseCase.BacktestResult?>(null)
    private val backtestRunning = MutableStateFlow(false)
    private val loading = MutableStateFlow(true)

    private val readouts = indicatorDao.observeAll().map { list ->
        list.map {
            IndicatorReadout(
                it.id, it.symbol, it.interval, IndicatorType.valueOf(it.type),
                Bias.valueOf(it.bias), it.strength, it.value, it.detail, it.computedAt,
            )
        }
    }

    val ui = combine(readouts, filter, backtest, backtestRunning, loading) { r, f, bt, btr, l ->
        IndicatorFeedUi(
            loading = l && r.isEmpty(),
            readouts = (if (f == null) r else r.filter { it.type == f }).sortedByDescending { it.strength },
            filter = f,
            backtest = bt,
            backtestRunning = btr,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IndicatorFeedUi())

    init {
        refresh()
        // Periodic kline refresh keeps the candle series itself current.
        viewModelScope.launch {
            while (isActive) {
                delay(CANDLE_REFRESH_MS)
                computeAll(refreshCandles = true)
            }
        }
        // Live ticks overlay the streamed price onto the open candle so readouts
        // track the tape between candle closes.
        viewModelScope.launch {
            marketRepository.liveTicks.sample(LIVE_RECOMPUTE_MS).collect { ticks ->
                for (symbol in SYMBOLS) {
                    val price = ticks[symbol]?.last ?: continue
                    runCatching { compute(symbol, INTERVAL, livePrice = price) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            computeAll(refreshCandles = true)
            loading.value = false
        }
    }

    private suspend fun computeAll(refreshCandles: Boolean) {
        for (symbol in SYMBOLS) {
            runCatching { compute(symbol, INTERVAL, refreshCandles = refreshCandles) }
        }
    }

    fun backtest(symbol: String, type: IndicatorType) {
        viewModelScope.launch {
            backtestRunning.value = true
            backtest.value = runCatching { runBacktest(symbol, "4h", type) }.getOrNull()
            backtestRunning.value = false
        }
    }

    fun clearBacktest() { backtest.value = null }

    companion object {
        private val SYMBOLS = listOf("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT")
        private const val INTERVAL = "4h"
        private const val CANDLE_REFRESH_MS = 5 * 60_000L
        private const val LIVE_RECOMPUTE_MS = 10_000L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndicatorFeedScreen(
    onOpenPair: (String) -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: IndicatorFeedViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    var methodologyFor by remember { mutableStateOf<IndicatorReadout?>(null) }
    var backtestFor by remember { mutableStateOf<IndicatorReadout?>(null) }

    Column(Modifier.fillMaxSize()) {
        // Pinned, non-dismissible compliance banner.
        DisclaimerBanner(stringResource(R.string.disclaimer_banner))

        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "filters") {
                // Mandatory LazyRow: 5+ indicator-type chips.
                val chipState = rememberLazyListState()
                LazyRow(
                    state = chipState,
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.sm),
                ) {
                    items(items = listOf<IndicatorType?>(null) + IndicatorType.entries, key = { it?.name ?: "ALL" }) { t ->
                        FilterChip(
                            selected = ui.filter == t,
                            onClick = { viewModel.filter.value = t },
                            label = { Text(t?.name?.replace('_', ' ') ?: "All") },
                        )
                    }
                }
            }
            if (ui.loading) {
                items(items = (0..5).toList(), key = { "sk_$it" }) { SkeletonRow() }
            } else if (ui.readouts.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        headline = "No readouts yet",
                        body = "Indicator readouts appear once market data has been fetched.",
                        cta = { TextButton(onClick = viewModel::refresh) { Text("Refresh") } },
                    )
                }
            } else {
                items(items = ui.readouts, key = { "${it.symbol}:${it.type}" }) { r ->
                    Card(
                        onClick = { onOpenPair(r.symbol) },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    ) {
                        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("${r.symbol} · ${r.type.name.replace('_', ' ')}", style = MaterialTheme.typography.titleMedium)
                                BiasChip(r.bias)
                            }
                            Text(r.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ConfidenceMeter(r.strength, stringResource(R.string.confidence_label))
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                TextButton(onClick = { methodologyFor = r }) { Text("How is this computed?") }
                                TextButton(onClick = { backtestFor = r; viewModel.backtest(r.symbol, r.type) }) { Text("Educational backtest") }
                            }
                            Text(
                                stringResource(R.string.indicator_footer),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item(key = "pro") {
                    TextButton(
                        onClick = onOpenPaywall,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) { Text("Unlock the Pro indicator set") }
                }
            }
        }
    }

    methodologyFor?.let { r ->
        ModalBottomSheet(onDismissRequest = { methodologyFor = null }) {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Methodology — ${r.type.name.replace('_', ' ')}", style = MaterialTheme.typography.titleLarge)
                Text(methodologyText(r.type), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Inputs: ${r.detail}. Computed from cached ${r.interval} candles at ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(r.computedAt))}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.indicator_footer), style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    backtestFor?.let { r ->
        ModalBottomSheet(onDismissRequest = { backtestFor = null; viewModel.clearBacktest() }) {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Educational backtest — ${r.symbol}", style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.simulated_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ui.backtestRunning) {
                    Text("Replaying historical candles…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    ui.backtest?.let { bt ->
                        Text("Closed trades: ${bt.trades}", style = MaterialTheme.typography.bodyMedium)
                        Text("Historical win rate: ${"%.0f".format(bt.winRate * 100)}%", style = MaterialTheme.typography.bodyMedium)
                        Text("Total simulated return: ${"%+.1f".format(bt.totalReturnPct)}%", style = MaterialTheme.typography.bodyMedium)
                        Text("Max drawdown: ${"%.1f".format(bt.maxDrawdownPct)}%", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Past patterns do not determine future market behaviour.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } ?: Text("Backtest unavailable for this pair right now.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun methodologyText(type: IndicatorType): String = when (type) {
    IndicatorType.RSI -> "RSI(14) measures the relative size of recent gains versus losses using Wilder smoothing. Readings under 30 are conventionally described as oversold; over 70 as overbought."
    IndicatorType.MACD -> "MACD(12,26,9) subtracts the 26-period EMA from the 12-period EMA and compares the result with its own 9-period EMA (signal line). Histogram sign changes mark crossovers."
    IndicatorType.MA_CONFLUENCE -> "Compares last price against the 20- and 50-period EMAs and the EMAs against each other. 3/3 bullish conditions = bullish bias; 0/3 = bearish bias."
    IndicatorType.MOMENTUM -> "10-bar rate of change: percentage difference between the latest close and the close 10 bars earlier."
    IndicatorType.STOCHASTIC -> "Stochastic(14,3) locates the latest close within the 14-bar high–low range. %K under 20 is conventionally described as oversold; over 80 as overbought. %D is a 3-bar average of %K."
    IndicatorType.BOLLINGER -> "Bollinger Bands(20,2): a 20-period average ± 2 standard deviations. %B shows where price sits in the bands (0 = lower, 1 = upper). Narrow bandwidth flags a volatility squeeze."
    IndicatorType.VOLUME_TREND -> "On-balance volume adds volume on up-closes and subtracts it on down-closes, then is compared with its 20-period EMA. Agreement with the price trend confirms the move; disagreement flags divergence."
    IndicatorType.SMART_CONFLUENCE -> "Weighted blend of all component indicators into one score from -1 to +1. ADX(14) sets the regime: trending markets (ADX ≥ 25) weight trend-followers (MACD, EMA stack, momentum) up; ranging markets (ADX < 20) weight mean-reverters (RSI, stochastic, %B) up. Volume flow always counts once."
}
