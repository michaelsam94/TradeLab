package com.michael.tradelab.domain.model

data class TradingPair(
    val symbol: String,
    val base: String,
    val quote: String,
    val isFavorite: Boolean = false,
)

data class Ticker(
    val symbol: String,
    val last: Double,
    val changePct: Double,
    val volume: Double,
    val updatedAt: Long,
)

data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

enum class OrderSide { BUY, SELL }
enum class OrderType { MARKET, LIMIT }
enum class OrderStatus { PENDING, FILLED, CANCELLED }

data class VirtualOrder(
    val id: Long = 0,
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val qty: Double,
    val limitPrice: Double?,
    val fillPrice: Double?,
    val status: OrderStatus,
    val createdAt: Long,
    val filledAt: Long?,
    /** realized P/L in quote currency for SELL fills; 0 otherwise */
    val realizedPnl: Double = 0.0,
    /** simulated leverage multiplier; margin = notional / leverage */
    val leverage: Int = 1,
    /** resolved take-profit / stop-loss trigger prices */
    val tpPrice: Double? = null,
    val slPrice: Double? = null,
    /** quantity of an existing position this fill closed (for analytics) */
    val reducedQty: Double = 0.0,
)

data class Position(
    val symbol: String,
    /** signed: positive = long, negative = short */
    val qty: Double,
    val avgEntry: Double,
    val leverage: Int = 1,
    val tpPrice: Double? = null,
    val slPrice: Double? = null,
) {
    val isShort: Boolean get() = qty < 0
    val absQty: Double get() = kotlin.math.abs(qty)
    val margin: Double get() = absQty * avgEntry / leverage

    /** Unrealized P/L at [price]; sign handles long/short automatically. */
    fun unrealizedPnl(price: Double): Double = (price - avgEntry) * qty

    /**
     * Simplified liquidation price: where unrealized loss equals locked margin
     * (no maintenance-margin buffer). 1x longs can never liquidate (price floor 0).
     */
    fun liquidationPrice(): Double? {
        if (leverage <= 1 && !isShort) return null
        return if (isShort) avgEntry * (1.0 + 1.0 / leverage)
        else avgEntry * (1.0 - 1.0 / leverage)
    }
}

/** How a take-profit / stop-loss target is expressed in the ticket. */
enum class TpSlMode { PRICE, PERCENT, PNL }

data class TpSlSpec(val mode: TpSlMode, val value: Double)

data class Wallet(val cashBalance: Double)

enum class IndicatorType { SMART_CONFLUENCE, RSI, MACD, MA_CONFLUENCE, MOMENTUM, STOCHASTIC, BOLLINGER, VOLUME_TREND }
enum class Bias { BULLISH, BEARISH, NEUTRAL }

data class IndicatorReadout(
    val id: Long = 0,
    val symbol: String,
    val interval: String,
    val type: IndicatorType,
    val bias: Bias,
    /** 0–100, labelled "historical pattern strength — not a prediction" */
    val strength: Int,
    val value: Double,
    val detail: String,
    val computedAt: Long,
)

enum class AlertKind { PRICE_ABOVE, PRICE_BELOW, RSI_BELOW, RSI_ABOVE }

data class Alert(
    val id: Long = 0,
    val symbol: String,
    val kind: AlertKind,
    val level: Double,
    val interval: String = "4h",
    val enabled: Boolean = true,
    val triggeredAt: Long? = null,
)

sealed class AppError {
    data object NetworkUnavailable : AppError()
    data object ExchangeRateLimited : AppError()
    data object StaleData : AppError()
    data object InsufficientVirtualBalance : AppError()
    data object BillingUnavailable : AppError()
    data class Unknown(val message: String? = null) : AppError()
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val error: AppError) : UiState<Nothing>
    data object Empty : UiState<Nothing>
}
