package com.tefumichangdev.dorodorotimer.feature.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tefumichangdev.dorodorotimer.R

// 骨格ではプレースホルダ。実装時に「重い集計（ANR-03）」の舞台になる予定。
@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.stats_placeholder))
    }
}
