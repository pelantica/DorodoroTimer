package com.pelantica.dorodorotimer.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pelantica.dorodorotimer.R
import com.pelantica.dorodorotimer.core.debug.Anr
import com.pelantica.dorodorotimer.core.debug.AppRestarter
import com.pelantica.dorodorotimer.core.debug.DemoFlagsState
import com.pelantica.dorodorotimer.core.ui.SectionCard
import org.koin.androidx.compose.koinViewModel

@StringRes
private fun Anr.labelRes(): Int = when (this) {
    Anr.ANR_01 -> R.string.settings_anr_01_label
    Anr.ANR_02 -> R.string.settings_anr_02_label
    Anr.ANR_03 -> R.string.settings_anr_03_label
    Anr.ANR_05 -> R.string.settings_anr_05_label
    Anr.ANR_06 -> R.string.settings_anr_06_label
    Anr.ANR_07 -> R.string.settings_anr_07_label
    Anr.ANR_FGS -> R.string.settings_anr_fgs_label
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    SettingsContent(
        modifier = modifier,
        state = state,
        onMaster = viewModel::setMaster,
        onAnr = viewModel::setAnr,
        onDismissRestartPrompt = viewModel::dismissRestartPrompt,
        onConfirmRestart = {
            viewModel.dismissRestartPrompt()
            AppRestarter.restart(context)
        },
    )
}

@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    state: DemoFlagsState,
    onMaster: (Boolean) -> Unit,
    onAnr: (Anr, Boolean) -> Unit,
    onDismissRestartPrompt: () -> Unit = {},
    onConfirmRestart: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // マスタースイッチ
        SectionCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_demo_mode_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_demo_mode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = state.master,
                    onCheckedChange = onMaster,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ANR個別トグルセクション
        Text(
            text = stringResource(R.string.settings_anr_section_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))

        SectionCard {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Anr.entries.forEachIndexed { index, anr ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(anr.labelRes()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = state.perAnr[anr] ?: false,
                            onCheckedChange = { on -> onAnr(anr, on) },
                            enabled = state.master,
                        )
                    }
                }
            }
        }
    }

    val restartPromptFor = state.restartPromptFor
    if (restartPromptFor != null) {
        AlertDialog(
            onDismissRequest = onDismissRestartPrompt,
            title = { Text(text = stringResource(R.string.settings_restart_dialog_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.settings_restart_dialog_message,
                        stringResource(restartPromptFor.labelRes()),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmRestart) {
                    Text(text = stringResource(R.string.settings_restart_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRestartPrompt) {
                    Text(text = stringResource(R.string.settings_restart_dialog_dismiss))
                }
            },
        )
    }
}
