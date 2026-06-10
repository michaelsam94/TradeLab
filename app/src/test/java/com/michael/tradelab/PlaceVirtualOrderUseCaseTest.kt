package com.michael.tradelab

import com.michael.tradelab.data.repo.MarketRepository
import com.michael.tradelab.data.repo.PortfolioRepository
import com.michael.tradelab.domain.model.AppError
import com.michael.tradelab.domain.model.OrderSide
import com.michael.tradelab.domain.model.OrderType
import com.michael.tradelab.domain.model.Position
import com.michael.tradelab.domain.model.Ticker
import com.michael.tradelab.domain.usecase.PlaceVirtualOrderUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceVirtualOrderUseCaseTest {

    private val portfolio = mockk<PortfolioRepository>(relaxed = true)
    private val market = mockk<MarketRepository>(relaxed = true)
    private val useCase = PlaceVirtualOrderUseCase(portfolio, market)

    private fun tick(price: Double) {
        every { market.liveTicks } returns MutableStateFlow(
            mapOf("BTCUSDT" to Ticker("BTCUSDT", price, 0.0, 0.0, 0L))
        )
    }

    @Test
    fun `market buy fills at last tick with derived quantity`() = runTest {
        tick(50_000.0)
        coEvery { portfolio.getWalletBalance() } returns 10_000.0
        val result = useCase("BTCUSDT", OrderSide.BUY, OrderType.MARKET, amountUsdt = 5_000.0, leverage = 1, limitPrice = null)
        assertTrue(result is PlaceVirtualOrderUseCase.Result.Filled)
        // 5,000 USDT at 50,000 → 0.1 BTC
        assertEquals(0.1, (result as PlaceVirtualOrderUseCase.Result.Filled).order.qty, 1e-9)
        coVerify { portfolio.applyFill(any(), 50_000.0) }
    }

    @Test
    fun `leverage multiplies position size not margin`() = runTest {
        tick(50_000.0)
        coEvery { portfolio.getWalletBalance() } returns 10_000.0
        val result = useCase("BTCUSDT", OrderSide.BUY, OrderType.MARKET, amountUsdt = 5_000.0, leverage = 10, limitPrice = null)
        assertTrue(result is PlaceVirtualOrderUseCase.Result.Filled)
        // 5,000 USDT margin × 10x at 50,000 → 1.0 BTC
        assertEquals(1.0, (result as PlaceVirtualOrderUseCase.Result.Filled).order.qty, 1e-9)
        assertEquals(10, result.order.leverage)
    }

    @Test
    fun `buy margin exceeding balance is rejected regardless of leverage`() = runTest {
        tick(50_000.0)
        coEvery { portfolio.getWalletBalance() } returns 10_000.0
        val result = useCase("BTCUSDT", OrderSide.BUY, OrderType.MARKET, amountUsdt = 10_001.0, leverage = 50, limitPrice = null)
        assertTrue(result is PlaceVirtualOrderUseCase.Result.Rejected)
        assertTrue((result as PlaceVirtualOrderUseCase.Result.Rejected).error is AppError.InsufficientVirtualBalance)
    }

    @Test
    fun `sell without holdings opens a short when margin suffices`() = runTest {
        tick(50_000.0)
        coEvery { portfolio.getPosition("BTCUSDT") } returns null
        coEvery { portfolio.getWalletBalance() } returns 10_000.0
        val result = useCase("BTCUSDT", OrderSide.SELL, OrderType.MARKET, amountUsdt = 100.0, leverage = 1, limitPrice = null)
        assertTrue(result is PlaceVirtualOrderUseCase.Result.Filled)
    }

    @Test
    fun `short open without margin is rejected`() = runTest {
        tick(50_000.0)
        coEvery { portfolio.getPosition("BTCUSDT") } returns null
        coEvery { portfolio.getWalletBalance() } returns 50.0
        val result = useCase("BTCUSDT", OrderSide.SELL, OrderType.MARKET, amountUsdt = 100.0, leverage = 1, limitPrice = null)
        assertTrue(result is PlaceVirtualOrderUseCase.Result.Rejected)
    }

    @Test
    fun `closing an existing long needs no new margin`() = runTest {
        tick(50_000.0)
        coEvery { portfolio.getPosition("BTCUSDT") } returns Position("BTCUSDT", 0.5, 40_000.0, 10)
        coEvery { portfolio.getWalletBalance() } returns 0.0
        // 10,000 USDT × 1x / 50,000 = 0.2 BTC — purely reduces the 0.5 long.
        val result = useCase("BTCUSDT", OrderSide.SELL, OrderType.MARKET, amountUsdt = 10_000.0, leverage = 1, limitPrice = null)
        assertTrue(result is PlaceVirtualOrderUseCase.Result.Filled)
    }

    @Test
    fun `sell within holdings fills`() = runTest {
        tick(50_000.0)
        coEvery { portfolio.getPosition("BTCUSDT") } returns Position("BTCUSDT", 0.5, 40_000.0)
        val result = useCase("BTCUSDT", OrderSide.SELL, OrderType.MARKET, amountUsdt = 5_000.0, leverage = 1, limitPrice = null)
        assertTrue(result is PlaceVirtualOrderUseCase.Result.Filled)
    }

    @Test
    fun `limit order is queued not filled`() = runTest {
        tick(50_000.0)
        coEvery { portfolio.getWalletBalance() } returns 10_000.0
        coEvery { portfolio.insertPendingOrder(any()) } returns 7L
        val result = useCase("BTCUSDT", OrderSide.BUY, OrderType.LIMIT, amountUsdt = 4_500.0, leverage = 2, limitPrice = 45_000.0)
        assertTrue(result is PlaceVirtualOrderUseCase.Result.Queued)
        // qty derived from limit price: 4,500 × 2 / 45,000 = 0.2
        assertEquals(0.2, (result as PlaceVirtualOrderUseCase.Result.Queued).order.qty, 1e-9)
    }

    @Test
    fun `zero amount is rejected`() = runTest {
        tick(50_000.0)
        val result = useCase("BTCUSDT", OrderSide.BUY, OrderType.MARKET, amountUsdt = 0.0, leverage = 1, limitPrice = null)
        assertTrue(result is PlaceVirtualOrderUseCase.Result.Rejected)
    }

    @Test
    fun `invalid leverage is rejected`() = runTest {
        tick(50_000.0)
        val result = useCase("BTCUSDT", OrderSide.BUY, OrderType.MARKET, amountUsdt = 100.0, leverage = 0, limitPrice = null)
        assertTrue(result is PlaceVirtualOrderUseCase.Result.Rejected)
    }

    @Test
    fun `no live tick rejects with stale data`() = runTest {
        every { market.liveTicks } returns MutableStateFlow(emptyMap())
        val result = useCase("BTCUSDT", OrderSide.BUY, OrderType.MARKET, amountUsdt = 100.0, leverage = 1, limitPrice = null)
        assertTrue((result as PlaceVirtualOrderUseCase.Result.Rejected).error is AppError.StaleData)
    }
}
