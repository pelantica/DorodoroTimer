package com.tefumichangdev.dorodorotimer.app

// [ANR-07] 起動時に多数クラスの初期化/ClassLoader が集中すると起動枠を圧迫しANR要因になる。
//  再現: demoMode ON のとき Application.onCreate で EagerGraph.forceLoadAll() を呼び、
//       40個のクラスを一括 eager 実体化する。
//  処方: Koin lazyModule 等で遅延ロードし、起動時に触るクラス数を減らす。
//
//  ※ ANR-02「重いCPU初期化」とは別物。
//     ANR-02 = 少数クラスの「計算量が重い」初期化がメインスレッドを長時間占有する問題。
//     ANR-07 = 多数クラスのロード/初期化が起動枠に集中し、個々は軽くても積み重なってANRになる問題。

private class Node00 { fun v(): Int = 0 }
private class Node01 { fun v(): Int = 1 }
private class Node02 { fun v(): Int = 2 }
private class Node03 { fun v(): Int = 3 }
private class Node04 { fun v(): Int = 4 }
private class Node05 { fun v(): Int = 5 }
private class Node06 { fun v(): Int = 6 }
private class Node07 { fun v(): Int = 7 }
private class Node08 { fun v(): Int = 8 }
private class Node09 { fun v(): Int = 9 }
private class Node10 { fun v(): Int = 10 }
private class Node11 { fun v(): Int = 11 }
private class Node12 { fun v(): Int = 12 }
private class Node13 { fun v(): Int = 13 }
private class Node14 { fun v(): Int = 14 }
private class Node15 { fun v(): Int = 15 }
private class Node16 { fun v(): Int = 16 }
private class Node17 { fun v(): Int = 17 }
private class Node18 { fun v(): Int = 18 }
private class Node19 { fun v(): Int = 19 }
private class Node20 { fun v(): Int = 20 }
private class Node21 { fun v(): Int = 21 }
private class Node22 { fun v(): Int = 22 }
private class Node23 { fun v(): Int = 23 }
private class Node24 { fun v(): Int = 24 }
private class Node25 { fun v(): Int = 25 }
private class Node26 { fun v(): Int = 26 }
private class Node27 { fun v(): Int = 27 }
private class Node28 { fun v(): Int = 28 }
private class Node29 { fun v(): Int = 29 }
private class Node30 { fun v(): Int = 30 }
private class Node31 { fun v(): Int = 31 }
private class Node32 { fun v(): Int = 32 }
private class Node33 { fun v(): Int = 33 }
private class Node34 { fun v(): Int = 34 }
private class Node35 { fun v(): Int = 35 }
private class Node36 { fun v(): Int = 36 }
private class Node37 { fun v(): Int = 37 }
private class Node38 { fun v(): Int = 38 }
private class Node39 { fun v(): Int = 39 }

/**
 * [ANR-07] 多数の小クラスを起動時に一括 eager 実体化するグラフ。
 *
 * 実際の ANR 要因は「分析SDK・画像ライブラリ・DI フレームワーク等が起動時に多数の
 * クラスを同期ロードし、ClassLoader/dex2oat のコストが積み重なる」こと。
 * このオブジェクトはその状況を40クラスで再現する教材用実装。
 */
internal object EagerGraph {

    /**
     * [ANR-07] 全ノード（Node00〜Node39）を実体化し、ClassLoader/初期化を起動時に強制する。
     *
     * 各インスタンスの [v] を呼んで合計を計算するが、重要なのは計算量ではなく
     * 「40クラスのロード・初期化が Application.onCreate に集中する」という事実。
     *
     * @return 触れたノード総数（= 40）。テストで決定的な値として検証可能。
     */
    fun forceLoadAll(): Int { // [ANR-07]
        val nodes = listOf(
            Node00(), Node01(), Node02(), Node03(), Node04(),
            Node05(), Node06(), Node07(), Node08(), Node09(),
            Node10(), Node11(), Node12(), Node13(), Node14(),
            Node15(), Node16(), Node17(), Node18(), Node19(),
            Node20(), Node21(), Node22(), Node23(), Node24(),
            Node25(), Node26(), Node27(), Node28(), Node29(),
            Node30(), Node31(), Node32(), Node33(), Node34(),
            Node35(), Node36(), Node37(), Node38(), Node39(),
        )
        // v() を呼ぶことでインスタンス参照が最適化除去されないようにする。
        // 合計値は検証には使わず、副作用（クラスロード実行）が目的。
        @Suppress("UNUSED_VARIABLE")
        val checksum = nodes.sumOf {
            when (it) {
                is Node00 -> it.v()
                is Node01 -> it.v()
                is Node02 -> it.v()
                is Node03 -> it.v()
                is Node04 -> it.v()
                is Node05 -> it.v()
                is Node06 -> it.v()
                is Node07 -> it.v()
                is Node08 -> it.v()
                is Node09 -> it.v()
                is Node10 -> it.v()
                is Node11 -> it.v()
                is Node12 -> it.v()
                is Node13 -> it.v()
                is Node14 -> it.v()
                is Node15 -> it.v()
                is Node16 -> it.v()
                is Node17 -> it.v()
                is Node18 -> it.v()
                is Node19 -> it.v()
                is Node20 -> it.v()
                is Node21 -> it.v()
                is Node22 -> it.v()
                is Node23 -> it.v()
                is Node24 -> it.v()
                is Node25 -> it.v()
                is Node26 -> it.v()
                is Node27 -> it.v()
                is Node28 -> it.v()
                is Node29 -> it.v()
                is Node30 -> it.v()
                is Node31 -> it.v()
                is Node32 -> it.v()
                is Node33 -> it.v()
                is Node34 -> it.v()
                is Node35 -> it.v()
                is Node36 -> it.v()
                is Node37 -> it.v()
                is Node38 -> it.v()
                is Node39 -> it.v()
                else -> 0
            }
        }
        return nodes.size
    }
}
