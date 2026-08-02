package com.pelantica.dorodorotimer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.pelantica.dorodorotimer.core.ui.DorodoroTimerTheme
import com.pelantica.dorodorotimer.core.ui.StrictModeBanner
import com.pelantica.dorodorotimer.feature.settings.SettingsScreen
import com.pelantica.dorodorotimer.feature.stats.StatsScreen
import com.pelantica.dorodorotimer.feature.timer.TimerScreen

class MainActivity : ComponentActivity() {
    // 選択中タブの真実はここに一本化する。Compose 側で remember すると、
    // 同じタブを指す通知を続けてタップしたとき（値が変わらないため）再反映されない。
    private var selectedTab by mutableStateOf(Tab.TIMER)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedTab = intent.toDeepLinkTab() ?: Tab.TIMER
        setContent {
            DorodoroTimerTheme {
                DorodoroApp(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // launchMode=singleTop のため、通知タップだけでなく
        // ランチャーからの復帰（ACTION_MAIN・data なし）でもここに来る。
        // ディープリンクでないときはタブ指定なし＝表示中のタブを維持する
        // （無条件に代入すると、統計タブを見ていてもタイマーに引き戻される）。
        intent.toDeepLinkTab()?.let { selectedTab = it }
    }
}

internal enum class Tab(val labelRes: Int, val icon: ImageVector) {
    TIMER(R.string.nav_timer, Icons.Filled.Timer),
    STATS(R.string.nav_stats, Icons.Filled.BarChart),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
private fun DorodoroApp(selectedTab: Tab = Tab.TIMER, onSelectTab: (Tab) -> Unit = {}) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // デバッグビルドで StrictMode 違反が出たときだけ、画面の一番上に出る。
            StrictModeBanner()
            val contentModifier = Modifier
                .fillMaxWidth()
                .weight(1f)
            when (selectedTab) {
                Tab.TIMER -> TimerScreen(modifier = contentModifier)
                Tab.STATS -> StatsScreen(modifier = contentModifier)
                Tab.SETTINGS -> SettingsScreen(modifier = contentModifier)
            }
        }
    }
}

/**
 * dorodoro:// のディープリンクが指すタブ。ディープリンクでない（ランチャー起動など）、
 * または未知の host のときは null＝「タブの指定なし」を意味し、呼び出し側が
 * 起動時は TIMER、復帰時は現在のタブ維持、と解釈する。
 */
internal fun Intent?.toDeepLinkTab(): Tab? {
    val data: Uri? = this?.data
    if (data?.scheme != "dorodoro") return null
    return when (data.host) {
        "stats" -> Tab.STATS
        "timer" -> Tab.TIMER
        else -> null
    }
}
