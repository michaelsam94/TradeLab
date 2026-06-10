package com.michael.tradelab.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import com.michael.tradelab.service.PositionMonitorService
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.michael.tradelab.data.repo.BillingRepository
import com.michael.tradelab.data.repo.MarketRepository
import com.michael.tradelab.data.repo.PortfolioRepository
import com.michael.tradelab.data.repo.SettingsRepository
import com.michael.tradelab.domain.usecase.CloseTpSlPositionsUseCase
import com.michael.tradelab.domain.usecase.FillPendingLimitOrdersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val market: MarketRepository,
    private val portfolio: PortfolioRepository,
    private val billing: BillingRepository,
    settings: SettingsRepository,
    private val fillPendingLimitOrders: FillPendingLimitOrdersUseCase,
    private val closeTpSlPositions: CloseTpSlPositionsUseCase,
    private val workManager: WorkManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val disclaimerAccepted = settings.disclaimerAccepted
        .stateIn(viewModelScope, SharingStarted.Eagerly, null as Boolean?)

    init {
        viewModelScope.launch {
            portfolio.ensureWallet()
            runCatching { market.refreshPairs() }
            runCatching { market.refreshTickersSnapshot() }
            // Resume the position monitor if positions were open when the app last closed.
            if (portfolio.positions.first().isNotEmpty()) {
                PositionMonitorService.start(appContext)
            }
        }
        // Limit fills are evaluated on incoming ticks while the app is open.
        viewModelScope.launch {
            market.liveTicks.sample(1_000).collect { ticks ->
                if (ticks.isNotEmpty()) {
                    runCatching { fillPendingLimitOrders(ticks) }
                    if (portfolio.positions.first().isNotEmpty()) {
                        PositionMonitorService.start(appContext)
                    }
                    runCatching { closeTpSlPositions(ticks) }
                }
            }
        }
        billing.connect()
        scheduleWorkers()
    }

    fun onForeground() = market.startStream()

    fun onBackground() {
        viewModelScope.launch {
            // The position-monitor foreground service owns the stream while
            // positions are open; only stop it when nothing is being monitored.
            if (portfolio.positions.first().isEmpty()) market.stopStream()
        }
    }

    private fun scheduleWorkers() {
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        workManager.enqueueUniquePeriodicWork(
            com.michael.tradelab.worker.AlertEvaluationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<com.michael.tradelab.worker.AlertEvaluationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network)
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            com.michael.tradelab.worker.DailyDigestWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<com.michael.tradelab.worker.DailyDigestWorker>(24, TimeUnit.HOURS)
                .setConstraints(network)
                .build(),
        )
    }
}
