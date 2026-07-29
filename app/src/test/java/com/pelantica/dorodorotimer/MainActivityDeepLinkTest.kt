package com.pelantica.dorodorotimer

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ディープリンク → タブ解決のテスト（Robolectric。Intent/Uri は Android の実装が要るため）。
 *
 * null が返る＝「タブの指定なし」であることが要点。onNewIntent は launchMode=singleTop の
 * ためランチャー復帰（ACTION_MAIN・data なし）でも呼ばれるので、ここで TIMER を返すと
 * 統計タブを見ていてもタイマーに引き戻されてしまう。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityDeepLinkTest {

    private fun viewIntent(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    @Test
    fun deepLinkToStats_selectsStatsTab() {
        assertEquals(Tab.STATS, viewIntent("dorodoro://stats").toDeepLinkTab())
    }

    @Test
    fun deepLinkToTimer_selectsTimerTab() {
        assertEquals(Tab.TIMER, viewIntent("dorodoro://timer").toDeepLinkTab())
    }

    @Test
    fun launcherIntent_hasNoTabPreference() {
        // ランチャー起動＝ACTION_MAIN で data なし。ここが null でないと
        // onNewIntent でタブが上書きされる（ランチャー復帰でタイマーに戻るバグ）。
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        assertNull(launcher.toDeepLinkTab())
    }

    @Test
    fun nullIntent_hasNoTabPreference() {
        assertNull(null.toDeepLinkTab())
    }

    @Test
    fun unknownHost_hasNoTabPreference() {
        assertNull(viewIntent("dorodoro://unknown").toDeepLinkTab())
    }

    @Test
    fun otherScheme_hasNoTabPreference() {
        assertNull(viewIntent("https://example.com/stats").toDeepLinkTab())
    }
}
