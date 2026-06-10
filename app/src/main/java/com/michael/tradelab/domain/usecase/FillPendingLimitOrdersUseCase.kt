package com.michael.tradelab.domain.usecase

import com.michael.tradelab.data.repo.PortfolioRepository
import com.michael.tradelab.domain.model.OrderSide
import com.michael.tradelab.domain.model.Ticker
import javax.inject.Inject

/** Tick-driven limit fills: buy fills when price <= limit, sell when price >= limit. */
class FillPendingLimitOrdersUseCase @Inject constructor(
    private val portfolio: PortfolioRepository,
) {
    suspend operator fun invoke(ticks: Map<String, Ticker>) {
        for (order in portfolio.pendingOrders()) {
            val price = ticks[order.symbol]?.last ?: continue
            val limit = order.limitPrice ?: continue
            val fills = when (order.side) {
                OrderSide.BUY -> price <= limit
                OrderSide.SELL -> price >= limit
            }
            if (fills) {
                // Re-check funds at fill time; skip (keep pending) if no longer affordable.
                val affordable = when (order.side) {
                    OrderSide.BUY -> order.qty * limit / order.leverage <= portfolio.getWalletBalance() + 1e-9
                    OrderSide.SELL -> order.qty <= (portfolio.getPosition(order.symbol)?.qty ?: 0.0) + 1e-9
                }
                if (affordable) portfolio.applyFill(order, limit)
            }
        }
    }
}
