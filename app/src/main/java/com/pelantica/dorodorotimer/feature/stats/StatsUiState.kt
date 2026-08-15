package com.pelantica.dorodorotimer.feature.stats

import com.pelantica.dorodorotimer.domain.model.DailyStat

/**
 * 統計画面の状態。
 *
 * 読み込み表示を出すのは「初回」と「デモ用セクション」だけにしている。実データ
 * （Room・数件）は数ミリ秒で返るので、読み直しのたびに表示を出しても知らせる中身が
 * 無くちらつくだけになる。対してデモ側は [ANR-01] のシード投入を伴って数秒かかり、
 * 黙って前回値を出したままだと「読み終わった値」に見えてしまう。
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
     * demoMode が ON か。[demoStats] がまだ null でもセクションの枠は出したいので、
     * 「デモ用セクションを表示するか」の判定にはこちらを使う。
     */
    val isDemoMode: Boolean = false,
    /** デモ用セクションを読み込み中か。セクションの中身をスピナーに差し替える。 */
    val isDemoLoading: Boolean = false,
)
