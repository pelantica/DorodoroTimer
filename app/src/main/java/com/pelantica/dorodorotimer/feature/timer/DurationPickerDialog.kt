package com.pelantica.dorodorotimer.feature.timer

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pelantica.dorodorotimer.R
import com.pelantica.dorodorotimer.core.ui.DorodoroTimerTheme
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged

private val WheelItemHeight = 40.dp
private const val WHEEL_VISIBLE_ITEM_COUNT = 5

@Composable
fun DurationPickerDialog(
    initialFocusSeconds: Int,
    initialBreakSeconds: Int,
    onConfirm: (focusSeconds: Int, breakSeconds: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val (initialFocusMinutes, initialFocusSecondsValue) = remember(initialFocusSeconds) {
        secondsToWheelValues(initialFocusSeconds, FOCUS_MIN_MINUTES, FOCUS_MAX_MINUTES)
    }
    val (initialBreakMinutes, initialBreakSecondsValue) = remember(initialBreakSeconds) {
        secondsToWheelValues(initialBreakSeconds, BREAK_MIN_MINUTES, BREAK_MAX_MINUTES)
    }

    var editingFocus by remember { mutableStateOf(true) }
    var focusMinutes by remember { mutableStateOf(initialFocusMinutes) }
    var focusSeconds by remember { mutableStateOf(initialFocusSecondsValue) }
    var breakMinutes by remember { mutableStateOf(initialBreakMinutes) }
    var breakSeconds by remember { mutableStateOf(initialBreakSecondsValue) }

    DurationPickerDialogContent(
        editingFocus = editingFocus,
        onEditingTargetChange = { editingFocus = it },
        focusMinutes = focusMinutes,
        focusSeconds = focusSeconds,
        breakMinutes = breakMinutes,
        breakSeconds = breakSeconds,
        onFocusMinutesChange = { focusMinutes = it },
        onFocusSecondsChange = { focusSeconds = it },
        onBreakMinutesChange = { breakMinutes = it },
        onBreakSecondsChange = { breakSeconds = it },
        onConfirm = {
            onConfirm(
                wheelValuesToSeconds(focusMinutes, focusSeconds),
                wheelValuesToSeconds(breakMinutes, breakSeconds),
            )
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun DurationPickerDialogContent(
    editingFocus: Boolean,
    onEditingTargetChange: (Boolean) -> Unit,
    focusMinutes: Int,
    focusSeconds: Int,
    breakMinutes: Int,
    breakSeconds: Int,
    onFocusMinutesChange: (Int) -> Unit,
    onFocusSecondsChange: (Int) -> Unit,
    onBreakMinutesChange: (Int) -> Unit,
    onBreakSecondsChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.duration_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = editingFocus,
                        onClick = { onEditingTargetChange(true) },
                        label = {
                            Text(
                                "${stringResource(R.string.duration_target_focus)} " +
                                    formatMinutesSeconds(focusMinutes, focusSeconds)
                            )
                        },
                    )
                    FilterChip(
                        selected = !editingFocus,
                        onClick = { onEditingTargetChange(false) },
                        label = {
                            Text(
                                "${stringResource(R.string.duration_target_break)} " +
                                    formatMinutesSeconds(breakMinutes, breakSeconds)
                            )
                        },
                    )
                }
                if (editingFocus) {
                    DurationWheelRow(
                        minutesValues = minutesWheelValues(FOCUS_MIN_MINUTES, FOCUS_MAX_MINUTES),
                        selectedMinutes = focusMinutes,
                        onMinutesChange = onFocusMinutesChange,
                        selectedSeconds = focusSeconds,
                        onSecondsChange = onFocusSecondsChange,
                    )
                } else {
                    DurationWheelRow(
                        minutesValues = minutesWheelValues(BREAK_MIN_MINUTES, BREAK_MAX_MINUTES),
                        selectedMinutes = breakMinutes,
                        onMinutesChange = onBreakMinutesChange,
                        selectedSeconds = breakSeconds,
                        onSecondsChange = onBreakSecondsChange,
                    )
                }
            }
        },
        confirmButton = {
            // 集中・休憩を同時に確定するので、どちらかが0分0秒なら押させない。
            Button(
                onClick = onConfirm,
                enabled = isValidDuration(focusMinutes, focusSeconds) &&
                    isValidDuration(breakMinutes, breakSeconds),
            ) { Text(stringResource(R.string.duration_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.duration_cancel)) }
        },
    )
}

/** 分・秒2本のドラムロールを横並びで表示する行。中央に選択枠を重ねる。 */
@Composable
private fun DurationWheelRow(
    minutesValues: List<Int>,
    selectedMinutes: Int,
    onMinutesChange: (Int) -> Unit,
    selectedSeconds: Int,
    onSecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WheelNumberPicker(
                values = minutesValues,
                selectedValue = selectedMinutes,
                onValueChange = onMinutesChange,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.duration_unit_minutes),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            WheelNumberPicker(
                values = SECONDS_WHEEL_VALUES,
                selectedValue = selectedSeconds,
                onValueChange = onSecondsChange,
                valueLabel = { "%02d".format(it) },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.duration_unit_seconds),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Column(modifier = Modifier.align(Alignment.Center).fillMaxWidth()) {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(WheelItemHeight))
            HorizontalDivider()
        }
    }
}

/**
 * LazyColumn + snapFlingBehavior によるドラムロール（ホイール）ピッカー。
 * 中央に来た項目がスクロール確定値になる。[values] の並び順がそのままホイールの並び。
 */
@Composable
private fun WheelNumberPicker(
    values: List<Int>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueLabel: (Int) -> String = { it.toString() },
) {
    val initialIndex = remember(values, selectedValue) {
        values.indexOf(selectedValue).coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(listState)
    val sidePadding: Dp = WheelItemHeight * (WHEEL_VISIBLE_ITEM_COUNT / 2)

    // 下の LaunchedEffect は selectedValue が変わっても再起動しないため、
    // 素で参照すると古い値を掴んだままになる。rememberUpdatedState で常に最新を見る。
    val currentSelectedValue by rememberUpdatedState(selectedValue)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    // スクロールが止まったタイミングでのみ確定値を通知する（composition中のState書き換えを避ける）。
    LaunchedEffect(listState, values) {
        snapshotFlow { listState.isScrollInProgress to listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { (isScrolling, index) ->
                if (!isScrolling) {
                    val value = values.getOrNull(index) ?: return@collect
                    if (value != currentSelectedValue) currentOnValueChange(value)
                }
            }
    }

    val centerIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = modifier.height(WheelItemHeight * WHEEL_VISIBLE_ITEM_COUNT),
        contentPadding = PaddingValues(vertical = sidePadding),
    ) {
        itemsIndexed(values) { index, value ->
            val distance = abs(index - centerIndex)
            val alpha = when (distance) {
                0 -> 1f
                1 -> 0.6f
                else -> 0.3f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelItemHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = valueLabel(value),
                    style = if (distance == 0) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                )
            }
        }
    }
}

private fun formatMinutesSeconds(minutes: Int, seconds: Int): String = "%d:%02d".format(minutes, seconds)

@Preview(showBackground = true)
@Composable
private fun DurationPickerDialogContentPreview() {
    DorodoroTimerTheme {
        DurationPickerDialogContent(
            editingFocus = true,
            onEditingTargetChange = {},
            focusMinutes = 25,
            focusSeconds = 0,
            breakMinutes = 5,
            breakSeconds = 0,
            onFocusMinutesChange = {},
            onFocusSecondsChange = {},
            onBreakMinutesChange = {},
            onBreakSecondsChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
