package com.tefumichangdev.dorodorotimer.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// 骨格では最小のライトテーマ。配色・ダーク対応は実装時に詰める。
@Composable
fun DorodoroTimerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content,
    )
}
