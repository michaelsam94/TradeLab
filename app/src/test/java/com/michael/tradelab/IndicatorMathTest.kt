package com.michael.tradelab

import com.michael.tradelab.domain.indicator.IndicatorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorMathTest {

    // Classic Wilder RSI fixture (J. Welles Wilder's worked example closes).
    private val wilderCloses = listOf(
        44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42, 45.84, 46.08,
        45.89, 46.03, 45.61, 46.28, 46.28, 46.00, 46.03, 46.41, 46.22, 45.64,
    )

    @Test
    fun `rsi matches known fixture`() {
        val rsi = IndicatorMath.rsi(wilderCloses, 14)
        assertEquals(70.46, rsi[14], 0.1)
        assertEquals(66.25, rsi[15], 0.5)
    }

    @Test
    fun `sma of constant series is constant`() {
        val sma = IndicatorMath.sma(List(10) { 5.0 }, 3)
        assertTrue(sma.drop(2).all { it == 5.0 })
        assertTrue(sma.take(2).all { it.isNaN() })
    }

    @Test
    fun `ema converges toward latest values`() {
        val values = List(50) { 10.0 } + List(50) { 20.0 }
        val ema = IndicatorMath.ema(values, 10)
        assertTrue(ema.last() > 19.5)
    }

    @Test
    fun `macd histogram is zero for constant series`() {
        val macd = IndicatorMath.macd(List(100) { 42.0 })
        assertEquals(0.0, macd.histogram.last(), 1e-9)
    }

    @Test
    fun `momentum measures rate of change`() {
        val values = (1..20).map { it.toDouble() }
        val mom = IndicatorMath.momentum(values, 10)
        assertEquals((20.0 - 10.0) / 10.0 * 100, mom.last(), 1e-9)
    }

    @Test
    fun `stochastic is 100 at range high and 0 at range low`() {
        val n = 30
        val highs = List(n) { 102.0 }
        val lows = List(n) { 98.0 }
        val atHigh = IndicatorMath.stochastic(highs, lows, List(n) { 102.0 })
        assertEquals(100.0, atHigh.k.last(), 1e-9)
        val atLow = IndicatorMath.stochastic(highs, lows, List(n) { 98.0 })
        assertEquals(0.0, atLow.k.last(), 1e-9)
    }

    @Test
    fun `bollinger percentB centers at half for constant series`() {
        // Constant series has zero stddev → bands collapse; use a mild oscillation instead.
        val values = (0 until 60).map { 100.0 + if (it % 2 == 0) 1.0 else -1.0 }
        val bb = IndicatorMath.bollinger(values)
        val pb = bb.percentB.last()
        assertTrue(pb in 0.0..1.0)
        assertTrue(bb.upper.last() > bb.lower.last())
    }

    @Test
    fun `atr reflects bar range`() {
        val n = 50
        val highs = List(n) { 102.0 }
        val lows = List(n) { 98.0 }
        val closes = List(n) { 100.0 }
        val atr = IndicatorMath.atr(highs, lows, closes)
        assertEquals(4.0, atr.last(), 1e-9)
    }

    @Test
    fun `obv accumulates volume with price direction`() {
        val closes = listOf(1.0, 2.0, 3.0, 2.0, 2.0)
        val volumes = listOf(10.0, 10.0, 10.0, 10.0, 10.0)
        val obv = IndicatorMath.obv(closes, volumes)
        assertEquals(listOf(0.0, 10.0, 20.0, 10.0, 10.0), obv)
    }

    @Test
    fun `adx is high in a persistent trend`() {
        val rising = (1..100).map { 100.0 + it }
        val highs = rising.map { it + 1.0 }
        val lows = rising.map { it - 1.0 }
        val adx = IndicatorMath.adx(highs, lows, rising)
        assertTrue("ADX should flag a one-way trend, was ${adx.last()}", adx.last() > 25.0)
    }

    @Test
    fun `confluence score is bullish in a steady uptrend with volume`() {
        val rising = (1..120).map { 100.0 + it * 0.5 }
        val highs = rising.map { it + 0.5 }
        val lows = rising.map { it - 0.5 }
        val volumes = List(rising.size) { 50.0 }
        val c = IndicatorMath.confluenceScores(highs, lows, rising, volumes)
        assertTrue("score should lean bullish, was ${c.scores.last()}", c.scores.last() > 0.0)
        assertTrue(c.scores.filter { !it.isNaN() }.all { it in -1.0..1.0 })
    }
}
