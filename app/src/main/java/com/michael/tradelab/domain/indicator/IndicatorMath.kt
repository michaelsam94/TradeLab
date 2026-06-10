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
}
