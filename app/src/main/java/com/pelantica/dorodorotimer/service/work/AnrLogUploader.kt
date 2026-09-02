package com.pelantica.dorodorotimer.service.work

/**
 * ANR/クラッシュログの送信先スタブ。実アプリなら Crashlytics 等へ送信する所だが、
 * 教材では件数を返すだけ（ANR-05 のポイントは送信処理自体が無罪であること）。
 */
object AnrLogUploader {
    /** レポートを「送信」した体で、送信件数を返す（スタブ）。 */
    fun upload(reports: List<String>): Int {
        return reports.size
    }
}
