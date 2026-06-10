package com.michael.tradelab.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michael.tradelab.R
import com.michael.tradelab.data.local.TradeLabDatabase
import com.michael.tradelab.data.repo.SettingsRepository
import com.michael.tradelab.ui.theme.Spacing
import com.michael.tradelab.ui.theme.lossColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val db: TradeLabDatabase,
) : ViewModel() {
    /** Local "Reset & erase all data" — no accounts exist, all data is on-device. */
    fun eraseAll(onDone: () -> Unit) = viewModelScope.launch {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        settings.eraseAll()
        onDone()
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    var showDisclaimer by remember { mutableStateOf(false) }
    var showErase by remember { mutableStateOf(false) }
    var erased by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Text("Legal", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = { showDisclaimer = true }) { Text("View simulator disclaimer") }
        Text(
            "Privacy: all watchlists, virtual trades, and alerts are stored only on this device. No account exists and no personal data is uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()

        Text("Data", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(
            onClick = { showErase = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = lossColor()),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Reset & erase all data") }
        if (erased) {
            Text("All local data erased. Restart the app to re-run onboarding.", style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider()

        Text("About", style = MaterialTheme.typography.titleLarge)
        Text(
            "TradeLab is a trading simulator for education and entertainment. Market data comes from Binance public endpoints; no orders ever leave this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            title = { Text("Simulator disclaimer") },
            text = { Text(stringResource(R.string.disclaimer_full)) },
            confirmButton = { TextButton(onClick = { showDisclaimer = false }) { Text("Close") } },
        )
    }
    if (showErase) {
        AlertDialog(
            onDismissRequest = { showErase = false },
            title = { Text("Erase all data?") },
            text = { Text("This permanently deletes your watchlist, virtual portfolio, alerts, and settings from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    showErase = false
                    viewModel.eraseAll { erased = true }
                }) { Text("Erase") }
            },
            dismissButton = { TextButton(onClick = { showErase = false }) { Text("Cancel") } },
        )
    }
}
