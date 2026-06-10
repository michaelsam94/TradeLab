package com.michael.tradelab.worker

import android.Manifest
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.michael.tradelab.MainActivity
import com.michael.tradelab.R
import com.michael.tradelab.TradeLabApp
import com.michael.tradelab.data.repo.AlertRepository
import com.michael.tradelab.data.repo.MarketRepository
import com.michael.tradelab.data.repo.PortfolioRepository
import com.michael.tradelab.domain.indicator.IndicatorMath
import com.michael.tradelab.domain.model.Alert
import com.michael.tradelab.domain.model.AlertKind
import com.michael.tradelab.domain.usecase.CloseTpSlPositionsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Periodic (15 min, inexact) background evaluation against REST snapshots:
 * 1. TP/SL/liquidation closes on open virtual positions (with notifications).
 * 2. Price/RSI alerts.
 * Notification copy is strictly factual — never a trading instruction.
 */
@HiltWorker
class AlertEvaluationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val alerts: AlertRepository,
    private val market: MarketRepository,
    private val portfolio: PortfolioRepository,
    private val closeTpSlPositions: CloseTpSlPositionsUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val active = alerts.activeAlerts()
        val hasPositions = portfolio.positions.first().isNotEmpty()
        if (active.isEmpty() && !hasPositions) return Result.success()

        runCatching { market.refreshTickersSnapshot() }.getOrElse { return Result.retry() }

        // Evaluate TP/SL/liquidation against the snapshot — the use case posts
        // its own per-position notifications.
        if (hasPositions) {
            val ticks = market.tickers.first().associateBy { it.symbol }
            runCatching { closeTpSlPositions(ticks) }
        }

        val triggered = mutableListOf<Alert>()
        for (alert in active) {
            val hit = when (alert.kind) {
                AlertKind.PRICE_ABOVE, AlertKind.PRICE_BELOW -> {
                    val price = latestPrice(alert.symbol) ?: continue
                    if (alert.kind == AlertKind.PRICE_ABOVE) price >= alert.level else price <= alert.level
                }
                AlertKind.RSI_ABOVE, AlertKind.RSI_BELOW -> {
                    val closes = runCatching {
                        market.fetchCandles(alert.symbol, alert.interval, 100).map { it.close }
                    }.getOrNull() ?: continue
                    val rsi = IndicatorMath.rsi(closes).lastOrNull { !it.isNaN() } ?: continue
                    if (alert.kind == AlertKind.RSI_ABOVE) rsi >= alert.level else rsi <= alert.level
                }
            }
            if (hit) {
                alerts.markTriggered(alert.id)
                triggered += alert
            }
        }
        if (triggered.isNotEmpty()) notify(triggered)
        return Result.success()
    }

    private suspend fun latestPrice(symbol: String): Double? =
        market.tickers.first().firstOrNull { it.symbol == symbol }?.last

    private fun notify(triggered: List<Alert>) {
        val ctx = applicationContext
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        // API 35 cooldown friendliness: batch all triggers into one summary notification.
        val first = triggered.first()
        val text = if (triggered.size == 1) describe(first)
        else triggered.joinToString("\n") { describe(it) }

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("tradelab://pair/${first.symbol}"),
            ctx,
            MainActivity::class.java,
        )
        val pending: PendingIntent = TaskStackBuilder.create(ctx).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(first.id.toInt(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)!!
        }

        val notification = NotificationCompat.Builder(ctx, TradeLabApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (triggered.size == 1) "Alert level crossed" else "${triggered.size} alert levels crossed")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentText(text.lineSequence().first())
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification)
    }

    private fun describe(alert: Alert): String = when (alert.kind) {
        AlertKind.PRICE_ABOVE, AlertKind.PRICE_BELOW -> "${alert.symbol} crossed your ${alert.level} level"
        AlertKind.RSI_ABOVE -> "${alert.symbol} RSI(14) ${alert.interval} rose past ${alert.level}"
        AlertKind.RSI_BELOW -> "${alert.symbol} RSI(14) ${alert.interval} fell below ${alert.level}"
    }

    companion object {
        const val WORK_NAME = "alert_evaluation"
        const val NOTIFICATION_ID = 1001
    }
}
