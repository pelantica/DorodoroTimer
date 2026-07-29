package com.pelantica.dorodorotimer.feature.timer

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pelantica.dorodorotimer.R
import com.pelantica.dorodorotimer.domain.model.TimerPhase
import com.pelantica.dorodorotimer.domain.model.TimerUiState
import org.koin.androidx.compose.koinViewModel

@Composable
fun TimerScreen(
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = koinViewModel(),
) {
    // POST_NOTIFICATIONS（Android 13+）を初回に要求（終了通知のため）
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 未許可でもタイマーは動く。通知が出ないだけ。 */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val preset by viewModel.preset.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val isSoundPlaying by viewModel.isSoundPlaying.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    TimerContent(
        state = state,
        isSoundPlaying = isSoundPlaying,
        modifier = modifier,
        onToggle = viewModel::toggleRunning,
        onReset = viewModel::reset,
        onEditTime = { showPicker = true },
        onToggleSound = viewModel::toggleSound,
    )

    if (showPicker) {
        DurationPickerDialog(
            initialFocusSeconds = preset.focusSeconds,
            initialBreakSeconds = preset.breakSeconds,
            onConfirm = { focus, brk ->
                viewModel.updateDurations(focus, brk)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun TimerContent(
    state: TimerUiState,
    isSoundPlaying: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onEditTime: () -> Unit,
    onToggleSound: () -> Unit,
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
        Text(
            text = formatTime(state.remainingSeconds),
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.clickable(enabled = !state.isRunning) { onEditTime() },
        )
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
        OutlinedButton(onClick = onToggleSound) {
            Text(
                stringResource(
                    if (isSoundPlaying) R.string.timer_sound_stop else R.string.timer_sound_start
                )
            )
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
