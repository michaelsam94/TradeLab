package com.michael.tradelab

import com.michael.tradelab.domain.model.Position
import com.michael.tradelab.domain.model.TpSlMode
import com.michael.tradelab.domain.model.TpSlSpec
import com.michael.tradelab.domain.usecase.CloseTpSlPositionsUseCase.Companion.liquidationTrigger
import com.michael.tradelab.domain.usecase.CloseTpSlPositionsUseCase.Companion.tpSlTrigger
import com.michael.tradelab.domain.usecase.PlaceVirtualOrderUseCase.Companion.resolveTriggerPrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortAndLiquidationTest {

    @Test
    fun `short unrealized pnl gains when price falls`() {
        val short = Position("BTCUSDT", -1.0, 50_000.0, 10)
        assertEquals(2_000.0, short.unrealizedPnl(48_000.0), 1e-9)
        assertEquals(-2_000.0, short.unrealizedPnl(52_000.0), 1e-9)
    }

    @Test
    fun `liquidation price is entry shifted by one over leverage`() {
        // Long 10x at 50,000 → liquidates at 45,000 (loss = margin)
        assertEquals(45_000.0, Position("BTCUSDT", 1.0, 50_000.0, 10).liquidationPrice()!!, 1e-9)
        // Short 10x at 50,000 → liquidates at 55,000
        assertEquals(55_000.0, Position("BTCUSDT", -1.0, 50_000.0, 10).liquidationPrice()!!, 1e-9)
        // 1x long can never liquidate
        assertNull(Position("BTCUSDT", 1.0, 50_000.0, 1).liquidationPrice())
    }

    @Test
    fun `liquidation triggers on the losing side only`() {
        val long = Position("BTCUSDT", 1.0, 50_000.0, 10)
        assertEquals(45_000.0, liquidationTrigger(long, 44_900.0)!!, 1e-9)
        assertNull(liquidationTrigger(long, 45_100.0))

        val short = Position("BTCUSDT", -1.0, 50_000.0, 10)
        assertEquals(55_000.0, liquidationTrigger(short, 55_100.0)!!, 1e-9)
        assertNull(liquidationTrigger(short, 54_900.0))
    }

    @Test
    fun `short tp triggers below entry and sl above`() {
        val short = Position("BTCUSDT", -1.0, 50_000.0, 10, tpPrice = 48_000.0, slPrice = 51_000.0)
        assertEquals(48_000.0, tpSlTrigger(short, 47_900.0)!!, 1e-9)
        assertEquals(51_000.0, tpSlTrigger(short, 51_100.0)!!, 1e-9)
        assertNull(tpSlTrigger(short, 50_000.0))
    }

    @Test
    fun `tp sl resolution flips direction for shorts`() {
        // Short entry 50,000, 10x, qty 1: +50% ROI TP → price falls 5% → 47,500
        assertEquals(
            47_500.0,
            resolveTriggerPrice(TpSlSpec(TpSlMode.PERCENT, 50.0), 50_000.0, 1.0, 10, isTp = true, short = true),
            1e-9,
        )
        // Short SL of 1,000 USDT loss → price rises 1,000 → 51,000
        assertEquals(
            51_000.0,
            resolveTriggerPrice(TpSlSpec(TpSlMode.PNL, 1_000.0), 50_000.0, 1.0, 10, isTp = false, short = true),
            1e-9,
        )
    }
}
