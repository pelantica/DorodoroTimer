package com.pelantica.dorodorotimer.feature.stats

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pelantica.dorodorotimer.R
import com.pelantica.dorodorotimer.core.debug.Anr
import com.pelantica.dorodorotimer.core.debug.DemoConfig
import com.pelantica.dorodorotimer.core.ui.SectionCardCorner
import com.pelantica.dorodorotimer.data.local.stats.StatsStore
import com.pelantica.dorodorotimer.domain.model.DailyStat
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate

@Composable
fun StatsScreen(modifier: Modifier = Modifier, viewModel: StatsViewModel = koinViewModel()) {
    // [ANR-03] 通知ディープリンク流入（冷えた起動）でこの画面が前面化する。
    //  起動と同時に走り出した StatsStore.warmUp がまだロックを握っている最中に、
    //  メイン（LaunchedEffect の本体は Main dispatcher で走る）が同じ monitor を取りに行く。
    if (DemoConfig.isOn(Anr.ANR_03)) {
        LaunchedEffect(Unit) {
            // [ANR-03] 初回フレームを2回待ってから取りに行く: 凍る瞬間を「画面が描かれ
            //  ウィンドウがフォーカスを得た後」に置き、ANR の帰属を自アプリにするため
            //  （最初のウィンドウより前に凍ると入力締切がランチャー帰属になり
            //  Crashlytics/Vitals に写らない）。
            withFrameNanos { }
            withFrameNanos { }
            // [ANR-03] 「もう初期化済みのはずだから一瞬」という思い込みの同期アクセス。
            //  初期化中なら monitor 待ちでメインが凍る。処方は StatsStore の KDoc 参照。
            StatsStore.awaitReady()
        }
    }
    // タブに入るたびに読み直す（LaunchedEffect(Unit) はこの画面が composition に
    // 入り直すたびに再実行される。理由は StatsViewModel.reload の KDoc）。
    LaunchedEffect(Unit) { viewModel.reload() }
    val uiState by viewModel.uiState.collectAsState()

    if (DemoConfig.isOn(Anr.ANR_03)) {
        StatsContent(modifier = modifier, uiState = uiState)
    } else {
        // [ANR-03][正版] awaitReady は呼ばない。readiness を observe するだけ＝main は待たない。
        //  画面は即描画され、準備中は上に小さなインジケータが乗るだけ（内容は隠さない）。
        //  準備できたら readiness が true になり、インジケータは自然に消える。
        val isStatsStoreReady by StatsStore.readiness.collectAsState()
        Column(modifier = modifier) {
            if (!isStatsStoreReady) {
                StatsStoreWarmingIndicator()
            }
            StatsContent(modifier = Modifier.weight(1f), uiState = uiState)
        }
    }
}

/**
 * [ANR-03][正版] [StatsStore.readiness] が false の間だけ出す非ブロッキングの案内行。
 * 画面全体を覆うスピナーにはしない（このアプリの方針。[StatsUiState] の KDoc参照）。
 * 内容は今まで通り描画したまま、上に小さく乗るだけで、準備が済めば自然に消える。
 */
@Composable
private fun StatsStoreWarmingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.stats_store_warming),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
fun StatsContent(modifier: Modifier = Modifier, uiState: StatsUiState = StatsUiState()) {
    // 読み込み表示はセクション単位。画面全体を覆うスピナーは出さない
    // （片方が読めていればその内容は出せるので、全部を隠す理由がない）。
    // demoMode OFF で読み終わっていて実データも空: 従来どおり中央に空表示
    if (!uiState.isDemoMode && !uiState.isRealLoading && uiState.realStats.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.stats_empty))
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
    ) {
        if (!uiState.isDemoMode) {
            // demoMode OFF（通常時）: 実データだけを従来どおり1枚で（見出しなし）
            if (uiState.isRealLoading) loadingCard(R.string.stats_loading)
            else statsCard(uiState.realStats)
            return@LazyColumn
        }
        // demoMode ON: 実データを上に、デモ用シードの集計を下に（実データが埋もれないように）
        sectionHeader(R.string.stats_section_real)
        when {
            uiState.isRealLoading -> loadingCard(R.string.stats_loading)
            uiState.realStats.isEmpty() -> item {
                Text(
                    text = stringResource(R.string.stats_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
            }
            else -> statsCard(uiState.realStats)
        }
        sectionHeader(R.string.stats_section_demo, topPadding = 24.dp)
        // シード投入を伴って数秒かかる。前回の集計を出したままにせず中身を差し替える
        // （出したままだと「読み終わった値」に見えてしまうため）。
        if (uiState.isDemoLoading) loadingCard(R.string.stats_loading_demo)
        else statsCard(uiState.demoStats.orEmpty())
    }
}

/** 読み込み中のセクション。[statsCard] と同じ白いカードの中にスピナーを置く。 */
private fun LazyListScope.loadingCard(@StringRes labelRes: Int) {
    item {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SectionCardCorner))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator(labelRes = labelRes)
        }
    }
}

/** スピナーと説明文の縦並び。 */
@Composable
private fun LoadingIndicator(@StringRes labelRes: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun LazyListScope.sectionHeader(@StringRes labelRes: Int, topPadding: Dp = 0.dp) {
    item {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = topPadding, bottom = 8.dp),
        )
    }
}

/**
 * 日別集計のリストを「白いカードに行が並ぶ」見た目で描く（設定画面のANRトグルと同じ意匠）。
 * 件数が読めないので Card は張れない（LazyColumn が要る）。代わりに項目ごとに
 * 角丸を出し分けて1枚のカードに見せている（先頭は上だけ・末尾は下だけ丸める）。
 */
private fun LazyListScope.statsCard(stats: List<DailyStat>) {
    itemsIndexed(stats) { index, stat ->
        val isFirst = index == 0
        val isLast = index == stats.lastIndex
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
