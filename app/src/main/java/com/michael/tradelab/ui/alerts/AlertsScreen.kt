package com.michael.tradelab.ui.alerts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michael.tradelab.R
import com.michael.tradelab.data.repo.AlertRepository
import com.michael.tradelab.data.repo.SettingsRepository
import com.michael.tradelab.domain.model.Alert
import com.michael.tradelab.domain.model.AlertKind
import com.michael.tradelab.ui.components.EmptyState
import com.michael.tradelab.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val repo: AlertRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val alerts = repo.alerts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val denialCount = settings.notificationDenialCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun create(symbol: String, kind: AlertKind, level: Double) =
        viewModelScope.launch { repo.create(Alert(symbol = symbol.uppercase().trim(), kind = kind, level = level)) }

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun recordDenial() = viewModelScope.launch { settings.recordNotificationDenial() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(viewModel: AlertsViewModel = hiltViewModel()) {
    val alerts by viewModel.alerts.collectAsState()
    val denials by viewModel.denialCount.collectAsState()
    val context = LocalContext.current
    var showCreate by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }

    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) viewModel.recordDenial()
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showCreate = true }) { Text("New alert") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (needsPermission && denials >= 2) {
                // Permanently denied path: persistent non-blocking banner, never re-prompt.
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    ) {
                        Text(
                            stringResource(R.string.alert_denied_fallback),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            )
                        }) { Text(stringResource(R.string.open_settings)) }
                    }
                }
            }
            if (alerts.isEmpty()) {
                EmptyState(
                    headline = "No alerts yet",
                    body = "Create a price or RSI alert — alerts are informational only and never trade for you.",
                )
            } else {
                LazyColumn {
                    items(items = alerts, key = { it.id }) { alert ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        ) {
                            Icon(Icons.Outlined.NotificationsActive, contentDescription = null,
                                tint = if (alert.triggeredAt != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(start = Spacing.md)) {
                                Text(describe(alert), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (alert.triggeredAt != null) "Triggered" else "Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.delete(alert.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete alert for ${alert.symbol}")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateAlertSheet(
            onDismiss = { showCreate = false },
            onCreate = { symbol, kind, level ->
                viewModel.create(symbol, kind, level)
                showCreate = false
                // Rationale before system dialog, triggered after the first alert is saved.
                if (needsPermission && denials < 2) showRationale = true
            },
        )
    }

    if (showRationale) {
        ModalBottomSheet(onDismissRequest = { showRationale = false }) {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.alert_rationale_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.alert_rationale_body), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(onClick = {
                        showRationale = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }) { Text("Grant") }
                    TextButton(onClick = { showRationale = false }) { Text("Not now") }
                }
            }
        }
    }
}

private fun describe(alert: Alert): String = when (alert.kind) {
    AlertKind.PRICE_ABOVE -> "${alert.symbol} crosses above ${alert.level}"
    AlertKind.PRICE_BELOW -> "${alert.symbol} falls below ${alert.level}"
    AlertKind.RSI_ABOVE -> "${alert.symbol} RSI(14) ${alert.interval} above ${alert.level}"
    AlertKind.RSI_BELOW -> "${alert.symbol} RSI(14) ${alert.interval} below ${alert.level}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAlertSheet(onDismiss: () -> Unit, onCreate: (String, AlertKind, Double) -> Unit) {
    var symbol by remember { mutableStateOf("BTCUSDT") }
    var kindIndex by remember { mutableStateOf(0) }
    var levelText by remember { mutableStateOf("") }
    val kinds = AlertKind.entries
    val labels = listOf("Price ↑", "Price ↓", "RSI <", "RSI >")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text("New alert", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = symbol, onValueChange = { symbol = it },
                label = { Text("Pair symbol") }, singleLine = true,
                shape = MaterialTheme.shapes.small,
                supportingText = { Text("e.g. BTCUSDT") },
                modifier = Modifier.fillMaxWidth(),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                // Order matches AlertKind.entries: PRICE_ABOVE, PRICE_BELOW, RSI_BELOW, RSI_ABOVE
                val ordered = listOf(0, 1, 2, 3)
                ordered.forEach { i ->
                    SegmentedButton(
                        selected = kindIndex == i,
                        onClick = { kindIndex = i },
                        shape = SegmentedButtonDefaults.itemShape(i, ordered.size),
                    ) { Text(labels[i]) }
                }
            }
            OutlinedTextField(
                value = levelText, onValueChange = { levelText = it },
                label = { Text("Level") }, singleLine = true,
                shape = MaterialTheme.shapes.small,
                supportingText = { Text("Price level, or RSI value 0–100") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { levelText.toDoubleOrNull()?.let { onCreate(symbol, kinds[kindIndex], it) } },
                enabled = levelText.toDoubleOrNull() != null && symbol.isNotBlank(),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save alert") }
        }
    }
}
