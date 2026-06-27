package com.tefumichangdev.dorodorotimer.feature.timer

/** 入力中の最大4桁(MMSS)文字列を秒に変換。例 "0130" -> 90 */
internal fun digitsToSeconds(digits: String): Int {
    if (digits.isEmpty()) return 0
    val padded = digits.padStart(4, '0').takeLast(4)
    val mm = padded.substring(0, 2).toInt()
    val ss = padded.substring(2, 4).toInt()
    return mm * 60 + ss
}

/** 秒を最大4桁(MMSS)文字列へ。例 90 -> "0130"（分は99で頭打ち） */
internal fun secondsToDigits(seconds: Int): String {
    val mm = (seconds / 60).coerceAtMost(99)
    val ss = seconds % 60
    return "%02d%02d".format(mm, ss)
}

internal fun formatDigits(digits: String): String {
    val padded = digits.padStart(4, '0').takeLast(4)
    return "${padded.substring(0, 2)}:${padded.substring(2, 4)}"
}

/**
 * テンキー入力1桁を buffer に追記する。
 * 候補 = (buffer + digit).takeLast(4) の下2桁（秒）が 0..59 のときだけ採用。
 * 60以上になる場合は buffer をそのまま返す（入力を拒否）。
 */
internal fun appendDigit(buffer: String, digit: Int): String {
    val candidate = (buffer + digit.toString()).takeLast(4)
    val ss = candidate.takeLast(2).toInt()
    return if (ss in 0..59) candidate else buffer
}
