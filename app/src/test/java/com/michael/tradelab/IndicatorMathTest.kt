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
}
