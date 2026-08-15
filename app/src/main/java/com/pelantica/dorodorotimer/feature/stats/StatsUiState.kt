package com.pelantica.dorodorotimer.feature.stats

import com.pelantica.dorodorotimer.domain.model.DailyStat

/**
 * 統計画面の状態。
 *
 * 読み込みの見せ方をセクション単位で分けている。実データ（Room・数件）はすぐ返るのに対し、
 * デモ用シードの読み込みは [ANR-01] のシード投入を伴って数秒かかるため、ひとつの
 * フラグで束ねると実データ側でスピナーが一瞬チラつくだけになってしまう。
 */
data class StatsUiState(
    /** タイマーで完了した本物のセッションの日別集計。常に安全な Room 経路から読む。 */
    val realStats: List<DailyStat> = emptyList(),
    /** [ANR-01] デモ用シードの日別集計。まだ一度も読めていない間は null。 */
    val demoStats: List<DailyStat>? = null,
    /**
     * まだ一度も読み終わっておらず、画面に出せるものが何も無い状態。
     * このときだけ全画面のスピナーを出す。
     */
    val isInitialLoading: Boolean = true,
    /**
     * 前回の内容を表示したまま裏で読み直している状態（タブに入り直したときなど）。
     * 最上部に進捗バーを出して「いま出ているのは前回の値」と分かるようにする。
     */
    val isRefreshing: Boolean = false,
    /**
     * demoMode が ON か。[demoStats] がまだ null でもセクションの枠は出したいので、
     * 「デモ用セクションを表示するか」の判定にはこちらを使う。
     */
    val isDemoMode: Boolean = false,
    /** デモ用セクションを読み込み中か。セクションの中身をスピナーに差し替える。 */
    val isDemoLoading: Boolean = false,
)
