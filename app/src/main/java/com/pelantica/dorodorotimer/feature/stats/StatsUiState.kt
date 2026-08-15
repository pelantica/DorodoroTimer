package com.pelantica.dorodorotimer.feature.stats

import com.pelantica.dorodorotimer.domain.model.DailyStat

/**
 * 統計画面の状態。読み込み表示はセクション単位で持つ（画面全体を覆うスピナーは出さない）。
 *
 * 2つのフラグの寿命が違う点に注意:
 *  - [isRealLoading] は**まだ一度も読めていない間だけ** true。実データは Room の数件＋
 *    Kotlin 側の集計で数ミリ秒で返るため、読み直しのたびに立てるとカードがスピナーに
 *    置き換わって一瞬で戻る＝レイアウトが跳ねるだけになる。
 *  - [isDemoLoading] は**読み直すたび** true。デモ側は [ANR-01] のシード投入を伴って
 *    数秒かかり、黙って前回値を出したままだと「読み終わった値」に見えてしまう。
 */
data class StatsUiState(
    /** タイマーで完了した本物のセッションの日別集計。常に安全な Room 経路から読む。 */
    val realStats: List<DailyStat> = emptyList(),
    /** [ANR-01] デモ用シードの日別集計。まだ一度も読めていない間は null。 */
    val demoStats: List<DailyStat>? = null,
    /** 実データをまだ一度も読めておらず、セクションに出せるものが無い状態。 */
    val isRealLoading: Boolean = true,
    /**
     * demoMode が ON か。[demoStats] がまだ null でもセクションの枠は出したいので、
     * 「デモ用セクションを表示するか」の判定にはこちらを使う。
     */
    val isDemoMode: Boolean = false,
    /** デモ用セクションを読み込み中か。セクションの中身をスピナーに差し替える。 */
    val isDemoLoading: Boolean = false,
)
