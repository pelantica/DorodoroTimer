package com.pelantica.dorodorotimer.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 設定画面・統計画面で共通に使う「地から浮いた白いカード」の角丸。
 * 統計はスクロール量が読めず LazyColumn を使うため Card を張れない（項目ごとに
 * 角を出し分けて1枚に見せている）。同じ見た目にするための値をここで一本化する。
 */
val SectionCardCorner = 12.dp

/**
 * 設定項目などを載せる白いカード。M3 の Card 既定色は暖色寄りの
 * surfaceContainerHighest なので、地と差をつけるため一番明るい面を明示する。
 */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        content()
    }
}
