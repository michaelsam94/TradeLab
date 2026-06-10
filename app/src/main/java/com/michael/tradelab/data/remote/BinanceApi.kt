package com.michael.tradelab.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class ExchangeInfoDto(val symbols: List<SymbolDto>)

@Serializable
data class SymbolDto(
    val symbol: String,
    val status: String,
    val baseAsset: String,
    val quoteAsset: String,
)

interface BinanceApi {
    @GET("api/v3/exchangeInfo")
    suspend fun exchangeInfo(): ExchangeInfoDto

    /** Each kline is a heterogeneous JSON array: [openTime, o, h, l, c, v, closeTime, ...] */
    @GET("api/v3/klines")
    suspend fun klines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 500,
    ): List<JsonArray>

    @Serializable
    data class MiniTicker24hDto(
        val symbol: String,
        @SerialName("lastPrice") val lastPrice: String,
        @SerialName("priceChangePercent") val priceChangePercent: String,
        @SerialName("quoteVolume") val quoteVolume: String,
    )

    @GET("api/v3/ticker/24hr")
    suspend fun ticker24h(): List<MiniTicker24hDto>

    companion object {
        const val BASE_URL = "https://api.binance.com/"
        const val WS_URL = "wss://stream.binance.com:9443/ws/!miniTicker@arr"
    }
}
