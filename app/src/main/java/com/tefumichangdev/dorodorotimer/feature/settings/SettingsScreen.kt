package com.tefumichangdev.dorodorotimer.feature.settings

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tefumichangdev.dorodorotimer.R
import com.tefumichangdev.dorodorotimer.core.debug.Anr
import com.tefumichangdev.dorodorotimer.core.debug.DemoFlagsState
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
    SettingsContent(
        modifier = modifier,
        state = state,
        onMaster = viewModel::setMaster,
        onAnr = viewModel::setAnr,
    )
}

@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    state: DemoFlagsState,
    onMaster: (Boolean) -> Unit,
    onAnr: (Anr, Boolean) -> Unit,
) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // マスタースイッチ
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_demo_mode_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_demo_mode_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = state.master,
                onCheckedChange = onMaster,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // ANR個別トグルセクション
        Text(
            text = stringResource(R.string.settings_anr_section_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Anr.entries.forEach { anr ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
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
