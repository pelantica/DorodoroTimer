package com.tefumichangdev.dorodorotimer.feature.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tefumichangdev.dorodorotimer.R
import com.tefumichangdev.dorodorotimer.core.debug.Anr
import com.tefumichangdev.dorodorotimer.core.debug.DemoConfig

@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        // [ANR-03] 通知ディープリンク流入（冷えた起動）でこの画面が前面化する。
        //  demoMode ON のとき、ここで重い同期集計やロック競合を走らせると起動ANRを再現できる。
        //  今回は土台のみ＝フックだけ。本体は未実装。
        if (DemoConfig.isOn(Anr.ANR_03)) {
            // TODO(ANR-03): 重い同期処理（DB全件集計 / ロック競合 / Thread.sleep 等）をここで。
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.stats_placeholder))
    }
}
