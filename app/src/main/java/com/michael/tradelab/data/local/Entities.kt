package com.michael.tradelab.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pairs")
data class PairEntity(
    @PrimaryKey val symbol: String,
    val base: String,
    val quote: String,
    val isFavorite: Boolean = false,
)

@Entity(tableName = "tickers")
data class TickerEntity(
    @PrimaryKey val symbol: String,
    val last: Double,
    val changePct: Double,
    val volume: Double,
    val updatedAt: Long,
)

@Entity(tableName = "candles", primaryKeys = ["symbol", "interval", "openTime"])
data class CandleEntity(
    val symbol: String,
    val interval: String,
    val openTime: Long,
    val o: Double,
    val h: Double,
    val l: Double,
    val c: Double,
    val v: Double,
)

@Entity(tableName = "wallet")
data class WalletEntity(
    @PrimaryKey val id: Int = 1,
    val cashBalance: Double,
)

@Entity(tableName = "positions")
data class PositionEntity(
    @PrimaryKey val symbol: String,
    /** signed: positive = long, negative = short */
    val qty: Double,
    val avgEntry: Double,
    val leverage: Int = 1,
    val tpPrice: Double? = null,
    val slPrice: Double? = null,
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val side: String,
    val type: String,
    val qty: Double,
    val limitPrice: Double?,
    val fillPrice: Double?,
    val status: String,
    val createdAt: Long,
    val filledAt: Long?,
    val realizedPnl: Double = 0.0,
    val leverage: Int = 1,
    val tpPrice: Double? = null,
    val slPrice: Double? = null,
    val reducedQty: Double = 0.0,
)

@Entity(tableName = "indicator_readouts")
data class IndicatorReadoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val interval: String,
    val type: String,
    val bias: String,
    val strength: Int,
    val value: Double,
    val detail: String,
    val computedAt: Long,
)

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val kind: String,
    val level: Double,
    val interval: String,
    val enabled: Boolean,
    val triggeredAt: Long?,
)

@Entity(tableName = "entitlements")
data class EntitlementEntity(
    @PrimaryKey val productId: String,
    val owned: Boolean,
    val updatedAt: Long,
)
