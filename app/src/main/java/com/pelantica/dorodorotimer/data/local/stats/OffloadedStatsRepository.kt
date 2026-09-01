package com.pelantica.dorodorotimer.data.local.stats

import android.util.Log
import com.pelantica.dorodorotimer.data.local.room.FocusSessionDao
import com.pelantica.dorodorotimer.data.local.room.FocusSessionEntity
import com.pelantica.dorodorotimer.domain.model.DailyStat
import com.pelantica.dorodorotimer.domain.model.TimerPhase
import com.pelantica.dorodorotimer.domain.repository.StatsRepository

/**
 * demoMode OFF 用の「守る」正版実装（事例① ANR-01 の処方）。
 * 対比相手は [BlockingStatsRepository]（生SQLite・withContext なし・メインで同期実行）＝「守ってくれない」側。
 *
 * Room の suspend DAO が内部で自分のクエリ実行スレッドへ逃がしてくれるため、
 * 呼び出し側（Main）で何もしなくてもメインスレッドを固めない。
 *
 * **両実装の差は「ライブラリ（Room）がスレッドを管理してくれるか否か」だけ**にしてある。
 * データ量・作業量・アプリ側のスレッド操作には一切差をつけない
 * （同じ [DemoStatsSeed] を、同じ「1件ずつ非トランザクション書き込み」で投入する。
 * アプリ側の `withContext(IO)` も意図的に置いていない → [dailyStats] のコメント）。
 *
 * @param seedDemoData デモ用シードを投入するかどうかを**読み直すたびに判定する**関数。
 *   **リリース版では必ず false を返す**（呼び出し元は `di/AppModule.kt` で
 *   `DemoConfig.enabled`＝master トグルを読む関数を渡す）。
 *   Repository が [com.pelantica.dorodorotimer.core.debug.DemoConfig] を直接参照しないのは
 *   テスタビリティのため（フラグはコンストラクタで注入する）。
 *
 *   Boolean ではなく関数で受けるのは、このクラスが Koin の `single` として
 *   キャッシュされるため。生成時の値で固定すると、master トグルを再起動なしで
 *   OFF→ON したとき「デモ用シードデータ」欄が実データを表示してしまう
 *   （生成時 false のまま＝シードも投入されず、`isDemo == false` の行＝実データが返る）。
 *   なお **どちらの実装を注入するか**は `single` の生成時に決まったままで、これは意図どおり
 *   （[com.pelantica.dorodorotimer.core.debug.Anr.ANR_01] は `requiresRestart = true`）。
 * @param seedRowCount デモ用シード行数。テストは小さい値を渡して軽量に検証する。
 */
class OffloadedStatsRepository(
    private val dao: FocusSessionDao,
    private val seedDemoData: () -> Boolean = { false },
    private val seedRowCount: Int = DemoStatsSeed.SEED_ROW_COUNT,
) : StatsRepository {

    override suspend fun dailyStats(): List<DailyStat> {
        // 1回の読み直しの中で判定がブレないよう、最初に一度だけ読む
        // （投入したのに絞り込みでは実データ側を見る、のような食い違いを防ぐ）。
        val seeding = seedDemoData()
        // [ANR-01] seeding=false（リリース既定）のときはここを一切通らない＝架空データを作らない。
        if (seeding) {
            reseedForDemo()
        }
        // [ANR-01] 正版。ここに withContext(IO) は**あえて置いていない**。この関数は Main
        //  （StatsViewModel.init → viewModelScope）から呼ばれるが、Room の suspend DAO が
        //  自分のクエリ実行スレッドへ逃がすため、それだけでメインは固まらない。
        //  アプリ側でも逃がすと「Roomが守ったのか自分で守ったのか」が区別できず対比が濁る。
        //  下の集計は Kotlin 側＝Main で走る（数千件で 10ms 程度）。
        //  「Roomが守るのは DAO 呼び出しの中だけ」という境界がここに出ている。
        val all = dao.getAll()
        // isDemo == seeding で絞る:
        //  - seeding=true（demoMode）: この読み口は「デモ用の重いデータ」担当。実データは
        //    画面が別途、常時安全な読み口（AppModule 参照）から取得して上のセクションに出す。
        //  - seeding=false（リリース）: 実データだけを返す。demoMode を OFF にした後に
        //    シード行が残っていても、本物の統計に混ざらない。
        return all.filter { it.phase == TimerPhase.FOCUS.name && it.isDemo == seeding }
            .groupBy { it.completedAtEpochMs / 86_400_000L }
            .map { (day, rows) ->
                DailyStat(
                    dateEpochDay = day,
                    focusCount = rows.size,
                    totalFocusSeconds = rows.sumOf { it.durationSeconds },
                )
            }
            .sortedByDescending { it.dateEpochDay }
    }

    /**
     * [ANR-01] デモ用シード: 既存のデモ行を消してから [seedRowCount] 件を1件ずつ INSERT し直す。
     * 生SQLite側の [RawSqliteStatsHelper.reseedForDemo] と作業量を揃えてあり、違いは
     * Room の suspend DAO 経由で書くのでメインを専有しないことだけ。
     * 毎回入れ直すのは再現性のため（詳細は [RawSqliteStatsHelper.reseedForDemo] のKDoc）。
     */
    private suspend fun reseedForDemo(nowEpochMs: Long = System.currentTimeMillis()) {
        // 前回のデモデータ（isDemo=1）だけを消す。実データは残る。
        // （DELETE 自体は軽い。重いのは下の非トランザクションINSERTループ）
        dao.deleteDemo()

        val start = System.currentTimeMillis()
        val rows = DemoStatsSeed.generate(seedRowCount, nowEpochMs)
        for (row in rows) {
            // [ANR-01] insertAll のような一括版・@Transaction は使わない: 生SQLite側と同じ
            // 「1件ごとに書き込みが完結する」作業量にする（対比を「IOに逃したか否か」だけにするため）。
            dao.insert(
                FocusSessionEntity(
                    phase = row.phase,
                    durationSeconds = row.durationSeconds,
                    completedAtEpochMs = row.completedAtEpochMs,
                    isDemo = true,
                )
            )
        }
        val elapsed = System.currentTimeMillis() - start
        Log.d(TAG, "reseedForDemo: inserted $seedRowCount rows (non-transactional) in ${elapsed}ms")
    }

    companion object {
        private const val TAG = "OffloadedStatsRepository"
    }
}
