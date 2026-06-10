package com.michael.tradelab.notifications

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
import com.michael.tradelab.MainActivity
import com.michael.tradelab.R
import com.michael.tradelab.TradeLabApp
import com.michael.tradelab.domain.model.Position
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Why a position was auto-closed by the tick engine. */
enum class CloseReason { TAKE_PROFIT, STOP_LOSS, LIQUIDATION }

/**
 * Posts a factual notification when a simulated position is auto-closed.
 * Copy is informational only — never a trading instruction.
 */
@Singleton
class PositionCloseNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyClosed(position: Position, reason: CloseReason, triggerPrice: Double) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val direction = if (position.isShort) "short" else "long"
        val pnl = position.unrealizedPnl(triggerPrice)
        val pnlText = String.format(Locale.US, "%+,.2f USDT (simulated)", pnl)
        val title = when (reason) {
            CloseReason.TAKE_PROFIT -> "Take profit reached — ${position.symbol}"
            CloseReason.STOP_LOSS -> "Stop loss reached — ${position.symbol}"
            CloseReason.LIQUIDATION -> "Position liquidated — ${position.symbol}"
        }
        val body = "Your virtual ${position.leverage}x $direction position closed at " +
            String.format(Locale.US, "%,.2f", triggerPrice) + ". Realized P/L: $pnlText."

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("tradelab://pair/${position.symbol}"),
            context,
            MainActivity::class.java,
        )
        val pending: PendingIntent = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(
                position.symbol.hashCode(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )!!
        }

        val notification = NotificationCompat.Builder(context, TradeLabApp.CHANNEL_POSITIONS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_BASE + (position.symbol.hashCode() and 0xFFFF), notification)
    }

    companion object {
        const val NOTIFICATION_ID_BASE = 2000
    }
}
