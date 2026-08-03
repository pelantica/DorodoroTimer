package com.pelantica.dorodorotimer.data.local.room

import com.pelantica.dorodorotimer.domain.model.TimerPhase
import com.pelantica.dorodorotimer.domain.repository.FocusSessionRecorder

/**
 * [FocusSessionRecorder] の Room 実装。
 * suspend DAO は Room がクエリ実行スレッドへ逃がすため、Main から呼ばれても固めない。
 */
class RoomFocusSessionRecorder(private val dao: FocusSessionDao) : FocusSessionRecorder {
    override suspend fun record(durationSeconds: Int, completedAtEpochMs: Long) {
        dao.insert(
            FocusSessionEntity(
                // 読み側（dailyStats）は phase == TimerPhase.FOCUS.name でフィルタする
                phase = TimerPhase.FOCUS.name,
                durationSeconds = durationSeconds,
                completedAtEpochMs = completedAtEpochMs,
            )
        )
    }
}
