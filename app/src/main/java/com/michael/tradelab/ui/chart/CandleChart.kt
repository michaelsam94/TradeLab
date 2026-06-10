package com.michael.tradelab.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.michael.tradelab.domain.indicator.IndicatorMath
import com.michael.tradelab.domain.model.Candle
import com.michael.tradelab.ui.components.formatPrice
import com.michael.tradelab.ui.theme.Brand400
import com.michael.tradelab.ui.theme.gainColor
import com.michael.tradelab.ui.theme.lossColor

/**
 * Single-Canvas OHLC chart with pinch-zoom/pan, optional EMA20 overlay and
 * long-press crosshair. Exposes a textual OHLC summary for accessibility.
 */
@Composable
fun CandleChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
    showEma: Boolean = true,
    alertLevel: Double? = null,
) {
    if (candles.isEmpty()) return
    val up = gainColor()
    val down = lossColor()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val crosshairColor = MaterialTheme.colorScheme.onSurfaceVariant

    var visibleCount by remember { mutableFloatStateOf(90f) }
    var endIndex by remember(candles.size) { mutableFloatStateOf(candles.size.toFloat()) }
    var crosshair by remember { mutableStateOf<Offset?>(null) }

    val emaSeries = remember(candles, showEma) {
        if (showEma) IndicatorMath.ema(candles.map { it.close }, 20) else emptyList()
    }

    val count = visibleCount.toInt().coerceIn(20, candles.size)
    val end = endIndex.toInt().coerceIn(count, candles.size)
    val visible = candles.subList(end - count, end)
    val min = visible.minOf { it.low }
    val max = visible.maxOf { it.high }
    val summary = "Candlestick chart, ${visible.size} candles. " +
        "Open ${formatPrice(visible.first().open)}, high ${formatPrice(max)}, " +
        "low ${formatPrice(min)}, close ${formatPrice(visible.last().close)}."

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .semantics { contentDescription = summary }
            .pointerInput(candles.size) {
                detectTransformGestures { _, pan, zoom, _ ->
                    visibleCount = (visibleCount / zoom).coerceIn(20f, candles.size.toFloat())
                    val perCandle = size.width / visibleCount
                    endIndex = (endIndex - pan.x / perCandle)
                        .coerceIn(visibleCount, candles.size.toFloat())
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { crosshair = it },
                    onTap = { crosshair = null },
                )
            },
    ) {
        val w = size.width
        val h = size.height
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        fun y(price: Double) = (h * (1 - (price - min) / range)).toFloat()
        val candleW = w / visible.size

        // Grid lines
        for (i in 1..3) {
            val gy = h * i / 4f
            drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
        }

        visible.forEachIndexed { i, c ->
            val x = candleW * i + candleW / 2
            val color = if (c.close >= c.open) up else down
            drawLine(color, Offset(x, y(c.high)), Offset(x, y(c.low)), strokeWidth = 1.5f)
            val top = y(maxOf(c.open, c.close))
            val bottom = y(minOf(c.open, c.close))
            drawRect(
                color,
                topLeft = Offset(x - candleW * 0.35f, top),
                size = androidx.compose.ui.geometry.Size(candleW * 0.7f, (bottom - top).coerceAtLeast(1f)),
            )
        }

        if (emaSeries.isNotEmpty()) {
            val path = Path()
            var started = false
            visible.forEachIndexed { i, _ ->
                val v = emaSeries[end - count + i]
                if (!v.isNaN()) {
                    val x = candleW * i + candleW / 2
                    if (!started) { path.moveTo(x, y(v)); started = true } else path.lineTo(x, y(v))
                }
            }
            drawPath(path, Brand400, style = Stroke(width = 2f))
        }

        alertLevel?.let { lvl ->
            if (lvl in min..max) {
                drawLine(
                    Color(0xFFF57C00),
                    Offset(0f, y(lvl)), Offset(w, y(lvl)),
                    strokeWidth = 2f,
                )
            }
        }

        crosshair?.let { pos ->
            drawLine(crosshairColor, Offset(pos.x, 0f), Offset(pos.x, h), strokeWidth = 1f)
            drawLine(crosshairColor, Offset(0f, pos.y), Offset(w, pos.y), strokeWidth = 1f)
        }
    }
}
