package com.pelantica.dorodorotimer.feature.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelantica.dorodorotimer.domain.model.PomodoroPreset
import com.pelantica.dorodorotimer.domain.model.TimerPhase
import com.pelantica.dorodorotimer.domain.model.TimerState
import com.pelantica.dorodorotimer.domain.model.TimerUiState
import com.pelantica.dorodorotimer.domain.model.isRunning
import com.pelantica.dorodorotimer.domain.repository.FocusSessionRecorder
import com.pelantica.dorodorotimer.domain.repository.PomodoroPresetRepository
import com.pelantica.dorodorotimer.domain.repository.TimerStateRepository
import com.pelantica.dorodorotimer.service.AmbientSoundController
import com.pelantica.dorodorotimer.service.TimerReducer
import com.pelantica.dorodorotimer.service.TimerScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * タイマーの真実は TimerState（runningUntilEpochMs）。VMは
 * start/pause/reset で状態を更新し、AlarmManager(TimerScheduler)に終了時刻を予約、
 * 実行中だけ毎秒 uiState を end-now から再計算する。常駐Serviceは持たない。
 */
class TimerViewModel(
    private val presetRepo: PomodoroPresetRepository,
    private val timerStateRepo: TimerStateRepository,
    private val scheduler: TimerScheduler,
    private val ambientSound: AmbientSoundController,
    private val sessionRecorder: FocusSessionRecorder,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private var timerState = TimerState()
    private var currentPreset: PomodoroPreset = PomodoroPreset.Default

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    val preset: StateFlow<PomodoroPreset> = presetRepo.preset.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PomodoroPreset.Default,
    )

    val isSoundPlaying: StateFlow<Boolean> = ambientSound.isPlaying

    private var tickJob: Job? = null

    init {
        viewModelScope.launch {
            timerState = timerStateRepo.load()
            // 閉じている間に終わっていたら（実行中だが既に0）フェーズを送って停止に確定。
            // この時点の currentPreset はまだ既定値なので、記録と onFinished を正しい設定時間で
            // 行うために保存済みの preset を先に読む（下の collect が始まる前）。
            if (timerState.isRunning && TimerReducer.displaySeconds(timerState, now()) <= 0) {
                currentPreset = presetRepo.preset.first()
                recordIfFocusCompleted()
                timerState = TimerReducer.onFinished(timerState, currentPreset)
                persist()
            }
            refreshUi()
            if (timerState.isRunning) startTicking()
            // load 完了後に設定購読を開始（順序保証：ロード前に collector が persist しない）。
            // 停止中で「現フェーズの満了値ちょうど」のときだけ新設定に追従し、
            // 一時停止で途中まで減った remaining はクロバーしない。
            presetRepo.preset.collect { p ->
                val atFullForPhase = !timerState.isRunning &&
                    timerState.remainingSeconds == TimerReducer.secondsFor(currentPreset, timerState.phase)
                currentPreset = p
                if (atFullForPhase) {
                    timerState = timerState.copy(remainingSeconds = TimerReducer.secondsFor(p, timerState.phase))
                    persist()
                    refreshUi()
                }
            }
        }
    }

    fun toggleRunning() {
        if (timerState.isRunning) pause() else start()
    }

    private fun start() {
        timerState = TimerReducer.start(timerState, now())
        scheduler.schedule(timerState.runningUntilEpochMs!!, timerState.phase)
        persist()
        refreshUi()
        startTicking()
    }

    private fun pause() {
        tickJob?.cancel()
        scheduler.cancel()
        timerState = TimerReducer.pause(timerState, now())
        persist()
        refreshUi()
    }

    fun reset() {
        tickJob?.cancel()
        scheduler.cancel()
        timerState = TimerReducer.reset(timerState, currentPreset)
        persist()
        refreshUi()
    }

    fun updateDurations(focusSeconds: Int, breakSeconds: Int) {
        viewModelScope.launch { presetRepo.update(focusSeconds, breakSeconds) }
    }

    fun toggleSound() {
        ambientSound.toggle()
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (timerState.isRunning) {
                if (TimerReducer.displaySeconds(timerState, now()) <= 0) {
                    // 0到達：フェーズを送って停止（通知はReceiver側が出す）
                    recordIfFocusCompleted()
                    timerState = TimerReducer.onFinished(timerState, currentPreset)
                    persist()
                    refreshUi()
                    break
                }
                refreshUi()
                delay(1000)
            }
        }
    }

    /**
     * 集中フェーズの満了を1セッションとして記録する（休憩の満了・reset は記録しない）。
     * timerState が満了前（onFinished 適用前）のうちに呼ぶこと。
     *
     * - durationSeconds は現設定の集中時間。一時停止を挟んでも合計の集中時間は
     *   設定値どおりなのでこれで足りる（一時停止中に設定を変えた場合だけ誤差になるが許容）。
     * - completedAt は実際に0へ到達した時刻＝runningUntilEpochMs。アプリを閉じている間に
     *   終わった場合でも、開き直した時刻ではなく本当の完了時刻の日付で計上される。
     */
    private fun recordIfFocusCompleted() {
        if (timerState.phase != TimerPhase.FOCUS) return
        val completedAt = timerState.runningUntilEpochMs ?: now()
        val duration = TimerReducer.secondsFor(currentPreset, TimerPhase.FOCUS)
        viewModelScope.launch { sessionRecorder.record(duration, completedAt) }
    }

    private fun refreshUi() {
        _uiState.value = TimerUiState(
            phase = timerState.phase,
            remainingSeconds = TimerReducer.displaySeconds(timerState, now()),
            isRunning = timerState.isRunning,
        )
    }

    private fun persist() {
        viewModelScope.launch { timerStateRepo.save(timerState) }
    }
}
