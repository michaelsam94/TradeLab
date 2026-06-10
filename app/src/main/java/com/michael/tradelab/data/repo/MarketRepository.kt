package com.michael.tradelab.data.repo

import com.michael.tradelab.data.local.CandleDao
import com.michael.tradelab.data.local.CandleEntity
import com.michael.tradelab.data.local.PairDao
import com.michael.tradelab.data.local.PairEntity
import com.michael.tradelab.data.local.TickerDao
import com.michael.tradelab.data.local.TickerEntity
import com.michael.tradelab.data.remote.BinanceApi
import com.michael.tradelab.data.remote.TickerWebSocket
import com.michael.tradelab.domain.model.Candle
import com.michael.tradelab.domain.model.Ticker
import com.michael.tradelab.domain.model.TradingPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketRepository @Inject constructor(
    private val api: BinanceApi,
    private val webSocket: TickerWebSocket,
    private val pairDao: PairDao,
    private val tickerDao: TickerDao,
    private val candleDao: CandleDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var streamJob: Job? = null

    /** High-frequency tick state, sampled before UI emission. */
    val liveTicks = MutableStateFlow<Map<String, Ticker>>(emptyMap())

    val pairs: Flow<List<TradingPair>> = pairDao.observeAll()
        .map { list -> list.map { TradingPair(it.symbol, it.base, it.quote, it.isFavorite) } }

    val tickers: Flow<List<Ticker>> = tickerDao.observeAll()
        .map { list -> list.map { Ticker(it.symbol, it.last, it.changePct, it.volume, it.updatedAt) } }

    val lastUpdated: Flow<Long?> = tickerDao.observeLastUpdated()

    suspend fun refreshPairs() {
        if (pairDao.count() > 0) return
        val info = api.exchangeInfo()
        pairDao.upsertAll(
            info.symbols
                .filter { it.status == "TRADING" && it.quoteAsset in SUPPORTED_QUOTES }
                .map { PairEntity(it.symbol, it.baseAsset, it.quoteAsset) }
        )
    }

    suspend fun refreshTickersSnapshot() {
        val now = System.currentTimeMillis()
        val snapshot = api.ticker24h().map {
            TickerEntity(it.symbol, it.lastPrice.toDouble(), it.priceChangePercent.toDouble(), it.quoteVolume.toDouble(), now)
        }
        tickerDao.upsertAll(snapshot)
    }

    /** Start the foreground-only stream; idempotent. Stopped from onStop via [stopStream]. */
    fun startStream() {
        if (streamJob?.isActive == true) return
        streamJob = scope.launch {
            launch {
                webSocket.stream().collect { batch ->
                    liveTicks.value = liveTicks.value + batch.associateBy { it.symbol }
                }
            }
            // Persist sampled ticks so offline-first cache stays warm without flooding Room.
            liveTicks.sample(1_000).collect { map ->
                if (map.isNotEmpty()) {
                    tickerDao.upsertAll(map.values.map {
                        TickerEntity(it.symbol, it.last, it.changePct, it.volume, it.updatedAt)
                    })
                }
            }
        }
    }

    fun stopStream() {
        streamJob?.cancel()
        streamJob = null
    }

    suspend fun toggleFavorite(symbol: String) {
        pairDao.get(symbol)?.let { pairDao.setFavorite(symbol, !it.isFavorite) }
    }

    fun observeCandles(symbol: String, interval: String): Flow<List<Candle>> =
        candleDao.observe(symbol, interval).map { list ->
            list.map { Candle(it.openTime, it.o, it.h, it.l, it.c, it.v) }
        }

    suspend fun fetchCandles(symbol: String, interval: String, limit: Int = 500): List<Candle> {
        val rows = api.klines(symbol, interval, limit)
        val entities = rows.map { k ->
            CandleEntity(
                symbol = symbol,
                interval = interval,
                openTime = k[0].jsonPrimitive.long,
                o = k[1].jsonPrimitive.content.toDouble(),
                h = k[2].jsonPrimitive.content.toDouble(),
                l = k[3].jsonPrimitive.content.toDouble(),
                c = k[4].jsonPrimitive.content.toDouble(),
                v = k[5].jsonPrimitive.content.toDouble(),
            )
        }
        candleDao.upsertAll(entities)
        return entities.map { Candle(it.openTime, it.o, it.h, it.l, it.c, it.v) }
    }

    suspend fun getCachedCandles(symbol: String, interval: String): List<Candle> =
        candleDao.get(symbol, interval).map { Candle(it.openTime, it.o, it.h, it.l, it.c, it.v) }

    companion object {
        val SUPPORTED_QUOTES = setOf("USDT", "BTC", "ETH", "EUR", "TRY")
    }
}
