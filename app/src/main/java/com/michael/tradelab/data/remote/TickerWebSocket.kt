package com.michael.tradelab.data.remote

import com.michael.tradelab.domain.model.Ticker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifecycle-bound Binance miniTicker stream. Collected only while the app is
 * foregrounded (repository scopes the collection); no foreground service exists.
 */
@Singleton
class TickerWebSocket @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {
    fun stream(): Flow<List<Ticker>> = callbackFlow {
        val request = Request.Builder().url(BinanceApi.WS_URL).build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val now = System.currentTimeMillis()
                    val tickers = json.parseToJsonElement(text).jsonArray.map { el ->
                        val o = el.jsonObject
                        val close = o["c"]!!.jsonPrimitive.content.toDouble()
                        val open = o["o"]!!.jsonPrimitive.content.toDouble()
                        Ticker(
                            symbol = o["s"]!!.jsonPrimitive.content,
                            last = close,
                            changePct = if (open != 0.0) (close - open) / open * 100 else 0.0,
                            volume = o["q"]!!.jsonPrimitive.content.toDouble(),
                            updatedAt = now,
                        )
                    }
                    trySendBlocking(tickers)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }
        })
        awaitClose { ws.cancel() }
    }.retryWhen { _, attempt ->
        delay(minOf(30_000L, 1_000L shl attempt.toInt().coerceAtMost(5)))
        true
    }
}
