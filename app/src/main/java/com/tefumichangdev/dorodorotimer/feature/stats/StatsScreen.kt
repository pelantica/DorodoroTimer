package com.tefumichangdev.dorodorotimer.feature.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tefumichangdev.dorodorotimer.R
import com.tefumichangdev.dorodorotimer.core.debug.Anr
import com.tefumichangdev.dorodorotimer.core.debug.DemoConfig
import com.tefumichangdev.dorodorotimer.domain.model.DailyStat
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate

@Composable
fun StatsScreen(modifier: Modifier = Modifier, viewModel: StatsViewModel = koinViewModel()) {
    LaunchedEffect(Unit) {
        // [ANR-03] 通知ディープリンク流入（冷えた起動）でこの画面が前面化する。
        //  demoMode ON のとき、ここで重い同期集計やロック競合を走らせると起動ANRを再現できる。
        //  今回は土台のみ＝フックだけ。本体は未実装。
        if (DemoConfig.isOn(Anr.ANR_03)) {
            // TODO(ANR-03): 重い同期処理（DB全件集計 / ロック競合 / Thread.sleep 等）をここで。
        }
    }
    val uiState by viewModel.uiState.collectAsState()
    StatsContent(modifier = modifier, uiState = uiState)
}

@Composable
fun StatsContent(modifier: Modifier = Modifier, uiState: StatsUiState) {
    when {
        uiState.isLoading -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.stats_placeholder))
        }
        uiState.stats.isEmpty() -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.stats_empty))
        }
        else -> LazyColumn(modifier = modifier) {
            items(uiState.stats) { stat ->
                StatsDailyRow(stat = stat)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StatsDailyRow(stat: DailyStat, modifier: Modifier = Modifier) {
    val date = LocalDate.ofEpochDay(stat.dateEpochDay)
    val totalMinutes = stat.totalFocusSeconds / 60
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_row_date, date.year, date.monthValue, date.dayOfMonth),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.stats_row_focus_count, stat.focusCount),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.stats_row_total_minutes, totalMinutes),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
