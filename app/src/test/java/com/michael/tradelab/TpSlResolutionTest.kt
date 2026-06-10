package com.michael.tradelab

import com.michael.tradelab.domain.model.TpSlMode
import com.michael.tradelab.domain.model.TpSlSpec
import com.michael.tradelab.domain.usecase.PlaceVirtualOrderUseCase.Companion.resolveTriggerPrice
import org.junit.Assert.assertEquals
import org.junit.Test

class TpSlResolutionTest {

    // Entry 50,000; margin 5,000 at 10x → qty = 1.0 BTC.
    private val entry = 50_000.0
    private val qty = 1.0
    private val leverage = 10

    @Test
    fun `price mode passes through`() {
        assertEquals(55_000.0, resolveTriggerPrice(TpSlSpec(TpSlMode.PRICE, 55_000.0), entry, qty, leverage, isTp = true), 1e-9)
        assertEquals(48_000.0, resolveTriggerPrice(TpSlSpec(TpSlMode.PRICE, 48_000.0), entry, qty, leverage, isTp = false), 1e-9)
    }

    @Test
    fun `percent mode is ROI on margin so price move is divided by leverage`() {
        // +50% ROI at 10x = +5% price move → 52,500
        assertEquals(52_500.0, resolveTriggerPrice(TpSlSpec(TpSlMode.PERCENT, 50.0), entry, qty, leverage, isTp = true), 1e-9)
        // -20% ROI at 10x = -2% price move → 49,000
        assertEquals(49_000.0, resolveTriggerPrice(TpSlSpec(TpSlMode.PERCENT, 20.0), entry, qty, leverage, isTp = false), 1e-9)
    }

    @Test
    fun `pnl mode converts USDT target to price move via quantity`() {
        // +2,000 USDT on 1.0 BTC → entry + 2,000
        assertEquals(52_000.0, resolveTriggerPrice(TpSlSpec(TpSlMode.PNL, 2_000.0), entry, qty, leverage, isTp = true), 1e-9)
        // -1,000 USDT on 1.0 BTC → entry - 1,000
        assertEquals(49_000.0, resolveTriggerPrice(TpSlSpec(TpSlMode.PNL, 1_000.0), entry, qty, leverage, isTp = false), 1e-9)
    }
}
