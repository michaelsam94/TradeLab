package com.michael.tradelab.domain.usecase

import com.michael.tradelab.data.local.IndicatorDao
import com.michael.tradelab.data.local.IndicatorReadoutEntity
import com.michael.tradelab.data.repo.MarketRepository
import com.michael.tradelab.domain.indicator.IndicatorMath
import com.michael.tradelab.domain.model.Bias
import com.michael.tradelab.domain.model.Candle
import com.michael.tradelab.domain.model.IndicatorReadout
import com.michael.tradelab.domain.model.IndicatorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

class ComputeIndicatorsUseCase @Inject constructor(
    private val marketRepository: MarketRepository,
    private val indicatorDao: IndicatorDao,
) {
    /** Computes readouts for [symbol]/[interval] and persists them. CPU-bound → Default. */
    suspend operator fun invoke(symbol: String, interval: String): List<IndicatorReadout> =
        withContext(Dispatchers.Default) {
            val candles = marketRepository.getCachedCandles(symbol, interval)
                .ifEmpty { withContext(Dispatchers.IO) { marketRepository.fetchCandles(symbol, interval) } }
            if (candles.size < 60) return@withContext emptyList()
            val readouts = compute(symbol, interval, candles)
            withContext(Dispatchers.IO) {
                indicatorDao.clearFor(symbol, interval)
                indicatorDao.insertAll(readouts.map {
                    IndicatorReadoutEntity(
                        symbol = it.symbol, interval = it.interval, type = it.type.name,
                        bias = it.bias.name, strength = it.strength, value = it.value,
                        detail = it.detail, computedAt = it.computedAt,
                    )
                })
            }
            readouts
        }

    companion object {
        /** Pure, deterministic mapping from candles to readouts — backtest reuses this. */
        fun compute(symbol: String, interval: String, candles: List<Candle>): List<IndicatorReadout> {
            val closes = candles.map { it.close }
            val now = System.currentTimeMillis()
            val out = mutableListOf<IndicatorReadout>()

            val rsi = IndicatorMath.rsi(closes).last()
            if (!rsi.isNaN()) {
                val bias = if (rsi < 30) Bias.BULLISH else if (rsi > 70) Bias.BEARISH else Bias.NEUTRAL
                val strength = when {
                    rsi < 30 -> ((30 - rsi) / 30 * 100).roundToInt().coerceIn(0, 100)
                    rsi > 70 -> ((rsi - 70) / 30 * 100).roundToInt().coerceIn(0, 100)
                    else -> (100 - abs(rsi - 50) * 2).roundToInt().coerceIn(0, 40)
                }
                out += IndicatorReadout(0, symbol, interval, IndicatorType.RSI, bias, strength, rsi,
                    "RSI(14) = ${"%.1f".format(rsi)} on $interval closes", now)
            }

            val macd = IndicatorMath.macd(closes)
            val h = macd.histogram.filter { !it.isNaN() }
            if (h.size >= 2) {
                val crossedUp = h[h.size - 2] <= 0 && h.last() > 0
                val crossedDown = h[h.size - 2] >= 0 && h.last() < 0
                val bias = if (crossedUp || h.last() > 0) Bias.BULLISH else if (crossedDown || h.last() < 0) Bias.BEARISH else Bias.NEUTRAL
                val strength = if (crossedUp || crossedDown) 70 else 35
                out += IndicatorReadout(0, symbol, interval, IndicatorType.MACD, bias, strength, h.last(),
                    "MACD(12,26,9) histogram = ${"%.4f".format(h.last())}" +
                        if (crossedUp) "; bullish crossover" else if (crossedDown) "; bearish crossover" else "", now)
            }

            val ema20 = IndicatorMath.ema(closes, 20).last()
            val ema50 = IndicatorMath.ema(closes, 50).last()
            if (!ema20.isNaN() && !ema50.isNaN()) {
                val last = closes.last()
                val above = (if (last > ema20) 1 else 0) + (if (last > ema50) 1 else 0) + (if (ema20 > ema50) 1 else 0)
                val bias = if (above == 3) Bias.BULLISH else if (above == 0) Bias.BEARISH else Bias.NEUTRAL
                out += IndicatorReadout(0, symbol, interval, IndicatorType.MA_CONFLUENCE, bias,
                    if (bias == Bias.NEUTRAL) 25 else 60, ema20,
                    "price vs EMA20/EMA50 confluence: $above/3 bullish conditions", now)
            }

            val mom = IndicatorMath.momentum(closes).last()
            if (!mom.isNaN()) {
                val bias = if (mom > 1.0) Bias.BULLISH else if (mom < -1.0) Bias.BEARISH else Bias.NEUTRAL
                out += IndicatorReadout(0, symbol, interval, IndicatorType.MOMENTUM, bias,
                    (abs(mom) * 8).roundToInt().coerceIn(0, 100), mom,
                    "10-bar rate of change = ${"%.2f".format(mom)}%", now)
            }
            return out
        }
    }
}
