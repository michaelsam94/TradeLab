package com.michael.tradelab.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PairEntity::class, TickerEntity::class, CandleEntity::class,
        WalletEntity::class, PositionEntity::class, OrderEntity::class,
        IndicatorReadoutEntity::class, AlertEntity::class, EntitlementEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class TradeLabDatabase : RoomDatabase() {
    abstract fun pairDao(): PairDao
    abstract fun tickerDao(): TickerDao
    abstract fun candleDao(): CandleDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun indicatorDao(): IndicatorDao
    abstract fun alertDao(): AlertDao
    abstract fun entitlementDao(): EntitlementDao
}
