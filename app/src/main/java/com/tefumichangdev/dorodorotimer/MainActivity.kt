package com.tefumichangdev.dorodorotimer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
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
import com.tefumichangdev.dorodorotimer.core.ui.DorodoroTimerTheme
import com.tefumichangdev.dorodorotimer.feature.settings.SettingsScreen
import com.tefumichangdev.dorodorotimer.feature.stats.StatsScreen
import com.tefumichangdev.dorodorotimer.feature.timer.TimerScreen

class MainActivity : ComponentActivity() {
    // 選択中タブの真実はここに一本化する。Compose 側で remember すると、
    // 同じタブを指す通知を続けてタップしたとき（値が変わらないため）再反映されない。
    private var selectedTab by mutableStateOf(Tab.TIMER)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedTab = intent.toStartTab()
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
        // launchMode=singleTop のため、起動中に通知をタップした場合はここに来る。
        selectedTab = intent.toStartTab()
    }
}

private enum class Tab(val labelRes: Int, val icon: ImageVector) {
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
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (selectedTab) {
            Tab.TIMER -> TimerScreen(modifier = contentModifier)
            Tab.STATS -> StatsScreen(modifier = contentModifier)
            Tab.SETTINGS -> SettingsScreen(modifier = contentModifier)
        }
    }
}

private fun Intent?.toStartTab(): Tab {
    val data: Uri? = this?.data
    if (data?.scheme != "dorodoro") return Tab.TIMER
    return when (data.host) {
        "stats" -> Tab.STATS
        "timer" -> Tab.TIMER
        else -> Tab.TIMER // 不明な host は現状維持でタイマーへフォールバック
    }
}
