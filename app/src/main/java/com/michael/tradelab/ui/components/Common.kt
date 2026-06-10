package com.michael.tradelab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.michael.tradelab.domain.model.Bias
import com.michael.tradelab.ui.theme.SemanticWarning
import com.michael.tradelab.ui.theme.Spacing
import com.michael.tradelab.ui.theme.gainColor
import com.michael.tradelab.ui.theme.lossColor
import java.util.Locale
import kotlin.math.abs

/** Non-dismissible compliance banner — SemanticWarning container, visually distinct from content. */
@Composable
fun DisclaimerBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = SemanticWarning.copy(alpha = 0.15f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = SemanticWarning, modifier = Modifier.size(16.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
    }
}

/** "Last updated HH:mm — reconnecting…" offline-first status chip. */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}

/** Signed, glyph-paired change text — color is never the only carrier of meaning. */
@Composable
fun ChangeText(changePct: Double, modifier: Modifier = Modifier) {
    val up = changePct >= 0
    val color = if (up) gainColor() else lossColor()
    val glyph = if (up) "▲" else "▼"
    val description = (if (up) "up " else "down ") + String.format(Locale.US, "%.2f percent", abs(changePct))
    Text(
        "$glyph ${String.format(Locale.US, "%+.2f%%", changePct)}",
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier.semantics { contentDescription = description },
    )
}

@Composable
fun BiasChip(bias: Bias, modifier: Modifier = Modifier) {
    val (label, color) = when (bias) {
        Bias.BULLISH -> "Bullish bias" to gainColor()
        Bias.BEARISH -> "Bearish bias" to lossColor()
        Bias.NEUTRAL -> "Neutral" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = color.copy(alpha = 0.14f), shape = MaterialTheme.shapes.extraSmall, modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}

/** Confidence meter labelled "historical pattern strength — not a prediction". */
@Composable
fun ConfidenceMeter(strength: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$strength/100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { strength / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs)
                .height(Spacing.xs),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/** Skeleton block matching real content shapes. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, corner: androidx.compose.ui.unit.Dp = Spacing.xs) {
    Box(
        modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                RoundedCornerShape(corner),
            )
    )
}

@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        SkeletonBlock(Modifier.size(Spacing.xxl))
        Column(Modifier.padding(start = Spacing.md).weight(1f)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(Spacing.lg))
            SkeletonBlock(Modifier.fillMaxWidth(0.25f).height(Spacing.md).padding(top = Spacing.xs))
        }
        SkeletonBlock(Modifier.width(Spacing.xxxl).height(Spacing.lg))
    }
}

@Composable
fun EmptyState(headline: String, body: String, modifier: Modifier = Modifier, cta: (@Composable () -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xxl),
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Spacing.xxxl),
        )
        Text(headline, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        cta?.invoke()
    }
}

fun formatPrice(value: Double): String = when {
    value >= 1000 -> String.format(Locale.US, "%,.2f", value)
    value >= 1 -> String.format(Locale.US, "%.2f", value)
    value >= 0.01 -> String.format(Locale.US, "%.4f", value)
    else -> String.format(Locale.US, "%.8f", value)
}

fun formatUsd(value: Double): String = String.format(Locale.US, "$%,.2f", value)
