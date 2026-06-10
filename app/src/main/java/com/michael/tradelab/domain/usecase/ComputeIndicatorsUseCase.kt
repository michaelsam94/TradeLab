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
    /**
     * Computes readouts for [symbol]/[interval] and persists them. CPU-bound → Default.
     *
     * [livePrice] overlays the latest streamed price onto the open candle so readouts
     * track the live tape between candle closes. [refreshCandles] forces a fresh kline
     * fetch (falling back to cache on network failure).
     */
    suspend operator fun invoke(
        symbol: String,
        interval: String,
        livePrice: Double? = null,
        refreshCandles: Boolean = false,
    ): List<IndicatorReadout> =
        withContext(Dispatchers.Default) {
            var candles = marketRepository.getCachedCandles(symbol, interval)
            if (candles.isEmpty() || refreshCandles) {
                candles = withContext(Dispatchers.IO) {
                    runCatching { marketRepository.fetchCandles(symbol, interval) }.getOrDefault(candles)
                }
            }
            if (candles.size < 60) return@withContext emptyList()
            if (livePrice != null && livePrice > 0) {
                val last = candles.last()
                candles = candles.dropLast(1) + last.copy(
                    close = livePrice,
                    high = maxOf(last.high, livePrice),
                    low = minOf(last.low, livePrice),
                )
            }
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

            val highs = candles.map { it.high }
            val lows = candles.map { it.low }
            val volumes = candles.map { it.volume }

            val stoch = IndicatorMath.stochastic(highs, lows, closes)
            val k = stoch.k.last()
            val d = stoch.d.last()
            if (!k.isNaN() && !d.isNaN()) {
                val bias = if (k < 20) Bias.BULLISH else if (k > 80) Bias.BEARISH else Bias.NEUTRAL
                val strength = when {
                    k < 20 -> ((20 - k) / 20 * 100).roundToInt().coerceIn(0, 100)
                    k > 80 -> ((k - 80) / 20 * 100).roundToInt().coerceIn(0, 100)
                    else -> 20
                }
                val cross = if (k > d) "%K above %D" else "%K below %D"
                out += IndicatorReadout(0, symbol, interval, IndicatorType.STOCHASTIC, bias, strength, k,
                    "Stoch(14,3) %K = ${"%.1f".format(k)}, %D = ${"%.1f".format(d)}; $cross", now)
            }

            val bb = IndicatorMath.bollinger(closes)
            val pb = bb.percentB.last()
            val bw = bb.bandwidthPct.last()
            if (!pb.isNaN()) {
                val bias = if (pb <= 0.05) Bias.BULLISH else if (pb >= 0.95) Bias.BEARISH else Bias.NEUTRAL
                val strength = when {
                    pb <= 0.05 -> ((0.05 - pb) * 400 + 50).roundToInt().coerceIn(50, 100)
                    pb >= 0.95 -> ((pb - 0.95) * 400 + 50).roundToInt().coerceIn(50, 100)
                    else -> 20
                }
                val squeeze = if (!bw.isNaN() && bw < 4.0) "; band squeeze (volatility compressed)" else ""
                out += IndicatorReadout(0, symbol, interval, IndicatorType.BOLLINGER, bias, strength, pb,
                    "Bollinger(20,2) %B = ${"%.2f".format(pb)}, bandwidth = ${"%.1f".format(bw)}%$squeeze", now)
            }

            val obv = IndicatorMath.obv(closes, volumes)
            val obvEma = IndicatorMath.ema(obv, 20).last()
            if (!obvEma.isNaN() && !ema20.isNaN()) {
                val volumeUp = obv.last() > obvEma
                val priceUp = closes.last() > ema20
                val (bias, strength, note) = when {
                    volumeUp && priceUp -> Triple(Bias.BULLISH, 55, "volume flow confirms the up-move")
                    !volumeUp && !priceUp -> Triple(Bias.BEARISH, 55, "volume flow confirms the down-move")
                    else -> Triple(Bias.NEUTRAL, 30, "volume flow diverges from price (caution)")
                }
                out += IndicatorReadout(0, symbol, interval, IndicatorType.VOLUME_TREND, bias, strength, obv.last(),
                    "OBV vs its EMA20: $note", now)
            }

            // Composite: regime-weighted blend of every component above (see IndicatorMath.confluenceScores).
            val confluence = IndicatorMath.confluenceScores(highs, lows, closes, volumes)
            val score = confluence.scores.last()
            if (!score.isNaN()) {
                val adx = confluence.adx.last()
                val bias = if (score > 0.15) Bias.BULLISH else if (score < -0.15) Bias.BEARISH else Bias.NEUTRAL
                val bulls = out.count { it.bias == Bias.BULLISH }
                val bears = out.count { it.bias == Bias.BEARISH }
                val regime = when {
                    adx.isNaN() -> "regime unknown"
                    adx >= 25 -> "trending market (ADX ${"%.0f".format(adx)})"
                    adx < 20 -> "ranging market (ADX ${"%.0f".format(adx)})"
                    else -> "transitional market (ADX ${"%.0f".format(adx)})"
                }
                out += IndicatorReadout(0, symbol, interval, IndicatorType.SMART_CONFLUENCE, bias,
                    (abs(score) * 100).roundToInt().coerceIn(0, 100), score,
                    "weighted blend of ${out.size} indicators = ${"%+.2f".format(score)}; " +
                        "$bulls bullish / $bears bearish components; $regime", now)
            }
            return out
        }
    }
}
