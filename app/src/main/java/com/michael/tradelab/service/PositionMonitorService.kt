package com.michael.tradelab.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.michael.tradelab.MainActivity
import com.michael.tradelab.R
import com.michael.tradelab.TradeLabApp
import com.michael.tradelab.data.repo.MarketRepository
import com.michael.tradelab.data.repo.PortfolioRepository
import com.michael.tradelab.domain.usecase.CloseTpSlPositionsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Foreground service (spec §16 guard-rail pattern): runs only while virtual
 * positions are open. Shows an ongoing, silent notification with live
 * unrealized P/L, a Stop action, and a deep-link content tap; keeps the tick
 * stream + TP/SL/liquidation engine alive in the background. Self-stops when
 * the last position closes.
 */
@OptIn(FlowPreview::class)
@AndroidEntryPoint
class PositionMonitorService : Service() {

    @Inject lateinit var market: MarketRepository
    @Inject lateinit var portfolio: PortfolioRepository
    @Inject lateinit var closeTpSlPositions: CloseTpSlPositionsUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning.set(true)
        startForeground(NOTIFICATION_ID, buildNotification("Connecting to live prices…"))
        market.startStream()
        scope.launch {
            combine(portfolio.positions, market.liveTicks.sample(2_000)) { positions, ticks ->
                positions to ticks
            }.collect { (positions, ticks) ->
                if (positions.isEmpty()) {
                    stopSelf()
                    return@collect
                }
                runCatching { closeTpSlPositions(ticks) }
                val priced = positions.mapNotNull { p -> ticks[p.symbol]?.last?.let { p.unrealizedPnl(it) } }
                val text = if (priced.size < positions.size) {
                    "${positions.size} open position(s) · waiting for live prices"
                } else {
                    String.format(
                        Locale.US,
                        "%d open position(s) · Unrealized P/L %+,.2f USDT (simulated)",
                        positions.size,
                        priced.sum(),
                    )
                }
                notifyUpdate(text)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        isRunning.set(false)
        scope.cancel()
        super.onDestroy()
    }

    private fun notifyUpdate(text: String) {
        getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tradelab://pair/BTCUSDT"), this, MainActivity::class.java)
        val tapPending: PendingIntent = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(tapIntent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)!!
        }
        val stopPending = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, ServiceControlReceiver::class.java).setAction(ServiceControlReceiver.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, TradeLabApp.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("TradeLab position monitor")
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(tapPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 3000
        private val isRunning = AtomicBoolean(false)

        fun start(context: Context) {
            if (!isRunning.compareAndSet(false, true)) return
            runCatching {
                context.startForegroundService(Intent(context, PositionMonitorService::class.java))
            }.onFailure {
                isRunning.set(false)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PositionMonitorService::class.java))
        }
    }
}
