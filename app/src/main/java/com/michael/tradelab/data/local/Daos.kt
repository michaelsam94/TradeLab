package com.michael.tradelab.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PairDao {
    @Upsert suspend fun upsertAll(pairs: List<PairEntity>)
    @Query("SELECT * FROM pairs ORDER BY symbol") fun observeAll(): Flow<List<PairEntity>>
    @Query("SELECT * FROM pairs WHERE symbol = :symbol") suspend fun get(symbol: String): PairEntity?
    @Query("UPDATE pairs SET isFavorite = :fav WHERE symbol = :symbol") suspend fun setFavorite(symbol: String, fav: Boolean)
    @Query("SELECT COUNT(*) FROM pairs") suspend fun count(): Int
}

@Dao
interface TickerDao {
    @Upsert suspend fun upsertAll(tickers: List<TickerEntity>)
    @Query("SELECT * FROM tickers") fun observeAll(): Flow<List<TickerEntity>>
    @Query("SELECT * FROM tickers WHERE symbol = :symbol") fun observe(symbol: String): Flow<TickerEntity?>
    @Query("SELECT MAX(updatedAt) FROM tickers") fun observeLastUpdated(): Flow<Long?>
}

@Dao
interface CandleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(candles: List<CandleEntity>)
    @Query("SELECT * FROM candles WHERE symbol = :symbol AND interval = :interval ORDER BY openTime")
    fun observe(symbol: String, interval: String): Flow<List<CandleEntity>>
    @Query("SELECT * FROM candles WHERE symbol = :symbol AND interval = :interval ORDER BY openTime")
    suspend fun get(symbol: String, interval: String): List<CandleEntity>
}

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM wallet WHERE id = 1") fun observeWallet(): Flow<WalletEntity?>
    @Query("SELECT * FROM wallet WHERE id = 1") suspend fun getWallet(): WalletEntity?
    @Upsert suspend fun upsertWallet(wallet: WalletEntity)

    @Query("SELECT * FROM positions WHERE qty != 0") fun observePositions(): Flow<List<PositionEntity>>
    @Query("SELECT * FROM positions WHERE symbol = :symbol") suspend fun getPosition(symbol: String): PositionEntity?
    @Upsert suspend fun upsertPosition(position: PositionEntity)
    @Query("DELETE FROM positions WHERE symbol = :symbol") suspend fun deletePosition(symbol: String)

    @Insert suspend fun insertOrder(order: OrderEntity): Long
    @Upsert suspend fun upsertOrder(order: OrderEntity)
    @Query("SELECT * FROM orders ORDER BY createdAt DESC") fun observeOrders(): Flow<List<OrderEntity>>
    @Query("SELECT * FROM orders WHERE status = 'PENDING'") suspend fun pendingOrders(): List<OrderEntity>
    @Query("SELECT * FROM orders WHERE status = 'FILLED' ORDER BY filledAt") suspend fun filledOrders(): List<OrderEntity>
    @Query("SELECT * FROM orders WHERE status = 'FILLED' ORDER BY filledAt DESC") fun observeFilledOrders(): Flow<List<OrderEntity>>

    @Query("DELETE FROM positions") suspend fun clearPositions()
    @Query("DELETE FROM orders") suspend fun clearOrders()

    @Transaction
    suspend fun reset(startingBalance: Double) {
        clearPositions()
        clearOrders()
        upsertWallet(WalletEntity(cashBalance = startingBalance))
    }
}

@Dao
interface IndicatorDao {
    @Query("DELETE FROM indicator_readouts WHERE symbol = :symbol AND interval = :interval") suspend fun clearFor(symbol: String, interval: String)
    @Insert suspend fun insertAll(readouts: List<IndicatorReadoutEntity>)
    @Query("SELECT * FROM indicator_readouts ORDER BY strength DESC") fun observeAll(): Flow<List<IndicatorReadoutEntity>>
    @Query("SELECT * FROM indicator_readouts WHERE symbol = :symbol ORDER BY computedAt DESC") fun observeFor(symbol: String): Flow<List<IndicatorReadoutEntity>>
}

@Dao
interface AlertDao {
    @Insert suspend fun insert(alert: AlertEntity): Long
    @Upsert suspend fun upsert(alert: AlertEntity)
    @Query("DELETE FROM alerts WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM alerts ORDER BY id DESC") fun observeAll(): Flow<List<AlertEntity>>
    @Query("SELECT * FROM alerts WHERE enabled = 1 AND triggeredAt IS NULL") suspend fun active(): List<AlertEntity>
    @Query("UPDATE alerts SET triggeredAt = :at, enabled = 0 WHERE id = :id") suspend fun markTriggered(id: Long, at: Long)
}

@Dao
interface EntitlementDao {
    @Upsert suspend fun upsertAll(items: List<EntitlementEntity>)
    @Query("SELECT * FROM entitlements") fun observeAll(): Flow<List<EntitlementEntity>>
}
