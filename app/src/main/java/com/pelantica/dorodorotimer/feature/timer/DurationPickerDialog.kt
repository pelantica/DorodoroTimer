package com.pelantica.dorodorotimer.feature.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.pelantica.dorodorotimer.R


@Composable
fun DurationPickerDialog(
    initialFocusSeconds: Int,
    initialBreakSeconds: Int,
    onConfirm: (focusSeconds: Int, breakSeconds: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var editingFocus by remember { mutableStateOf(true) }
    var focusDigits by remember { mutableStateOf(secondsToDigits(initialFocusSeconds)) }
    var breakDigits by remember { mutableStateOf(secondsToDigits(initialBreakSeconds)) }

    fun appendDigit(d: Int) {
        if (editingFocus) {
            focusDigits = appendDigit(focusDigits, d)
        } else {
            breakDigits = appendDigit(breakDigits, d)
        }
    }
    fun deleteDigit() {
        if (editingFocus) focusDigits = focusDigits.dropLast(1)
        else breakDigits = breakDigits.dropLast(1)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.duration_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = editingFocus,
                        onClick = { editingFocus = true },
                        label = { Text("${stringResource(R.string.duration_target_focus)} ${formatDigits(focusDigits)}") },
                    )
                    FilterChip(
                        selected = !editingFocus,
                        onClick = { editingFocus = false },
                        label = { Text("${stringResource(R.string.duration_target_break)} ${formatDigits(breakDigits)}") },
                    )
                }
                // 数字パッド 1-9, 下段に 削除/0/⌫相当
                val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
                rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { n ->
                            OutlinedButton(onClick = { appendDigit(n) }, modifier = Modifier.weight(1f)) {
                                Text(n.toString())
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { deleteDigit() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.duration_delete))
                    }
                    OutlinedButton(onClick = { appendDigit(0) }, modifier = Modifier.weight(1f)) {
                        Text("0")
                    }
                    OutlinedButton(onClick = { /* placeholder for alignment */ }, modifier = Modifier.weight(1f), enabled = false) {
                        Text("")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    digitsToSeconds(focusDigits).coerceAtLeast(1),
                    digitsToSeconds(breakDigits).coerceAtLeast(1),
                )
            }) { Text(stringResource(R.string.duration_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.duration_cancel)) }
        },
    )
}
