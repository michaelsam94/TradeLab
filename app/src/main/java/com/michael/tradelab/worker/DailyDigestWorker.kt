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
import com.michael.tradelab.domain.usecase.ComputeIndicatorsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Daily digest: computes indicator readouts for a few majors and posts one factual summary. */
@HiltWorker
class DailyDigestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val computeIndicators: ComputeIndicatorsUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val symbols = listOf("BTCUSDT", "ETHUSDT", "BNBUSDT")
        val lines = symbols.mapNotNull { symbol ->
            runCatching { computeIndicators(symbol, "4h") }.getOrNull()
                ?.maxByOrNull { it.strength }
                ?.let { "${it.symbol}: ${it.detail}" }
        }
        if (lines.isEmpty()) return Result.retry()

        val ctx = applicationContext
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tradelab://indicators"), ctx, MainActivity::class.java)
        val pending: PendingIntent = TaskStackBuilder.create(ctx).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)!!
        }
        val notification = NotificationCompat.Builder(ctx, TradeLabApp.CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Daily indicator digest")
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentText(lines.first())
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "daily_digest"
        const val NOTIFICATION_ID = 1002
    }
}
