package com.michael.tradelab

import com.michael.tradelab.domain.model.Candle
import com.michael.tradelab.domain.model.IndicatorType
import com.michael.tradelab.domain.usecase.RunBacktestUseCase
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

class BacktestDeterminismTest {

    private fun seededCandles(seed: Int, n: Int = 300): List<Candle> {
        val rnd = Random(seed)
        var price = 100.0
        return (0 until n).map { i ->
            val drift = sin(i / 15.0) * 2 + (rnd.nextDouble() - 0.5)
            val open = price
            price += drift
            Candle(i * 3_600_000L, open, maxOf(open, price) + 0.5, minOf(open, price) - 0.5, price, 1000.0)
        }
    }

    @Test
    fun `seeded candles produce identical backtest results`() {
        val candles = seededCandles(42)
        for (type in IndicatorType.entries) {
            val a = RunBacktestUseCase.backtest(candles, type)
            val b = RunBacktestUseCase.backtest(seededCandles(42), type)
            assertEquals(a, b)
        }
    }

    @Test
    fun `win rate is between zero and one`() {
        val result = RunBacktestUseCase.backtest(seededCandles(7), IndicatorType.RSI)
        assertEquals(true, result.winRate in 0.0..1.0)
    }
}
