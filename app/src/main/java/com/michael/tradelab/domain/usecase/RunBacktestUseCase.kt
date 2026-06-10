package com.michael.tradelab.domain.usecase

import com.michael.tradelab.data.repo.MarketRepository
import com.michael.tradelab.domain.indicator.IndicatorMath
import com.michael.tradelab.domain.model.Candle
import com.michael.tradelab.domain.model.IndicatorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Educational backtest: replays a simple enter-on-signal / exit-on-opposite rule
 * over cached candles. Deterministic for a given candle set.
 */
class RunBacktestUseCase @Inject constructor(
    private val market: MarketRepository,
) {
    data class BacktestResult(
        val trades: Int,
        val winRate: Double,
        val totalReturnPct: Double,
        val maxDrawdownPct: Double,
        val equityCurve: List<Double>,
    )

    suspend operator fun invoke(symbol: String, interval: String, type: IndicatorType): BacktestResult =
        withContext(Dispatchers.Default) {
            val candles = market.getCachedCandles(symbol, interval)
                .ifEmpty { withContext(Dispatchers.IO) { market.fetchCandles(symbol, interval) } }
            backtest(candles, type)
        }

    companion object {
        fun backtest(candles: List<Candle>, type: IndicatorType): BacktestResult {
            val closes = candles.map { it.close }
            val signals: List<Int> = when (type) { // 1 = long signal, -1 = exit/short signal, 0 = hold
                IndicatorType.RSI -> IndicatorMath.rsi(closes).map { if (it.isNaN()) 0 else if (it < 30) 1 else if (it > 70) -1 else 0 }
                IndicatorType.MACD -> IndicatorMath.macd(closes).histogram.map { if (it.isNaN()) 0 else if (it > 0) 1 else -1 }
                IndicatorType.MA_CONFLUENCE -> {
                    val e20 = IndicatorMath.ema(closes, 20); val e50 = IndicatorMath.ema(closes, 50)
                    closes.indices.map { i -> if (e20[i].isNaN() || e50[i].isNaN()) 0 else if (e20[i] > e50[i]) 1 else -1 }
                }
                IndicatorType.MOMENTUM -> IndicatorMath.momentum(closes).map { if (it.isNaN()) 0 else if (it > 1) 1 else if (it < -1) -1 else 0 }
            }

            var equity = 1.0
            var entry = -1.0
            var wins = 0
            var trades = 0
            var peak = 1.0
            var maxDd = 0.0
            val curve = mutableListOf(1.0)

            for (i in closes.indices) {
                if (entry < 0 && signals[i] == 1) entry = closes[i]
                else if (entry > 0 && signals[i] == -1) {
                    val r = closes[i] / entry
                    equity *= r
                    trades++
                    if (r > 1.0) wins++
                    entry = -1.0
                }
                val mark = if (entry > 0) equity * closes[i] / entry else equity
                peak = maxOf(peak, mark)
                maxDd = maxOf(maxDd, (peak - mark) / peak)
                curve += mark
            }
            return BacktestResult(
                trades = trades,
                winRate = if (trades > 0) wins.toDouble() / trades else 0.0,
                totalReturnPct = (equity - 1.0) * 100.0,
                maxDrawdownPct = maxDd * 100.0,
                equityCurve = curve,
            )
        }
    }
}
