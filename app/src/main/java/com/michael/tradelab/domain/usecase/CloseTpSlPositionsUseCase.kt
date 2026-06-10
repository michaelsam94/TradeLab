package com.michael.tradelab.domain.usecase

import com.michael.tradelab.data.repo.PortfolioRepository
import com.michael.tradelab.notifications.CloseReason
import com.michael.tradelab.notifications.PositionCloseNotifier
import com.michael.tradelab.domain.model.OrderSide
import com.michael.tradelab.domain.model.OrderStatus
import com.michael.tradelab.domain.model.OrderType
import com.michael.tradelab.domain.model.Position
import com.michael.tradelab.domain.model.Ticker
import com.michael.tradelab.domain.model.VirtualOrder
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Tick-driven position management, evaluated in priority order:
 * 1. Liquidation — unrealized loss has consumed the locked margin; the full
 *    position is force-closed at the liquidation price (margin wiped).
 * 2. Take-profit / stop-loss — full close at the trigger price.
 * Longs close with a simulated SELL, shorts with a simulated BUY.
 */
class CloseTpSlPositionsUseCase @Inject constructor(
    private val portfolio: PortfolioRepository,
    private val notifier: PositionCloseNotifier,
) {
    suspend operator fun invoke(ticks: Map<String, Ticker>) {
        for (position in portfolio.positions.first()) {
            val price = ticks[position.symbol]?.last ?: continue
            val liquidation = liquidationTrigger(position, price)
            val trigger = liquidation
                ?: tpSlTrigger(position, price)
                ?: continue
            val reason = when {
                liquidation != null -> CloseReason.LIQUIDATION
                trigger == position.tpPrice -> CloseReason.TAKE_PROFIT
                else -> CloseReason.STOP_LOSS
            }
            portfolio.applyFill(
                VirtualOrder(
                    symbol = position.symbol,
                    side = if (position.isShort) OrderSide.BUY else OrderSide.SELL,
                    type = OrderType.MARKET,
                    qty = position.absQty,
                    limitPrice = null,
                    fillPrice = null,
                    status = OrderStatus.PENDING,
                    createdAt = System.currentTimeMillis(),
                    filledAt = null,
                    leverage = position.leverage,
                ),
                trigger,
            )
            notifier.notifyClosed(position, reason, trigger)
        }
    }

    companion object {
        fun liquidationTrigger(position: Position, price: Double): Double? {
            val liq = position.liquidationPrice() ?: return null
            val hit = if (position.isShort) price >= liq else price <= liq
            return if (hit) liq else null
        }

        fun tpSlTrigger(position: Position, price: Double): Double? = when {
            position.tpPrice != null &&
                (if (position.isShort) price <= position.tpPrice else price >= position.tpPrice) -> position.tpPrice
            position.slPrice != null &&
                (if (position.isShort) price >= position.slPrice else price <= position.slPrice) -> position.slPrice
            else -> null
        }
    }
}
