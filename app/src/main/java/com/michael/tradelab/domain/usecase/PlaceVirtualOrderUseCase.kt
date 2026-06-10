package com.michael.tradelab.domain.usecase

import com.michael.tradelab.data.repo.MarketRepository
import com.michael.tradelab.data.repo.PortfolioRepository
import com.michael.tradelab.domain.model.AppError
import com.michael.tradelab.domain.model.OrderSide
import com.michael.tradelab.domain.model.OrderStatus
import com.michael.tradelab.domain.model.OrderType
import com.michael.tradelab.domain.model.TpSlMode
import com.michael.tradelab.domain.model.TpSlSpec
import com.michael.tradelab.domain.model.VirtualOrder
import javax.inject.Inject

class PlaceVirtualOrderUseCase @Inject constructor(
    private val portfolio: PortfolioRepository,
    private val market: MarketRepository,
) {
    sealed interface Result {
        data class Filled(val order: VirtualOrder) : Result
        data class Queued(val order: VirtualOrder) : Result
        data class Rejected(val error: AppError) : Result
    }

    /**
     * The ticket takes a USDT [amountUsdt] (the margin to commit) and a [leverage]
     * multiplier; position size in base units = amount × leverage / price.
     */
    suspend operator fun invoke(
        symbol: String,
        side: OrderSide,
        type: OrderType,
        amountUsdt: Double?,
        leverage: Int,
        limitPrice: Double?,
        takeProfit: TpSlSpec? = null,
        stopLoss: TpSlSpec? = null,
    ): Result {
        if (amountUsdt == null || amountUsdt <= 0.0) return Result.Rejected(AppError.Unknown("Amount must be positive"))
        if (leverage < 1) return Result.Rejected(AppError.Unknown("Leverage must be at least 1x"))
        val lastTick = market.liveTicks.value[symbol]?.last
            ?: return Result.Rejected(AppError.StaleData)

        val refPrice = if (type == OrderType.LIMIT) {
            limitPrice ?: return Result.Rejected(AppError.Unknown("Limit price required"))
        } else lastTick
        if (refPrice <= 0.0) return Result.Rejected(AppError.Unknown("Invalid price"))

        val qty = amountUsdt * leverage / refPrice
        val isShort = side == OrderSide.SELL

        // Resolve TP/SL into trigger prices for the resulting direction
        // (BUY → long targets, SELL → short targets).
        val tpPrice = takeProfit?.let { resolveTriggerPrice(it, refPrice, qty, leverage, isTp = true, short = isShort) }
        val slPrice = stopLoss?.let { resolveTriggerPrice(it, refPrice, qty, leverage, isTp = false, short = isShort) }
        if (tpPrice != null && (if (isShort) tpPrice >= refPrice else tpPrice <= refPrice))
            return Result.Rejected(AppError.Unknown("Take profit must be on the profitable side of the entry price"))
        if (slPrice != null && (slPrice <= 0.0 || (if (isShort) slPrice <= refPrice else slPrice >= refPrice)))
            return Result.Rejected(AppError.Unknown("Stop loss must be on the losing side of the entry price"))

        val order = VirtualOrder(
            symbol = symbol, side = side, type = type, qty = qty,
            limitPrice = limitPrice, fillPrice = null, status = OrderStatus.PENDING,
            createdAt = System.currentTimeMillis(), filledAt = null, leverage = leverage,
            tpPrice = tpPrice, slPrice = slPrice,
        )

        // Margin check: only the exposure-increasing part of the order needs new margin
        // (netting — the closing part releases margin instead).
        val posQty = portfolio.getPosition(symbol)?.qty ?: 0.0
        val dir = if (side == OrderSide.BUY) 1.0 else -1.0
        val increaseQty = if (posQty == 0.0 || (posQty > 0) == (dir > 0)) qty
        else (qty - kotlin.math.abs(posQty)).coerceAtLeast(0.0)
        val requiredMargin = increaseQty * refPrice / leverage
        if (requiredMargin > portfolio.getWalletBalance() + 1e-9)
            return Result.Rejected(AppError.InsufficientVirtualBalance)

        return when (type) {
            OrderType.MARKET -> {
                portfolio.applyFill(order, lastTick)
                Result.Filled(order.copy(status = OrderStatus.FILLED, fillPrice = lastTick))
            }
            OrderType.LIMIT -> {
                val id = portfolio.insertPendingOrder(order)
                Result.Queued(order.copy(id = id))
            }
        }
    }

    companion object {
        /**
         * Converts a TP/SL spec into an absolute trigger price:
         * - PRICE: used as-is.
         * - PERCENT: ROI on margin, so the price move is value / leverage percent.
         * - PNL: USDT P/L target; price move = value / qty.
         * For shorts the profitable direction is down, so the sign flips.
         */
        fun resolveTriggerPrice(
            spec: TpSlSpec,
            entry: Double,
            qty: Double,
            leverage: Int,
            isTp: Boolean,
            short: Boolean = false,
        ): Double {
            val sign = (if (isTp) 1.0 else -1.0) * (if (short) -1.0 else 1.0)
            return when (spec.mode) {
                TpSlMode.PRICE -> spec.value
                TpSlMode.PERCENT -> entry * (1 + sign * spec.value / (100.0 * leverage))
                TpSlMode.PNL -> entry + sign * spec.value / qty
            }
        }
    }
}
