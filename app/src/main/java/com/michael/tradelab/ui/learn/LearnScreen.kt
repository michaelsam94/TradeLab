package com.michael.tradelab.ui.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.michael.tradelab.ui.theme.Spacing

private data class Lesson(val id: Int, val title: String, val body: String)

private val LESSONS = listOf(
    Lesson(1, "What is paper trading?", "Paper trading means practising with simulated money against real market prices. Every balance in TradeLab is virtual: nothing can be deposited, withdrawn, or converted."),
    Lesson(2, "Reading candlesticks", "Each candle shows open, high, low, and close for one time interval. A filled body below the open marks a down candle; above, an up candle. Wicks show the traded extremes."),
    Lesson(3, "RSI basics", "The Relative Strength Index compares average gains to average losses over 14 periods. Values under 30 are conventionally called oversold and over 70 overbought — descriptions of past behaviour, not forecasts."),
    Lesson(4, "MACD explained", "MACD subtracts a slow EMA from a fast EMA and tracks that difference against its own average. Crossovers describe shifts in historical momentum."),
    Lesson(5, "Order types", "A market order fills immediately at the latest price. A limit order waits until the market crosses your chosen level. TradeLab simulates both against live data."),
    Lesson(6, "Risk and drawdown", "Max drawdown measures the largest peak-to-trough drop of an equity curve. Professional risk management focuses on limiting drawdown, not maximising single trades."),
)

@Composable
fun LearnScreen(onOpenSettings: () -> Unit, onOpenAlerts: () -> Unit) {
    var selected by remember { mutableStateOf<Lesson?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "Learn",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(Spacing.lg),
        )
        // Mandatory LazyRow: fixed-width lesson cards.
        val lessonState = rememberLazyListState()
        LazyRow(
            state = lessonState,
            contentPadding = PaddingValues(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items = LESSONS, key = { it.id }) { lesson ->
                Card(
                    onClick = { selected = lesson },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected?.id == lesson.id)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.width(200.dp),
                ) {
                    Column(Modifier.padding(Spacing.lg)) {
                        Text("Lesson ${lesson.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        (selected ?: LESSONS.first()).let { lesson ->
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(lesson.title, style = MaterialTheme.typography.titleLarge)
                Text(lesson.body, style = MaterialTheme.typography.bodyLarge)
            }
        }
        TextButton(onClick = onOpenAlerts, modifier = Modifier.padding(horizontal = Spacing.lg)) { Text("Manage alerts") }
        TextButton(onClick = onOpenSettings, modifier = Modifier.padding(horizontal = Spacing.lg)) { Text("Settings & legal") }
    }
}
