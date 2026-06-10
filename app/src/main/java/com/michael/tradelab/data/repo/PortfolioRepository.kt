package com.michael.tradelab.data.repo

import com.michael.tradelab.data.local.OrderEntity
import com.michael.tradelab.data.local.PortfolioDao
import com.michael.tradelab.data.local.PositionEntity
import com.michael.tradelab.data.local.WalletEntity
import com.michael.tradelab.domain.model.OrderSide
import com.michael.tradelab.domain.model.OrderStatus
import com.michael.tradelab.domain.model.OrderType
import com.michael.tradelab.domain.model.Position
import com.michael.tradelab.domain.model.VirtualOrder
import com.michael.tradelab.domain.model.Wallet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Purely local virtual portfolio. No remote write path exists — order execution
 * is a pure-Kotlin function over Room state + last tick, provably incapable of
 * touching a real exchange.
 */
@Singleton
class PortfolioRepository @Inject constructor(
    private val dao: PortfolioDao,
) {
    val wallet: Flow<Wallet> = dao.observeWallet().map { Wallet(it?.cashBalance ?: STARTING_BALANCE) }

    val positions: Flow<List<Position>> = dao.observePositions()
        .map { list -> list.map { Position(it.symbol, it.qty, it.avgEntry, it.leverage, it.tpPrice, it.slPrice) } }

    val orders: Flow<List<VirtualOrder>> = dao.observeOrders().map { list -> list.map { it.toModel() } }
    val filledOrders: Flow<List<VirtualOrder>> = dao.observeFilledOrders().map { list -> list.map { it.toModel() } }

    suspend fun ensureWallet() {
        if (dao.getWallet() == null) dao.upsertWallet(WalletEntity(cashBalance = STARTING_BALANCE))
    }

    suspend fun getWalletBalance(): Double = dao.getWallet()?.cashBalance ?: STARTING_BALANCE
    suspend fun getPosition(symbol: String): Position? =
        dao.getPosition(symbol)?.let { Position(it.symbol, it.qty, it.avgEntry, it.leverage, it.tpPrice, it.slPrice) }

    /**
     * Netting fill (Binance one-way style): a BUY increases a long or reduces a
     * short; a SELL increases a short or reduces a long. Opening locks
     * notional/leverage of margin; closing releases the proportional margin plus
     * the signed (leveraged) P/L on the closed quantity. Positions hold signed
     * qty: positive = long, negative = short.
     */
    suspend fun applyFill(order: VirtualOrder, fillPrice: Double) {
        val wallet = dao.getWallet() ?: WalletEntity(cashBalance = STARTING_BALANCE)
        val position = dao.getPosition(order.symbol)
        val now = System.currentTimeMillis()
        val dir = if (order.side == OrderSide.BUY) 1.0 else -1.0
        val q = order.qty
        val posQty = position?.qty ?: 0.0
        var cash = wallet.cashBalance
        var realized = 0.0
        var reducedQty = 0.0

        val sameDirection = posQty == 0.0 || posQty.sign() == dir
        if (sameDirection) {
            // Increase (or open) in the order's direction.
            cash -= q * fillPrice / order.leverage
            val newAbs = kotlin.math.abs(posQty) + q
            val newAvg = (kotlin.math.abs(posQty) * (position?.avgEntry ?: 0.0) + q * fillPrice) / newAbs
            dao.upsertPosition(
                PositionEntity(
                    order.symbol, dir * newAbs, newAvg, order.leverage,
                    // Latest order's TP/SL takes over; keep existing if the order sets none.
                    tpPrice = order.tpPrice ?: position?.tpPrice,
                    slPrice = order.slPrice ?: position?.slPrice,
                )
            )
        } else {
            // Reduce the opposite position; any remainder flips direction.
            requireNotNull(position)
            val closeQty = minOf(q, kotlin.math.abs(posQty))
            realized = (fillPrice - position.avgEntry) * posQty.sign() * closeQty
            cash += closeQty * position.avgEntry / position.leverage + realized
            reducedQty = closeQty
            val remainderOpen = q - closeQty
            if (remainderOpen > 1e-12) {
                cash -= remainderOpen * fillPrice / order.leverage
                dao.upsertPosition(
                    PositionEntity(
                        order.symbol, dir * remainderOpen, fillPrice, order.leverage,
                        tpPrice = order.tpPrice, slPrice = order.slPrice,
                    )
                )
            } else {
                val remaining = posQty + dir * closeQty
                if (kotlin.math.abs(remaining) > 1e-12) dao.upsertPosition(position.copy(qty = remaining))
                else dao.deletePosition(order.symbol)
            }
        }

        dao.upsertWallet(wallet.copy(cashBalance = cash.coerceAtLeast(0.0)))
        dao.upsertOrder(
            order.toEntity().copy(
                status = OrderStatus.FILLED.name,
                fillPrice = fillPrice,
                filledAt = now,
                realizedPnl = realized,
                reducedQty = reducedQty,
            )
        )
    }

    suspend fun insertPendingOrder(order: VirtualOrder): Long = dao.insertOrder(order.toEntity())

    suspend fun pendingOrders(): List<VirtualOrder> = dao.pendingOrders().map { it.toModel() }

    suspend fun reset() = dao.reset(STARTING_BALANCE)

    companion object {
        const val STARTING_BALANCE = 10_000.0

        private fun Double.sign(): Double = if (this >= 0.0) 1.0 else -1.0

        fun OrderEntity.toModel() = VirtualOrder(
            id = id, symbol = symbol, side = OrderSide.valueOf(side), type = OrderType.valueOf(type),
            qty = qty, limitPrice = limitPrice, fillPrice = fillPrice,
            status = OrderStatus.valueOf(status), createdAt = createdAt, filledAt = filledAt,
            realizedPnl = realizedPnl, leverage = leverage, tpPrice = tpPrice, slPrice = slPrice,
            reducedQty = reducedQty,
        )

        fun VirtualOrder.toEntity() = OrderEntity(
            id = id, symbol = symbol, side = side.name, type = type.name, qty = qty,
            limitPrice = limitPrice, fillPrice = fillPrice, status = status.name,
            createdAt = createdAt, filledAt = filledAt, realizedPnl = realizedPnl,
            leverage = leverage, tpPrice = tpPrice, slPrice = slPrice, reducedQty = reducedQty,
        )
    }
}
