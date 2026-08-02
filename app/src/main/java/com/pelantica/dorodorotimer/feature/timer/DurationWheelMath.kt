package com.pelantica.dorodorotimer.feature.timer

/**
 * ドラムロール（ホイール）ピッカー用の純粋関数群。
 * 分＝1分刻み、秒＝5秒刻み（0,5,10,...,55 の12段）。
 */

/** 秒の刻み幅。 */
internal const val SECONDS_STEP = 5

/** 秒ホイールに表示する値のリスト（0,5,...,55）。 */
internal val SECONDS_WHEEL_VALUES: List<Int> = (0 until 60 step SECONDS_STEP).toList()

/**
 * 集中時間の分レンジ（0〜120分）。下限が0なのは 0:05 のような1分未満を組めるようにするため
 * （終了通知やフェーズ切り替えの確認に要る）。0分0秒だけは [isValidDuration] で弾く。
 */
internal const val FOCUS_MIN_MINUTES = 0
internal const val FOCUS_MAX_MINUTES = 120

/** 休憩時間の分レンジ（0〜60分）。下限の意図は [FOCUS_MIN_MINUTES] と同じ。 */
internal const val BREAK_MIN_MINUTES = 0
internal const val BREAK_MAX_MINUTES = 60

/** 分ホイールに表示する値のリストを生成する。 */
internal fun minutesWheelValues(minMinutes: Int, maxMinutes: Int): List<Int> =
    (minMinutes..maxMinutes).toList()

/**
 * 秒の生値を最寄りの5秒刻みへ丸める。60に達する場合は繰り上げが必要なことを示すため、
 * 呼び出し側の [secondsToWheelValues] で分側へキャリーする。
 */
private fun roundToNearestStep(rawSeconds: Int): Int {
    val clamped = rawSeconds.coerceIn(0, 59)
    return ((clamped + SECONDS_STEP / 2) / SECONDS_STEP) * SECONDS_STEP
}

/**
 * 保存されている合計秒数を、ホイール表示用の (分, 秒) へ変換する。
 * 秒は5秒刻みに丸め（60になる場合は分を+1して秒を0にキャリー）、
 * 分は [minMinutes]..[maxMinutes] にクランプする。
 *
 * この正規化は「テンキー時代の1秒単位の値を読むため」だけのものではない。
 * 保存は秒単位の Int で、刻みも範囲も持たないホイールより表現力が広く、
 * ホイールで表せない値が入りうる。たとえば
 *  - [SECONDS_STEP] や分レンジの定数を後から変えたとき、既存の保存値が刻み外・範囲外になる
 *  - allowBackup 経由で、旧バージョンで保存された値が復元されてくる
 * 正規化を外しても落ちはしないが、WheelNumberPicker の indexOf が -1 になって
 * ホイールの先頭（0分）へ飛ぶ。近い値へ寄せる方が被害が小さい。
 */
internal fun secondsToWheelValues(totalSeconds: Int, minMinutes: Int, maxMinutes: Int): Pair<Int, Int> {
    val safeTotal = totalSeconds.coerceAtLeast(0)
    val rawMinutes = safeTotal / 60
    val rawSeconds = safeTotal % 60
    val roundedSeconds = roundToNearestStep(rawSeconds)
    val (carriedMinutes, seconds) = if (roundedSeconds >= 60) {
        (rawMinutes + 1) to 0
    } else {
        rawMinutes to roundedSeconds
    }
    val minutes = carriedMinutes.coerceIn(minMinutes, maxMinutes)
    return minutes to seconds
}

/** ホイールで選択された (分, 秒) を合計秒数へ変換する。 */
internal fun wheelValuesToSeconds(minutes: Int, seconds: Int): Int = minutes * 60 + seconds

/**
 * ホイールで選ばれた値を保存してよいか。
 * 0分0秒を許すと、開始した瞬間に残り0秒と判定されてフェーズが即終了してしまうため弾く。
 */
internal fun isValidDuration(minutes: Int, seconds: Int): Boolean =
    wheelValuesToSeconds(minutes, seconds) > 0
