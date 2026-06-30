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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.tefumichangdev.dorodorotimer.R
import com.tefumichangdev.dorodorotimer.core.debug.Anr
import com.tefumichangdev.dorodorotimer.core.debug.DemoConfig
import com.tefumichangdev.dorodorotimer.domain.model.DailyStat
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate

@Composable
fun StatsScreen(modifier: Modifier = Modifier, viewModel: StatsViewModel = koinViewModel()) {
    LaunchedEffect(Unit) {
        if (DemoConfig.isOn(Anr.ANR_03)) {
            // [ANR-03] 通知ディープリンク(dorodoro://stats)で冷えた起動→この画面が前面化。
            //  バックグラウンドがロックを保持して重い処理中だと、
            //  メインがロックを取りにいくと待たされる(held by = 待たされ系ANR)。
            //  処方: 遅延評価／重処理をメイン外へ（withContext(IO) や StateFlow で非同期化）。

            // BG でロック保持 + 重処理を開始（Dispatchers.Default スレッドプール）
            launch(Dispatchers.Default) {
                StatsLockHolder.holdAndCompute() // [ANR-03] BG: ロックを長時間保持（heavyCompute）
            }
            // suspend delay でメインをブロックせず BG にロックを取得させる猶予（50ms）
            // ※ delay は suspend 関数のためメインスレッド自体はブロックしない
            delay(50L)
            // [ANR-03] メイン(Main dispatcher) で同じロックを取得しようとして待たされる
            //  → BG が重処理(heavyCompute + sleep 6s)を終えてロックを解放するまで停止 = ANR
            StatsLockHolder.acquireForForeground()
        }
    }
    val uiState by viewModel.uiState.collectAsState()
    StatsContent(modifier = modifier, uiState = uiState)
}

@Composable
fun StatsContent(modifier: Modifier = Modifier, uiState: StatsUiState = StatsUiState()) {
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
private fun StatsDailyRow(modifier: Modifier = Modifier, stat: DailyStat) {
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
