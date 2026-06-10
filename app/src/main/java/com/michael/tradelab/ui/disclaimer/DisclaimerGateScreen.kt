package com.michael.tradelab.ui.disclaimer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michael.tradelab.R
import com.michael.tradelab.data.repo.SettingsRepository
import com.michael.tradelab.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DisclaimerViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {
    fun accept() = viewModelScope.launch { settings.acceptDisclaimer() }
}

/**
 * F7 compliance gate: un-dismissible, no skip; "I understand" enables only
 * after the full disclaimer has been scrolled to the end.
 */
@Composable
fun DisclaimerGateScreen(viewModel: DisclaimerViewModel = hiltViewModel()) {
    val scrollState = rememberScrollState()
    val scrolledToEnd by remember {
        derivedStateOfScrolledToEnd(scrollState)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = Spacing.lg),
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.lg),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .testTag("disclaimer_scroll"),
        ) {
            Text("Before you start", style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.disclaimer_full),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            // Spacer guarantees genuine scrolling on tall screens so acceptance is deliberate.
            androidx.compose.foundation.layout.Spacer(Modifier.height(600.dp))
            Text(
                "End of disclaimer.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!scrolledToEnd) {
            Text(
                stringResource(R.string.scroll_to_continue),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )
        }
        Button(
            onClick = viewModel::accept,
            enabled = scrolledToEnd,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.lg)
                .height(52.dp)
                .testTag("disclaimer_accept"),
        ) {
            Text(stringResource(R.string.i_understand))
        }
    }
}

private fun derivedStateOfScrolledToEnd(scrollState: androidx.compose.foundation.ScrollState) =
    androidx.compose.runtime.derivedStateOf {
        scrollState.maxValue == 0 || scrollState.value >= scrollState.maxValue - 4
    }
