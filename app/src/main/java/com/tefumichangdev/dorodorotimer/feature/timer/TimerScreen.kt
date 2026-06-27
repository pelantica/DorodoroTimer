package com.tefumichangdev.dorodorotimer.feature.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tefumichangdev.dorodorotimer.R
import com.tefumichangdev.dorodorotimer.domain.model.TimerPhase
import org.koin.androidx.compose.koinViewModel

@Composable
fun TimerScreen(
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    TimerContent(
        state = state,
        modifier = modifier,
        onToggle = viewModel::toggleRunning,
        onReset = viewModel::reset,
    )
}

@Composable
private fun TimerContent(
    state: TimerUiState,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val phaseLabel = when (state.phase) {
            TimerPhase.FOCUS -> stringResource(R.string.timer_phase_focus)
            TimerPhase.BREAK -> stringResource(R.string.timer_phase_break)
        }
        Text(text = phaseLabel, style = MaterialTheme.typography.titleMedium)
        Text(text = formatTime(state.remainingSeconds), style = MaterialTheme.typography.displayLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onToggle) {
                Text(
                    stringResource(
                        if (state.isRunning) R.string.timer_pause else R.string.timer_start
                    )
                )
            }
            OutlinedButton(onClick = onReset) {
                Text(stringResource(R.string.timer_reset))
            }
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
