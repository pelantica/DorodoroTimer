package com.tefumichangdev.dorodorotimer.feature.stats

/**
 * ANR-03「Deeplink × ロック競合」の教材用ロックホルダー。
 *
 * 通知ディープリンク（dorodoro://stats）で統計画面が前面化したとき、
 * バックグラウンドがロックを保持して重い処理中だと、メインスレッドが
 * 同じロックを取りにいくことで待たされる（**held by**）= ANR を再現する。
 *
 * | 関数                  | 呼び出し元         | 目的                                   |
 * |-----------------------|--------------------|----------------------------------------|
 * | [holdAndCompute]      | BG スレッド        | ロックを長時間保持して重処理をシミュレート |
 * | [acquireForForeground]| メインスレッド      | 同じロックを取りにいく → 待たされる       |
 * | [heavyCompute]        | holdAndCompute 内 | 決定的な重い計算（テスト可能）             |
 *
 * ⚠️ このオブジェクトは [com.tefumichangdev.dorodorotimer.core.debug.DemoConfig.isOn]
 *    が true のときだけ呼び出す。OFF パスでは一切触れないこと。
 */
object StatsLockHolder {

    /** [ANR-03] BG とメインスレッドが競合する共有ロック。 */
    val lock = Any()

    /**
     * [ANR-03] ロックを保持したまま重い同期処理を行う。バックグラウンドスレッドから呼ぶ想定。
     *
     * `synchronized(lock)` でロックを取得し、[heavyCompute] を実行して長時間保持する。
     * この間に別スレッド（= メインスレッド）が [acquireForForeground] を呼ぶと待たされる。
     */
    fun holdAndCompute(): Long = synchronized(lock) { heavyCompute() }

    /**
     * メインスレッドが同じロックを取りにいく（[holdAndCompute] の保持者がいると待たされる）。
     *
     * ANR-03 の held by を作ることがこの呼び出しの目的。
     * [holdAndCompute] がロックを解放するまでメインスレッドはここで停止する。
     *
     * @return 0L（ロック取得のみが目的のため計算は行わない）
     */
    fun acquireForForeground(): Long = synchronized(lock) { 0L }

    /**
     * 教材用の決定的な重い計算。`0 + 1 + ... + (iterations-1)` の合計を返す。
     *
     * デフォルト値は実機で ANR 閾値（5 秒）を確実に超えるよう、
     * ループ計算 + [Thread.sleep] を組み合わせる。
     * テスト時は `iterations` を小さく、`sleepMs=0` にして高速に決定値を検証できる。
     *
     * @param iterations ループ回数（デフォルト=100_000、本体の保持はsleepが主担当）
     * @param sleepMs スリープ時間 ms（デフォルト=6_000: ANR 閾値 5 秒を確実に超える）
     * @return sum of 0..(iterations-1) as Long
     */
    // [ANR-03] internal にしてテストから直接 iterations/sleepMs を指定可能にする
    internal fun heavyCompute(iterations: Int = 100_000, sleepMs: Long = 6_000L): Long {
        // [ANR-03] 教材用の重い計算：ループ集計 + スリープでロック保持時間を確保
        var acc = 0L
        repeat(iterations) { acc += it.toLong() }
        Thread.sleep(sleepMs) // [ANR-03] ANR 閾値(5秒)を超えるための保持スリープ
        return acc
    }
}
