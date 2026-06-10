package com.michael.tradelab.domain.usecase

import com.michael.tradelab.domain.model.VirtualOrder
import javax.inject.Inject

/** Performance stats over filled virtual orders — all values are simulated. */
class ComputePerformanceStatsUseCase @Inject constructor() {

    data class Stats(
        val realizedPnl: Double,
        val winRate: Double,
        val closedTrades: Int,
        val maxDrawdownPct: Double,
        val equityCurve: List<Double>,
    )

    operator fun invoke(filledOrders: List<VirtualOrder>, startingBalance: Double): Stats {
        // Closing fills carry realized P/L — either side can close under netting.
        val sells = filledOrders.filter { it.reducedQty > 0 }.sortedBy { it.filledAt ?: 0 }
        val realized = sells.sumOf { it.realizedPnl }
        val wins = sells.count { it.realizedPnl > 0 }

        var equity = startingBalance
        var peak = startingBalance
        var maxDd = 0.0
        val curve = mutableListOf(startingBalance)
        for (s in sells) {
            equity += s.realizedPnl
            peak = maxOf(peak, equity)
            if (peak > 0) maxDd = maxOf(maxDd, (peak - equity) / peak)
            curve += equity
        }
        return Stats(
            realizedPnl = realized,
            winRate = if (sells.isNotEmpty()) wins.toDouble() / sells.size else 0.0,
            closedTrades = sells.size,
            maxDrawdownPct = maxDd * 100.0,
            equityCurve = curve,
        )
    }
}
