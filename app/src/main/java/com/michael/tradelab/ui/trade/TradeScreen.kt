package com.michael.tradelab.ui.trade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michael.tradelab.R
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.michael.tradelab.data.repo.MarketRepository
import com.michael.tradelab.data.repo.PortfolioRepository
import com.michael.tradelab.data.repo.SettingsRepository
import com.michael.tradelab.domain.model.AppError
import com.michael.tradelab.domain.model.OrderSide
import com.michael.tradelab.domain.model.OrderType
import com.michael.tradelab.domain.model.Position
import com.michael.tradelab.domain.model.Ticker
import com.michael.tradelab.domain.model.TpSlMode
import com.michael.tradelab.domain.model.TpSlSpec
import com.michael.tradelab.domain.usecase.PlaceVirtualOrderUseCase
import com.michael.tradelab.service.PositionMonitorService
import com.michael.tradelab.ui.chart.CandleChart
import com.michael.tradelab.ui.components.ChangeText
import com.michael.tradelab.ui.components.DisclaimerBanner
import com.michael.tradelab.ui.components.formatPrice
import com.michael.tradelab.ui.components.formatUsd
import com.michael.tradelab.ui.theme.Spacing
import com.michael.tradelab.ui.theme.gainColor
import com.michael.tradelab.ui.theme.lossColor
import com.michael.tradelab.domain.model.Candle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TradeUi(
    val symbol: String = "BTCUSDT",
    val ticker: Ticker? = null,
    val ticks: Map<String, Ticker> = emptyMap(),
    val candles: List<Candle> = emptyList(),
    val interval: String = "1h",
    val cash: Double = 0.0,
    val positions: List<Position> = emptyList(),
    val message: String? = null,
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TradeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val market: MarketRepository,
    portfolio: PortfolioRepository,
    private val placeOrder: PlaceVirtualOrderUseCase,
    private val portfolioRepo: PortfolioRepository,
    private val settings: SettingsRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val notificationDenialCount = settings.notificationDenialCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun recordNotificationDenial() = viewModelScope.launch { settings.recordNotificationDenial() }

    val symbol: String = savedStateHandle.get<String>("symbol") ?: "BTCUSDT"
    val interval = MutableStateFlow("1h")
    private val message = MutableStateFlow<String?>(null)

    private val candles = interval.flatMapLatest { iv ->
        viewModelScope.launch { runCatching { market.fetchCandles(symbol, iv) } }
        market.observeCandles(symbol, iv)
    }

    val ui = combine(
        market.liveTicks.sample(250),
        candles,
        interval,
        combine(portfolio.wallet, portfolio.positions) { w, p -> w to p },
        message,
    ) { ticks, candles, iv, (wallet, positions), msg ->
        TradeUi(
            symbol = symbol,
            ticker = ticks[symbol],
            ticks = ticks,
            candles = candles,
            interval = iv,
            cash = wallet.cashBalance,
            positions = positions,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TradeUi(symbol = symbol))

    fun place(
        side: OrderSide,
        type: OrderType,
        amountUsdt: Double?,
        leverage: Int,
        limit: Double?,
        takeProfit: TpSlSpec?,
        stopLoss: TpSlSpec?,
    ) {
        if (amountUsdt == null || amountUsdt <= 0.0) { message.value = "Enter a valid USDT amount"; return }
        viewModelScope.launch {
            message.value = when (val r = placeOrder(symbol, side, type, amountUsdt, leverage, limit, takeProfit, stopLoss)) {
                is PlaceVirtualOrderUseCase.Result.Filled -> {
                    if (portfolioRepo.positions.first().isNotEmpty()) {
                        PositionMonitorService.start(appContext)
                    }
                    "Virtual ${side.name.lowercase()} filled at ${formatPrice(r.order.fillPrice ?: 0.0)}"
                }
                is PlaceVirtualOrderUseCase.Result.Queued -> "Virtual limit order queued"
                is PlaceVirtualOrderUseCase.Result.Rejected -> when (val e = r.error) {
                    AppError.InsufficientVirtualBalance -> "Insufficient virtual balance or holdings"
                    AppError.StaleData -> "Waiting for live price — try again in a moment"
                    is AppError.Unknown -> e.message ?: "Order could not be placed"
                    else -> "Order could not be placed"
                }
            }
        }
    }

    /** Market-close the full position at the latest live tick. */
    fun closePosition(position: Position) = viewModelScope.launch {
        val price = market.liveTicks.value[position.symbol]?.last
        if (price == null) {
            message.value = "Waiting for live price — try again in a moment"
            return@launch
        }
        portfolioRepo.applyFill(
            com.michael.tradelab.domain.model.VirtualOrder(
                symbol = position.symbol,
                side = if (position.isShort) OrderSide.BUY else OrderSide.SELL,
                type = OrderType.MARKET,
                qty = position.absQty,
                limitPrice = null,
                fillPrice = null,
                status = com.michael.tradelab.domain.model.OrderStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                filledAt = null,
                leverage = position.leverage,
            ),
            price,
        )
        message.value = "Virtual position ${position.symbol} closed at ${formatPrice(price)}"
    }

    fun resetPortfolio() = viewModelScope.launch {
        portfolioRepo.reset()
        message.value = "Virtual portfolio reset to ${formatUsd(PortfolioRepository.STARTING_BALANCE)}"
    }
}

private val TIMEFRAMES = listOf("1m", "5m", "15m", "1h", "4h", "1d", "1w")
private val LEVERAGES = listOf(1, 2, 5, 10, 25, 50)

private fun tpSlUnit(mode: TpSlMode) = when (mode) {
    TpSlMode.PRICE -> "price"
    TpSlMode.PERCENT -> "% ROI"
    TpSlMode.PNL -> "USDT"
}

private fun tpSlHint(mode: TpSlMode) = when (mode) {
    TpSlMode.PRICE -> "Exact trigger price"
    TpSlMode.PERCENT -> "Return on margin, leverage-adjusted"
    TpSlMode.PNL -> "Profit or loss target in USDT"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(symbol: String, viewModel: TradeViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    var side by remember { mutableStateOf(OrderSide.BUY) }
    var type by remember { mutableStateOf(OrderType.MARKET) }
    var amountText by remember { mutableStateOf("") }
    var leverage by remember { mutableStateOf(1) }
    var limitText by remember { mutableStateOf("") }
    var tpText by remember { mutableStateOf("") }
    var slText by remember { mutableStateOf("") }
    var tpSlMode by remember { mutableStateOf(TpSlMode.PERCENT) }
    var showReset by remember { mutableStateOf(false) }
    var showNotifRationale by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val denials by viewModel.notificationDenialCount.collectAsState()
    val needsNotifPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) viewModel.recordNotificationDenial()
    }

    Column(Modifier.fillMaxSize()) {
        DisclaimerBanner(stringResource(R.string.disclaimer_banner))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                ) {
                    Column {
                        Text(ui.symbol, style = MaterialTheme.typography.titleLarge)
                        ui.ticker?.let { ChangeText(it.changePct) }
                    }
                    Text(
                        ui.ticker?.let { formatPrice(it.last) } ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
            item(key = "timeframes") {
                // Mandatory LazyRow: 7 timeframe chips guaranteed to overflow compact width.
                val tfState = rememberLazyListState()
                LazyRow(
                    state = tfState,
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(items = TIMEFRAMES, key = { it }) { tf ->
                        FilterChip(
                            selected = ui.interval == tf,
                            onClick = { viewModel.interval.value = tf },
                            label = { Text(tf) },
                        )
                    }
                }
            }
            item(key = "chart") {
                CandleChart(
                    candles = ui.candles,
                    modifier = Modifier.padding(Spacing.lg),
                )
            }
            item(key = "ticket") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                ) {
                    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text("Virtual order ticket", style = MaterialTheme.typography.titleMedium)
                        // Buy/Sell segmented control — semantic green/red, never brand teal.
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = side == OrderSide.BUY,
                                onClick = { side = OrderSide.BUY },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = gainColor().copy(alpha = 0.2f),
                                    activeContentColor = gainColor(),
                                ),
                            ) { Text("Buy") }
                            SegmentedButton(
                                selected = side == OrderSide.SELL,
                                onClick = { side = OrderSide.SELL },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = lossColor().copy(alpha = 0.2f),
                                    activeContentColor = lossColor(),
                                ),
                            ) { Text("Sell") }
                        }
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = type == OrderType.MARKET,
                                onClick = { type = OrderType.MARKET },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                            ) { Text("Market") }
                            SegmentedButton(
                                selected = type == OrderType.LIMIT,
                                onClick = { type = OrderType.LIMIT },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                            ) { Text("Limit") }
                        }
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Amount (USDT)") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            supportingText = {
                                val amount = amountText.toDoubleOrNull()
                                val refPrice = if (type == OrderType.LIMIT) limitText.toDoubleOrNull() else ui.ticker?.last
                                val qty = if (amount != null && refPrice != null && refPrice > 0)
                                    amount * leverage / refPrice else null
                                Text(
                                    "Virtual cash: ${formatUsd(ui.cash)}" +
                                        (qty?.let { " · ≈ ${formatPrice(it)} ${ui.symbol.removeSuffix("USDT")}" } ?: "")
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Column {
                            Text(
                                "Leverage (simulated)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // Mandatory LazyRow: 6 leverage chips overflow compact width.
                            val levState = rememberLazyListState()
                            LazyRow(
                                state = levState,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                            ) {
                                items(items = LEVERAGES, key = { it }) { lev ->
                                    FilterChip(
                                        selected = leverage == lev,
                                        onClick = { leverage = lev },
                                        label = { Text("${lev}x") },
                                    )
                                }
                            }
                        }
                        if (type == OrderType.LIMIT) {
                            OutlinedTextField(
                                value = limitText,
                                onValueChange = { limitText = it },
                                label = { Text("Limit price") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.small,
                                supportingText = { Text("Fills when the live price crosses this level") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        run {
                            Text(
                                "Take profit / stop loss (optional)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                TpSlMode.entries.forEachIndexed { i, mode ->
                                    SegmentedButton(
                                        selected = tpSlMode == mode,
                                        onClick = { tpSlMode = mode },
                                        shape = SegmentedButtonDefaults.itemShape(i, TpSlMode.entries.size),
                                    ) {
                                        Text(
                                            when (mode) {
                                                TpSlMode.PRICE -> "Price"
                                                TpSlMode.PERCENT -> "%"
                                                TpSlMode.PNL -> "PnL"
                                            }
                                        )
                                    }
                                }
                            }
                            val entry = if (type == OrderType.LIMIT) limitText.toDoubleOrNull() else ui.ticker?.last
                            val amount = amountText.toDoubleOrNull()
                            fun preview(raw: String, isTp: Boolean): String {
                                val v = raw.toDoubleOrNull() ?: return tpSlHint(tpSlMode)
                                if (entry == null || entry <= 0 || amount == null || amount <= 0) return tpSlHint(tpSlMode)
                                val qty = amount * leverage / entry
                                val trigger = PlaceVirtualOrderUseCase.resolveTriggerPrice(
                                    TpSlSpec(tpSlMode, v), entry, qty, leverage, isTp, short = side == OrderSide.SELL,
                                )
                                return "Triggers at ${formatPrice(trigger)}"
                            }
                            OutlinedTextField(
                                value = tpText,
                                onValueChange = { tpText = it },
                                label = { Text("Take profit (${tpSlUnit(tpSlMode)})") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.small,
                                supportingText = { Text(preview(tpText, isTp = true)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = slText,
                                onValueChange = { slText = it },
                                label = { Text("Stop loss (${tpSlUnit(tpSlMode)})") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.small,
                                supportingText = { Text(preview(slText, isTp = false)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        ui.message?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                val tp = tpText.toDoubleOrNull()?.let { TpSlSpec(tpSlMode, it) }
                                val sl = slText.toDoubleOrNull()?.let { TpSlSpec(tpSlMode, it) }
                                viewModel.place(side, type, amountText.toDoubleOrNull(), leverage, limitText.toDoubleOrNull(), tp, sl)
                                // Position monitor and TP/SL closes use notifications; ask contextually, max twice.
                                if (needsNotifPermission && denials < 2) {
                                    showNotifRationale = true
                                }
                            },
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        ) { Text(stringResource(R.string.place_virtual_order)) }
                    }
                }
            }
            item(key = "positions_header") {
                Text(
                    "Open positions (simulated)",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                )
            }
            if (ui.positions.isEmpty()) {
                item(key = "no_positions") {
                    Text(
                        "No open virtual positions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            } else {
                items(items = ui.positions, key = { it.symbol }) { pos ->
                    val last = ui.ticks[pos.symbol]?.last
                    val unrealized = last?.let { pos.unrealizedPnl(it) }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    ) {
                        Column {
                            Text(
                                "${pos.symbol} · ${if (pos.isShort) "SHORT" else "LONG"} ${pos.leverage}x",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (pos.isShort) lossColor() else gainColor(),
                            )
                            Text(
                                "${formatPrice(pos.absQty)} @ ${formatPrice(pos.avgEntry)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Tick ${last?.let { formatPrice(it) } ?: "waiting for live price"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val riskLine = listOfNotNull(
                                pos.tpPrice?.let { "TP ${formatPrice(it)}" },
                                pos.slPrice?.let { "SL ${formatPrice(it)}" },
                                pos.liquidationPrice()?.let { "Liq ${formatPrice(it)}" },
                            )
                            if (riskLine.isNotEmpty()) {
                                Text(
                                    riskLine.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text(
                                unrealized?.let { (if (it >= 0) "▲ " else "▼ ") + formatUsd(it) } ?: "—",
                                style = MaterialTheme.typography.titleMedium,
                                color = unrealized?.let { if (it >= 0) gainColor() else lossColor() }
                                    ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            M3TextButton(onClick = { viewModel.closePosition(pos) }) { Text("Close") }
                        }
                    }
                }
            }
            item(key = "reset") {
                OutlinedButton(
                    onClick = { showReset = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = lossColor()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                ) { Text(stringResource(R.string.reset_portfolio)) }
            }
        }
    }

    if (showNotifRationale) {
        ModalBottomSheet(onDismissRequest = { showNotifRationale = false }) {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Show the position monitor?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "TradeLab shows an ongoing notification while simulated positions are open, with a Stop button, and can notify you when TP, SL, or liquidation closes a position. Notifications are informational only — nothing is ever traded on your behalf.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(onClick = {
                        showNotifRationale = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }) { Text("Grant") }
                    M3TextButton(onClick = { showNotifRationale = false }) { Text("Not now") }
                }
            }
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset virtual portfolio?") },
            text = { Text("This clears all simulated positions and order history and restores the virtual ${formatUsd(PortfolioRepository.STARTING_BALANCE)} balance. Nothing real is affected.") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetPortfolio(); showReset = false }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("Cancel") } },
        )
    }
}
