package com.pelantica.dorodorotimer.feature.settings

import androidx.lifecycle.ViewModel
import com.pelantica.dorodorotimer.core.debug.Anr
import com.pelantica.dorodorotimer.core.debug.DemoFlags
import com.pelantica.dorodorotimer.core.debug.DemoFlagsState
import com.pelantica.dorodorotimer.core.report.CrashReportBreadcrumbs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(private val flags: DemoFlags) : ViewModel() {

    private val _state = MutableStateFlow(flags.snapshot())
    val state: StateFlow<DemoFlagsState> = _state.asStateFlow()

    /**
     * マスタートグルを切り替える。
     *
     * OFF にするときは、ON になっている個別 ANR トグルもまとめて OFF にする
     * （マスター OFF 中は個別 Switch が disabled になり、内部だけ ON が残ると
     * 「次にマスターを ON にした瞬間に思わぬ事例が有効になる」ため）。
     * 確認ダイアログは出さない代わりに、設定画面のマスターカードに常設の注記を置いている。
     *
     * [DemoFlagsState.restartPromptFor] は立てない。再起動プロンプトは個別トグル操作
     * （[setAnr]）からのみ立つもので、マスター OFF は「まとめて無効化する」操作だから。
     */
    fun setMaster(on: Boolean) {
        if (!on) {
            Anr.entries.filter { flags.isOn(it) }.forEach { flags.setOn(it, false) }
        }
        flags.setMaster(on)
        _state.value = flags.snapshot()
        // 一括クリアでキーが実状態からズレるため、Crashlytics のカスタムキーを貼り直す。
        // 書き込みは Crashlytics 内部のワーカーに積まれるだけでメインスレッド I/O にはならない。
        CrashReportBreadcrumbs.setDemoFlags(_state.value)
    }

    fun setAnr(anr: Anr, on: Boolean) {
        val previousValue = flags.isOn(anr)
        flags.setOn(anr, on)
        _state.value = flags.snapshot().copy(
            restartPromptFor = if (anr.requiresRestart) anr else null,
            restartPromptPreviousValue = previousValue,
        )
    }

    /**
     * 再起動確認ダイアログを「キャンセル」で閉じる（あとで／ダイアログ外タップ／戻る操作）。
     * [Anr.requiresRestart] のトグルは再起動するまで実際の挙動に反映されないため、
     * 変更前の値へ書き戻して「トグルの表示＝実際に効いている状態」を保つ。
     */
    fun dismissRestartPrompt() {
        val anr = _state.value.restartPromptFor ?: return
        flags.setOn(anr, _state.value.restartPromptPreviousValue)
        _state.value = flags.snapshot().copy(restartPromptFor = null)
    }

    /**
     * 再起動確認ダイアログを「再起動する」で閉じる。フラグは変更後の値のまま維持し、
     * プロンプト状態だけをクリアする（[AppRestarter] の呼び出しは Context を持つ Screen 側の責務）。
     */
    fun confirmRestartPrompt() {
        _state.value = _state.value.copy(restartPromptFor = null)
    }
}
