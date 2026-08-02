package com.pelantica.dorodorotimer.feature.timer

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pelantica.dorodorotimer.R
import com.pelantica.dorodorotimer.domain.model.TimerPhase
import com.pelantica.dorodorotimer.domain.model.TimerUiState
import org.koin.androidx.compose.koinViewModel

/** 残り時間リングの直径と太さ。 */
private val RingSize = 240.dp
private val RingStroke = 12.dp

/** ピル型ボタンの内側余白。M3 既定より横に広げて、モックの形に寄せる。 */
private val PillPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)

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

    // リングの満量は「今のフェーズの設定時間」。休憩中は breakSeconds が基準になる。
    val totalSeconds = when (state.phase) {
        TimerPhase.FOCUS -> preset.focusSeconds
        TimerPhase.BREAK -> preset.breakSeconds
    }

    TimerContent(
        state = state,
        totalSeconds = totalSeconds,
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
    totalSeconds: Int,
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
        PhaseChip(phase = state.phase)

        Spacer(modifier = Modifier.height(32.dp))

        TimerRing(
            remainingSeconds = state.remainingSeconds,
            totalSeconds = totalSeconds,
            // 実行中に設定時間を変えるとリングの基準がずれるので、停止中だけ編集に入れる。
            onEditTime = if (state.isRunning) null else onEditTime,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onToggle, shape = CircleShape, contentPadding = PillPadding) {
                Text(
                    stringResource(
                        if (state.isRunning) R.string.timer_pause else R.string.timer_start
                    )
                )
            }
            OutlinedButton(onClick = onReset, shape = CircleShape, contentPadding = PillPadding) {
                Text(stringResource(R.string.timer_reset))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 雨音は主導線ではないので、枠線も文字も一段退かせる。
        OutlinedButton(
            onClick = onToggleSound,
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text(
                stringResource(
                    if (isSoundPlaying) R.string.timer_sound_stop else R.string.timer_sound_start
                )
            )
        }
    }
}

/** 「🍅 集中」／「☕ 休憩」のピル。いまどちらのフェーズかを一目で分かるようにする。 */
@Composable
private fun PhaseChip(phase: TimerPhase, modifier: Modifier = Modifier) {
    // 絵文字は文字列側（strings.xml）に持たせている。Composable では地と文字色だけ決める。
    val label = when (phase) {
        TimerPhase.FOCUS -> stringResource(R.string.timer_phase_focus)
        TimerPhase.BREAK -> stringResource(R.string.timer_phase_break)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * 残り時間のリングと、中央の時刻表示。
 * [onEditTime] が null のときは時刻をタップしても編集に入らない（＝実行中）。
 */
@Composable
private fun TimerRing(
    remainingSeconds: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier,
    onEditTime: (() -> Unit)?,
) {
    // 設定が 0 秒でも 0 除算しないようにする。残量なので時間が経つと減る向き。
    val progress = if (totalSeconds > 0) {
        (remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
    } else {
        0f
    }
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val progressColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier.size(RingSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = RingStroke.toPx()
            // 線は中心線を基準に太るので、半分だけ内側に寄せないと上下左右が切れる。
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (progress > 0f) {
                drawArc(
                    color = progressColor,
                    // 12時から時計回り。Compose の 0 度は3時方向なので -90 から始める。
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = formatTime(remainingSeconds),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = if (onEditTime == null) Modifier else Modifier.clickable(onClick = onEditTime),
        )
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
