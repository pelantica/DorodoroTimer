package com.pelantica.dorodorotimer.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 設定画面・統計画面で共通に使う白いカードの角丸。統計は LazyColumn のため Card を張れず
 * 項目ごとに角を出し分けており、同じ見た目にするための値をここで一本化する。
 */
val SectionCardCorner = 12.dp

/** 設定項目などを載せる白いカード。 */
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
