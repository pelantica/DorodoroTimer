package com.tefumichangdev.dorodorotimer.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ANR-07] EagerGraph のユニットテスト。
 * Android 依存なし（Robolectric 不要）で JVM 上で動く。
 * 「クラスロードの重さ」自体はテストせず、forceLoadAll() が触れたノード総数を
 * 決定的な値として返すことのみを検証する。
 */
class EagerGraphTest {

    @Test
    fun forceLoadAll_returnsTotalNodeCount() {
        // Node00〜Node39 の40クラスが全て実体化されること＝戻り値が40であることを検証。
        // [ANR-07] 教材として重要なのは「クラス数＝ロード対象の多さ」であり、計算量ではない。
        assertEquals(40, EagerGraph.forceLoadAll())
    }
}
