package com.michael.tradelab.domain.indicator

import kotlin.math.abs

/** Pure indicator math — unit-tested against fixture vectors. */
object IndicatorMath {

    /** Simple moving average; result aligned to input (first period-1 entries are NaN). */
    fun sma(values: List<Double>, period: Int): List<Double> {
        val out = MutableList(values.size) { Double.NaN }
        if (period <= 0 || values.size < period) return out
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= period) sum -= values[i - period]
            if (i >= period - 1) out[i] = sum / period
        }
        return out
    }

    /** Exponential moving average seeded with SMA of the first [period] values. */
    fun ema(values: List<Double>, period: Int): List<Double> {
        val out = MutableList(values.size) { Double.NaN }
        if (period <= 0 || values.size < period) return out
        val k = 2.0 / (period + 1)
        var prev = values.take(period).average()
        out[period - 1] = prev
        for (i in period until values.size) {
            prev = values[i] * k + prev * (1 - k)
            out[i] = prev
        }
        return out
    }

    /** Wilder-smoothed RSI. */
    fun rsi(closes: List<Double>, period: Int = 14): List<Double> {
        val out = MutableList(closes.size) { Double.NaN }
        if (closes.size <= period) return out
        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in 1..period) {
            val d = closes[i] - closes[i - 1]
            if (d > 0) avgGain += d else avgLoss += -d
        }
        avgGain /= period
        avgLoss /= period
        out[period] = rsiValue(avgGain, avgLoss)
        for (i in period + 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + maxOf(d, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + maxOf(-d, 0.0)) / period
            out[i] = rsiValue(avgGain, avgLoss)
        }
        return out
    }

    private fun rsiValue(avgGain: Double, avgLoss: Double): Double =
        if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)

    data class Macd(val macd: List<Double>, val signal: List<Double>, val histogram: List<Double>)

    fun macd(closes: List<Double>, fast: Int = 12, slow: Int = 26, signalPeriod: Int = 9): Macd {
        val emaFast = ema(closes, fast)
        val emaSlow = ema(closes, slow)
        val macdLine = closes.indices.map { i ->
            if (emaFast[i].isNaN() || emaSlow[i].isNaN()) Double.NaN else emaFast[i] - emaSlow[i]
        }
        val valid = macdLine.dropWhile { it.isNaN() }
        val signalValid = ema(valid, signalPeriod)
        val offset = macdLine.size - valid.size
        val signal = MutableList(macdLine.size) { Double.NaN }
        for (i in signalValid.indices) signal[i + offset] = signalValid[i]
        val histogram = macdLine.indices.map { i ->
            if (macdLine[i].isNaN() || signal[i].isNaN()) Double.NaN else macdLine[i] - signal[i]
        }
        return Macd(macdLine, signal, histogram)
    }

    /** Rate-of-change momentum over [period] bars, in percent. */
    fun momentum(closes: List<Double>, period: Int = 10): List<Double> =
        closes.indices.map { i ->
            if (i < period || closes[i - period] == 0.0) Double.NaN
            else (closes[i] - closes[i - period]) / abs(closes[i - period]) * 100.0
        }

    data class Stochastic(val k: List<Double>, val d: List<Double>)

    /** Stochastic oscillator: %K over [kPeriod] highs/lows, %D = SMA(%K, [dPeriod]). */
    fun stochastic(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        kPeriod: Int = 14,
        dPeriod: Int = 3,
    ): Stochastic {
        val k = MutableList(closes.size) { Double.NaN }
        for (i in closes.indices) {
            if (i < kPeriod - 1) continue
            var hh = Double.NEGATIVE_INFINITY
            var ll = Double.POSITIVE_INFINITY
            for (j in i - kPeriod + 1..i) {
                if (highs[j] > hh) hh = highs[j]
                if (lows[j] < ll) ll = lows[j]
            }
            k[i] = if (hh == ll) 50.0 else (closes[i] - ll) / (hh - ll) * 100.0
        }
        val valid = k.dropWhile { it.isNaN() }
        val dValid = sma(valid, dPeriod)
        val d = MutableList(k.size) { Double.NaN }
        val offset = k.size - valid.size
        for (i in dValid.indices) d[i + offset] = dValid[i]
        return Stochastic(k, d)
    }

    data class Bollinger(
        val upper: List<Double>,
        val middle: List<Double>,
        val lower: List<Double>,
        /** position of close within the bands: 0 = lower band, 1 = upper band */
        val percentB: List<Double>,
        /** (upper − lower) / middle, in percent — narrow bands flag a squeeze */
        val bandwidthPct: List<Double>,
    )

    fun bollinger(closes: List<Double>, period: Int = 20, mult: Double = 2.0): Bollinger {
        val mid = sma(closes, period)
        val n = closes.size
        val upper = MutableList(n) { Double.NaN }
        val lower = MutableList(n) { Double.NaN }
        val pb = MutableList(n) { Double.NaN }
        val bw = MutableList(n) { Double.NaN }
        for (i in period - 1 until n) {
            var varSum = 0.0
            for (j in i - period + 1..i) {
                val d = closes[j] - mid[i]
                varSum += d * d
            }
            val sd = kotlin.math.sqrt(varSum / period)
            upper[i] = mid[i] + mult * sd
            lower[i] = mid[i] - mult * sd
            val width = upper[i] - lower[i]
            if (width > 0) pb[i] = (closes[i] - lower[i]) / width
            if (mid[i] != 0.0) bw[i] = width / mid[i] * 100.0
        }
        return Bollinger(upper, mid, lower, pb, bw)
    }

    /** Wilder-smoothed average true range. */
    fun atr(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): List<Double> {
        val n = closes.size
        val out = MutableList(n) { Double.NaN }
        if (n <= period) return out
        val tr = DoubleArray(n)
        tr[0] = highs[0] - lows[0]
        for (i in 1 until n) {
            tr[i] = maxOf(highs[i] - lows[i], abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1]))
        }
        var prev = (1..period).sumOf { tr[it] } / period
        out[period] = prev
        for (i in period + 1 until n) {
            prev = (prev * (period - 1) + tr[i]) / period
            out[i] = prev
        }
        return out
    }

    /** Wilder ADX trend-strength (0–100); readings ≥ 25 conventionally mark a trending market. */
    fun adx(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): List<Double> {
        val n = closes.size
        val out = MutableList(n) { Double.NaN }
        if (n <= period * 2) return out
        val tr = DoubleArray(n)
        val plusDm = DoubleArray(n)
        val minusDm = DoubleArray(n)
        for (i in 1 until n) {
            tr[i] = maxOf(highs[i] - lows[i], abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1]))
            val up = highs[i] - highs[i - 1]
            val down = lows[i - 1] - lows[i]
            plusDm[i] = if (up > down && up > 0) up else 0.0
            minusDm[i] = if (down > up && down > 0) down else 0.0
        }
        var smTr = (1..period).sumOf { tr[it] }
        var smPlus = (1..period).sumOf { plusDm[it] }
        var smMinus = (1..period).sumOf { minusDm[it] }
        val dx = DoubleArray(n) { Double.NaN }
        for (i in period + 1 until n) {
            smTr = smTr - smTr / period + tr[i]
            smPlus = smPlus - smPlus / period + plusDm[i]
            smMinus = smMinus - smMinus / period + minusDm[i]
            if (smTr == 0.0) continue
            val diPlus = smPlus / smTr * 100.0
            val diMinus = smMinus / smTr * 100.0
            val diSum = diPlus + diMinus
            dx[i] = if (diSum == 0.0) 0.0 else abs(diPlus - diMinus) / diSum * 100.0
        }
        val firstDx = period + 1
        var adx = (firstDx until firstDx + period).map { dx[it] }.average()
        out[firstDx + period - 1] = adx
        for (i in firstDx + period until n) {
            adx = (adx * (period - 1) + dx[i]) / period
            out[i] = adx
        }
        return out
    }

    /** Cumulative on-balance volume. */
    fun obv(closes: List<Double>, volumes: List<Double>): List<Double> {
        val out = MutableList(closes.size) { 0.0 }
        for (i in 1 until closes.size) {
            out[i] = out[i - 1] + when {
                closes[i] > closes[i - 1] -> volumes[i]
                closes[i] < closes[i - 1] -> -volumes[i]
                else -> 0.0
            }
        }
        return out
    }

    data class Confluence(val scores: List<Double>, val adx: List<Double>)

    /**
     * Per-bar weighted blend of all component indicators into one score in [-1, 1]
     * (positive = bullish lean). ADX picks the regime: trending markets (ADX ≥ 25)
     * weight trend-followers (MACD, MA stack, momentum) up and mean-reverters
     * (RSI, stochastic, Bollinger %B) down; quiet markets (ADX < 20) do the opposite.
     */
    fun confluenceScores(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        volumes: List<Double>,
    ): Confluence {
        val rsi = rsi(closes)
        val hist = macd(closes).histogram
        val e20 = ema(closes, 20)
        val e50 = ema(closes, 50)
        val mom = momentum(closes)
        val stoch = stochastic(highs, lows, closes)
        val bb = bollinger(closes)
        val obvLine = obv(closes, volumes)
        val obvEma = ema(obvLine, 20)
        val adxLine = adx(highs, lows, closes)

        val scores = closes.indices.map { i ->
            var trendW = 1.0
            var mrW = 1.0
            val a = adxLine[i]
            if (!a.isNaN()) {
                if (a >= 25) { trendW = 1.5; mrW = 0.6 } else if (a < 20) { trendW = 0.6; mrW = 1.4 }
            }
            var sum = 0.0
            var wSum = 0.0
            fun add(score: Double, weight: Double) {
                if (!score.isNaN()) { sum += score.coerceIn(-1.0, 1.0) * weight; wSum += weight }
            }
            add((50.0 - rsi[i]) / 20.0, mrW)
            if (!hist[i].isNaN()) add(if (hist[i] > 0) 1.0 else if (hist[i] < 0) -1.0 else 0.0, trendW)
            if (!e20[i].isNaN() && !e50[i].isNaN()) {
                val c = (if (closes[i] > e20[i]) 1 else 0) + (if (closes[i] > e50[i]) 1 else 0) + (if (e20[i] > e50[i]) 1 else 0)
                add((c * 2 - 3) / 3.0, trendW)
            }
            add(mom[i] / 3.0, trendW)
            add((50.0 - stoch.k[i]) / 30.0, mrW)
            add((0.5 - bb.percentB[i]) * 2.0, mrW)
            if (!obvEma[i].isNaN()) add(if (obvLine[i] > obvEma[i]) 1.0 else -1.0, 1.0)
            if (wSum == 0.0) Double.NaN else sum / wSum
        }
        return Confluence(scores, adxLine)
    }
}
