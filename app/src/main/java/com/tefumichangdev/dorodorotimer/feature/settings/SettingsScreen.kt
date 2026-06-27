package com.tefumichangdev.dorodorotimer.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tefumichangdev.dorodorotimer.R
import com.tefumichangdev.dorodorotimer.core.debug.DemoConfig

// 骨格では demoMode トグルのみ。実装時に DataStore 永続化・デバッグメニュー化する。
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var demoEnabled by remember { mutableStateOf(DemoConfig.enabled) }
    Column(modifier = modifier.padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                checked = demoEnabled,
                onCheckedChange = {
                    demoEnabled = it
                    DemoConfig.enabled = it
                },
            )
        }
    }
}
