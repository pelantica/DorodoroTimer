package com.pelantica.dorodorotimer.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pelantica.dorodorotimer.R
import com.pelantica.dorodorotimer.core.debug.Anr
import com.pelantica.dorodorotimer.core.debug.DemoConfig
import com.pelantica.dorodorotimer.core.ui.SectionCardCorner
import com.pelantica.dorodorotimer.domain.model.DailyStat
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
fun StatsContent(modifier: Modifier = Modifier, uiState: StatsUiState = StatsUiState()) {
    when {
        uiState.isLoading -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.stats_placeholder))
        }
        uiState.stats.isEmpty() -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.stats_empty))
        }
        // 設定画面の ANR トグルと同じ「白いカードに行が並ぶ」見た目にする。
        // 件数が読めないので Card は張れない（LazyColumn が要る）。代わりに項目ごとに
        // 角丸を出し分けて1枚のカードに見せている（先頭は上だけ・末尾は下だけ丸める）。
        else -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        ) {
            itemsIndexed(uiState.stats) { index, stat ->
                val isFirst = index == 0
                val isLast = index == uiState.stats.lastIndex
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(
                                topStart = if (isFirst) SectionCardCorner else 0.dp,
                                topEnd = if (isFirst) SectionCardCorner else 0.dp,
                                bottomStart = if (isLast) SectionCardCorner else 0.dp,
                                bottomEnd = if (isLast) SectionCardCorner else 0.dp,
                            )
                        )
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    // 区切り線は行の「間」だけ。先頭行の上には引かない。
                    if (!isFirst) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    StatsDailyRow(stat = stat)
                }
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.stats_row_total_minutes, totalMinutes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
